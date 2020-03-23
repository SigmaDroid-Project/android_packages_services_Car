/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "carwatchdogd"

#include "IoPerfCollection.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <binder/IServiceManager.h>
#include <cutils/android_filesystem_config.h>
#include <inttypes.h>
#include <log/log.h>
#include <processgroup/sched_policy.h>
#include <pwd.h>

#include <iomanip>
#include <limits>
#include <string>
#include <thread>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

using android::defaultServiceManager;
using android::IBinder;
using android::IServiceManager;
using android::sp;
using android::String16;
using android::base::Error;
using android::base::ParseUint;
using android::base::Result;
using android::base::StringAppendF;
using android::base::WriteStringToFd;
using android::content::pm::IPackageManagerNative;

namespace {

const std::string kDumpMajorDelimiter = std::string(100, '-') + "\n";

double percentage(uint64_t numer, uint64_t denom) {
    return denom == 0 ? 0.0 : (static_cast<double>(numer) / static_cast<double>(denom)) * 100.0;
}

struct UidProcessStats {
    uint64_t uid = 0;
    uint32_t ioBlockedTasksCnt = 0;
    uint32_t totalTasksCnt = 0;
    uint64_t majorFaults = 0;
};

std::unordered_map<uint32_t, UidProcessStats> getUidProcessStats(
        const std::vector<ProcessStats>& processStats) {
    std::unordered_map<uint32_t, UidProcessStats> uidProcessStats;
    for (const auto& stats : processStats) {
        if (stats.uid < 0) {
            continue;
        }
        uint32_t uid = static_cast<uint32_t>(stats.uid);
        if (uidProcessStats.find(uid) == uidProcessStats.end()) {
            uidProcessStats[uid] = UidProcessStats{.uid = uid};
        }
        auto& curUidProcessStats = uidProcessStats[uid];
        // Top-level process stats has the aggregated major page faults count and this should be
        // persistent across thread creation/termination. Thus use the value from this field.
        curUidProcessStats.majorFaults += stats.process.majorFaults;
        curUidProcessStats.totalTasksCnt += stats.threads.size();
        // The process state is the same as the main thread state. Thus to avoid double counting
        // ignore the process state.
        for (const auto& threadStat : stats.threads) {
            curUidProcessStats.ioBlockedTasksCnt += threadStat.second.state == "D" ? 1 : 0;
        }
    }
    return uidProcessStats;
}

Result<std::chrono::seconds> parseSecondsFlag(Vector<String16> args, size_t pos) {
    if (args.size() < pos) {
        return Error() << "Value not provided";
    }

    uint64_t value;
    std::string strValue = std::string(String8(args[pos]).string());
    if (!ParseUint(strValue, &value)) {
        return Error() << "Invalid value " << args[pos].string() << ", must be an integer";
    }
    return std::chrono::seconds(value);
}

}  // namespace

std::string toString(const UidIoPerfData& data) {
    std::string buffer;
    if (data.topNReads.size() > 0) {
        StringAppendF(&buffer, "\nTop N Reads:\n%s\n", std::string(12, '-').c_str());
        StringAppendF(&buffer,
                      "Android User ID, Package Name, Foreground Bytes, Foreground Bytes %%, "
                      "Foreground Fsync, Foreground Fsync %%, Background Bytes, "
                      "Background Bytes %%, Background Fsync, Background Fsync %%\n");
    }
    for (const auto& stat : data.topNReads) {
        StringAppendF(&buffer, "%" PRIu32 ", %s", stat.userId, stat.packageName.c_str());
        for (int i = 0; i < UID_STATES; ++i) {
            StringAppendF(&buffer, ", %" PRIu64 ", %.2f%%, %" PRIu64 ", %.2f%%", stat.bytes[i],
                          percentage(stat.bytes[i], data.total[READ_BYTES][i]), stat.fsync[i],
                          percentage(stat.fsync[i], data.total[FSYNC_COUNT][i]));
        }
        StringAppendF(&buffer, "\n");
    }
    if (data.topNWrites.size() > 0) {
        StringAppendF(&buffer, "\nTop N Writes:\n%s\n", std::string(13, '-').c_str());
        StringAppendF(&buffer,
                      "Android User ID, Package Name, Foreground Bytes, Foreground Bytes %%, "
                      "Foreground Fsync, Foreground Fsync %%, Background Bytes, "
                      "Background Bytes %%, Background Fsync, Background Fsync %%\n");
    }
    for (const auto& stat : data.topNWrites) {
        StringAppendF(&buffer, "%" PRIu32 ", %s", stat.userId, stat.packageName.c_str());
        for (int i = 0; i < UID_STATES; ++i) {
            StringAppendF(&buffer, ", %" PRIu64 ", %.2f%%, %" PRIu64 ", %.2f%%", stat.bytes[i],
                          percentage(stat.bytes[i], data.total[WRITE_BYTES][i]), stat.fsync[i],
                          percentage(stat.fsync[i], data.total[FSYNC_COUNT][i]));
        }
        StringAppendF(&buffer, "\n");
    }
    return buffer;
}

std::string toString(const SystemIoPerfData& data) {
    std::string buffer;
    StringAppendF(&buffer, "CPU I/O wait time/percent: %" PRIu64 " / %.2f%%\n", data.cpuIoWaitTime,
                  percentage(data.cpuIoWaitTime, data.totalCpuTime));
    StringAppendF(&buffer, "Number of I/O blocked processes/percent: %" PRIu32 " / %.2f%%\n",
                  data.ioBlockedProcessesCnt,
                  percentage(data.ioBlockedProcessesCnt, data.totalProcessesCnt));
    return buffer;
}

std::string toString(const ProcessIoPerfData& data) {
    std::string buffer;
    StringAppendF(&buffer, "Number of major page faults since last collection: %" PRIu64 "\n",
                  data.totalMajorFaults);
    StringAppendF(&buffer,
                  "Percentage of change in major page faults since last collection: %.2f%%\n",
                  data.majorFaultsPercentChange);
    if (data.topNMajorFaults.size() > 0) {
        StringAppendF(&buffer, "\nTop N major page faults:\n%s\n", std::string(24, '-').c_str());
        StringAppendF(&buffer,
                      "Android User ID, Package Name, Number of major page faults, "
                      "Percentage of total major page faults\n");
    }
    for (const auto& stat : data.topNMajorFaults) {
        StringAppendF(&buffer, "%" PRIu32 ", %s, %" PRIu64 ", %.2f%%\n", stat.userId,
                      stat.packageName.c_str(), stat.count,
                      percentage(stat.count, data.totalMajorFaults));
    }
    if (data.topNIoBlockedUids.size() > 0) {
        StringAppendF(&buffer, "\nTop N I/O waiting UIDs:\n%s\n", std::string(23, '-').c_str());
        StringAppendF(&buffer,
                      "Android User ID, Package Name, Number of owned tasks waiting for I/O, "
                      "Percentage of owned tasks waiting for I/O\n");
    }
    for (size_t i = 0; i < data.topNIoBlockedUids.size(); ++i) {
        const auto& stat = data.topNIoBlockedUids[i];
        StringAppendF(&buffer, "%" PRIu32 ", %s, %" PRIu64 ", %.2f%%\n", stat.userId,
                      stat.packageName.c_str(), stat.count,
                      percentage(stat.count, data.topNIoBlockedUidsTotalTaskCnt[i]));
    }
    return buffer;
}

std::string toString(const IoPerfRecord& record) {
    std::string buffer;
    StringAppendF(&buffer, "%s%s%s", toString(record.systemIoPerfData).c_str(),
                  toString(record.processIoPerfData).c_str(),
                  toString(record.uidIoPerfData).c_str());
    return buffer;
}

std::string toString(const CollectionInfo& collectionInfo) {
    std::string buffer;
    StringAppendF(&buffer, "Number of collections: %zu\n", collectionInfo.records.size());
    auto interval =
            std::chrono::duration_cast<std::chrono::seconds>(collectionInfo.interval).count();
    StringAppendF(&buffer, "Collection interval: %lld second%s\n", interval,
                  ((interval > 1) ? "s" : ""));
    for (size_t i = 0; i < collectionInfo.records.size(); ++i) {
        const auto& record = collectionInfo.records[i];
        std::stringstream timestamp;
        timestamp << std::put_time(std::localtime(&record.time), "%c %Z");
        StringAppendF(&buffer, "Collection %zu: <%s>\n%s\n%s\n", i, timestamp.str().c_str(),
                      std::string(45, '=').c_str(), toString(record).c_str());
    }
    return buffer;
}

Result<void> IoPerfCollection::start() {
    {
        Mutex::Autolock lock(mMutex);
        if (mCurrCollectionEvent != CollectionEvent::INIT || mCollectionThread.joinable()) {
            return Error(INVALID_OPERATION)
                    << "Cannot start I/O performance collection more than once";
        }

        // TODO(b/148489461): Once |kTopNStatsPerCategory|, |kBoottimeCollectionInterval| and
        // |kPeriodicCollectionInterval| constants are moved to read-only persistent properties,
        // read and store them in the collection infos.

        mBoottimeCollection = {
                .interval = kBoottimeCollectionInterval,
                .maxCacheSize = std::numeric_limits<std::size_t>::max(),
                .lastCollectionUptime = 0,
                .records = {},
        };
        mPeriodicCollection = {
                .interval = kPeriodicCollectionInterval,
                .maxCacheSize = kPeriodicCollectionBufferSize,
                .lastCollectionUptime = 0,
                .records = {},
        };
    }

    mCollectionThread = std::thread([&]() {
        {
            Mutex::Autolock lock(mMutex);
            if (mCurrCollectionEvent != CollectionEvent::INIT) {
                ALOGE("Skipping I/O performance data collection as the current collection event "
                      "%s != %s",
                      toString(mCurrCollectionEvent).c_str(),
                      toString(CollectionEvent::INIT).c_str());
                return;
            }
            mCurrCollectionEvent = CollectionEvent::BOOT_TIME;
            mBoottimeCollection.lastCollectionUptime = mHandlerLooper->now();
            mHandlerLooper->setLooper(Looper::prepare(/*opts=*/0));
            mHandlerLooper->sendMessage(this, CollectionEvent::BOOT_TIME);
        }
        if (set_sched_policy(0, SP_BACKGROUND) != 0) {
            ALOGW("Failed to set background scheduling priority to I/O performance data collection "
                  "thread");
        }
        bool isCollectionActive = true;
        // Loop until the collection is not active -- I/O perf collection runs on this thread in a
        // handler.
        while (isCollectionActive) {
            mHandlerLooper->pollAll(/*timeoutMillis=*/-1);
            Mutex::Autolock lock(mMutex);
            isCollectionActive = mCurrCollectionEvent != CollectionEvent::TERMINATED;
        }
    });
    return {};
}

void IoPerfCollection::terminate() {
    {
        Mutex::Autolock lock(mMutex);
        if (mCurrCollectionEvent == CollectionEvent::TERMINATED) {
            ALOGE("I/O performance data collection was terminated already");
            return;
        }
        ALOGE("Terminating I/O performance data collection");
        mCurrCollectionEvent = CollectionEvent::TERMINATED;
    }
    if (mCollectionThread.joinable()) {
        mHandlerLooper->removeMessages(this);
        mHandlerLooper->wake();
        mCollectionThread.join();
    }
}

Result<void> IoPerfCollection::onBootFinished() {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent != CollectionEvent::BOOT_TIME) {
        return Error(INVALID_OPERATION)
                << "Current I/O performance data collection event "
                << toString(mCurrCollectionEvent) << " != " << toString(CollectionEvent::BOOT_TIME)
                << " collection event";
    }
    mHandlerLooper->removeMessages(this);
    mCurrCollectionEvent = CollectionEvent::PERIODIC;
    mPeriodicCollection.lastCollectionUptime = mHandlerLooper->now();
    mHandlerLooper->sendMessage(this, CollectionEvent::PERIODIC);
    return {};
}

Result<void> IoPerfCollection::dump(int fd, const Vector<String16>& args) {
    if (args.empty()) {
        const auto& ret = dumpCollection(fd);
        if (!ret) {
            return ret;
        }
        return {};
    }

    if (args[0] == String16(kStartCustomCollectionFlag)) {
        if (args.size() > 5) {
            return Error(INVALID_OPERATION) << "Number of arguments to start custom "
                                            << "I/O performance data collection cannot exceed 5";
        }
        std::chrono::nanoseconds interval = kCustomCollectionInterval;
        std::chrono::nanoseconds maxDuration = kCustomCollectionDuration;
        for (size_t i = 1; i < args.size(); ++i) {
            if (args[i] == String16(kIntervalFlag)) {
                const auto& ret = parseSecondsFlag(args, i + 1);
                if (!ret) {
                    return Error(FAILED_TRANSACTION)
                            << "Failed to parse " << kIntervalFlag << ": " << ret.error();
                }
                interval = std::chrono::duration_cast<std::chrono::nanoseconds>(*ret);
                ++i;
                continue;
            }
            if (args[i] == String16(kMaxDurationFlag)) {
                const auto& ret = parseSecondsFlag(args, i + 1);
                if (!ret) {
                    return Error(FAILED_TRANSACTION)
                            << "Failed to parse " << kMaxDurationFlag << ": " << ret.error();
                }
                maxDuration = std::chrono::duration_cast<std::chrono::nanoseconds>(*ret);
                ++i;
                continue;
            }
            ALOGW("Unknown flag %s provided to start custom I/O performance data collection",
                  String8(args[i]).string());
            return Error(INVALID_OPERATION) << "Unknown flag " << String8(args[i]).string()
                                            << " provided to start custom I/O performance data "
                                            << "collection";
        }
        const auto& ret = startCustomCollection(interval, maxDuration);
        if (!ret) {
            return ret;
        }
        return {};
    }

    if (args[0] == String16(kEndCustomCollectionFlag)) {
        if (args.size() != 1) {
            ALOGW("Number of arguments to end custom I/O performance data collection cannot "
                  "exceed 1");
        }
        const auto& ret = endCustomCollection(fd);
        if (!ret) {
            return ret;
        }
        return {};
    }

    return Error(INVALID_OPERATION)
            << "Dump arguments start neither with " << kStartCustomCollectionFlag << " nor with "
            << kEndCustomCollectionFlag << " flags";
}

Result<void> IoPerfCollection::dumpCollection(int fd) {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent == CollectionEvent::TERMINATED) {
        ALOGW("I/O performance data collection not active. Dumping cached data");
        if (!WriteStringToFd("I/O performance data collection not active. Dumping cached data.",
                             fd)) {
            return Error(FAILED_TRANSACTION) << "Failed to write I/O performance collection status";
        }
    }

    const auto& ret = dumpCollectorsStatusLocked(fd);
    if (!ret) {
        return Error(FAILED_TRANSACTION) << ret.error();
    }

    if (!WriteStringToFd(StringPrintf("%sI/O performance data reports:\n%sBoot-time collection "
                                      "report:\n%s\n",
                                      kDumpMajorDelimiter.c_str(), kDumpMajorDelimiter.c_str(),
                                      std::string(28, '=').c_str()),
                         fd) ||
        !WriteStringToFd(toString(mBoottimeCollection), fd) ||
        !WriteStringToFd(StringPrintf("%s\nPeriodic collection report:\n%s\n",
                                      std::string(75, '-').c_str(), std::string(27, '=').c_str()),
                         fd) ||
        !WriteStringToFd(toString(mPeriodicCollection), fd) ||
        !WriteStringToFd(kDumpMajorDelimiter, fd)) {
        return Error(FAILED_TRANSACTION)
                << "Failed to dump the boot-time and periodic collection reports.";
    }
    return {};
}

Result<void> IoPerfCollection::dumpCollectorsStatusLocked(int fd) {
    if (!mUidIoStats->enabled() &&
        !WriteStringToFd(StringPrintf("UidIoStats collector failed to access the file %s",
                                      mUidIoStats->filePath().c_str()),
                         fd)) {
        return Error() << "Failed to write UidIoStats collector status";
    }
    if (!mProcStat->enabled() &&
        !WriteStringToFd(StringPrintf("ProcStat collector failed to access the file %s",
                                      mProcStat->filePath().c_str()),
                         fd)) {
        return Error() << "Failed to write ProcStat collector status";
    }
    if (!mProcPidStat->enabled() &&
        !WriteStringToFd(StringPrintf("ProcPidStat collector failed to access the directory %s",
                                      mProcPidStat->dirPath().c_str()),
                         fd)) {
        return Error() << "Failed to write ProcPidStat collector status";
    }
    return {};
}

Result<void> IoPerfCollection::startCustomCollection(std::chrono::nanoseconds interval,
                                                     std::chrono::nanoseconds maxDuration) {
    if (interval < kMinCollectionInterval || maxDuration < kMinCollectionInterval) {
        return Error(INVALID_OPERATION)
                << "Collection interval and maximum duration must be >= "
                << std::chrono::duration_cast<std::chrono::milliseconds>(kMinCollectionInterval)
                           .count()
                << " milliseconds.";
    }
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent != CollectionEvent::PERIODIC) {
        return Error(INVALID_OPERATION)
                << "Cannot start a custom collection when "
                << "the current collection event " << toString(mCurrCollectionEvent)
                << " != " << toString(CollectionEvent::PERIODIC) << " collection event";
    }

    mCustomCollection = {
            .interval = interval,
            .maxCacheSize = std::numeric_limits<std::size_t>::max(),
            .lastCollectionUptime = mHandlerLooper->now(),
            .records = {},
    };

    mHandlerLooper->removeMessages(this);
    nsecs_t uptime = mHandlerLooper->now() + maxDuration.count();
    mHandlerLooper->sendMessageAtTime(uptime, this, SwitchEvent::END_CUSTOM_COLLECTION);
    mCurrCollectionEvent = CollectionEvent::CUSTOM;
    mHandlerLooper->sendMessage(this, CollectionEvent::CUSTOM);
    return {};
}

Result<void> IoPerfCollection::endCustomCollection(int fd) {
    Mutex::Autolock lock(mMutex);
    if (mCurrCollectionEvent != CollectionEvent::CUSTOM) {
        return Error(INVALID_OPERATION) << "No custom collection is running";
    }

    mHandlerLooper->removeMessages(this);
    mHandlerLooper->sendMessage(this, SwitchEvent::END_CUSTOM_COLLECTION);

    const auto& ret = dumpCollectorsStatusLocked(fd);
    if (!ret) {
        return Error(FAILED_TRANSACTION) << ret.error();
    }

    if (!WriteStringToFd(StringPrintf("%sI/O performance data report for custom collection:\n%s",
                                      kDumpMajorDelimiter.c_str(), kDumpMajorDelimiter.c_str()),
                         fd) ||
        !WriteStringToFd(toString(mCustomCollection), fd) ||
        !WriteStringToFd(kDumpMajorDelimiter, fd)) {
        return Error(FAILED_TRANSACTION) << "Failed to write custom collection report.";
    }

    return {};
}

void IoPerfCollection::handleMessage(const Message& message) {
    Result<void> result;

    switch (message.what) {
        case static_cast<int>(CollectionEvent::BOOT_TIME):
            result = processCollectionEvent(CollectionEvent::BOOT_TIME, &mBoottimeCollection);
            break;
        case static_cast<int>(CollectionEvent::PERIODIC):
            result = processCollectionEvent(CollectionEvent::PERIODIC, &mPeriodicCollection);
            break;
        case static_cast<int>(CollectionEvent::CUSTOM):
            result = processCollectionEvent(CollectionEvent::CUSTOM, &mCustomCollection);
            break;
        case static_cast<int>(SwitchEvent::END_CUSTOM_COLLECTION): {
            Mutex::Autolock lock(mMutex);
            if (mCurrCollectionEvent != CollectionEvent::CUSTOM) {
                ALOGW("Skipping END_CUSTOM_COLLECTION message as the current collection %s != %s",
                      toString(mCurrCollectionEvent).c_str(),
                      toString(CollectionEvent::CUSTOM).c_str());
                return;
            }
            mCustomCollection = {};
            mHandlerLooper->removeMessages(this);
            mCurrCollectionEvent = CollectionEvent::PERIODIC;
            mPeriodicCollection.lastCollectionUptime = mHandlerLooper->now();
            mHandlerLooper->sendMessage(this, CollectionEvent::PERIODIC);
            return;
        }
        default:
            result = Error() << "Unknown message: " << message.what;
    }

    if (!result) {
        Mutex::Autolock lock(mMutex);
        ALOGE("Terminating I/O performance data collection: %s", result.error().message().c_str());
        // DO NOT CALL terminate() as it tries to join the collection thread but this code is
        // executed on the collection thread. Thus it will result in a deadlock.
        mCurrCollectionEvent = CollectionEvent::TERMINATED;
        mHandlerLooper->removeMessages(this);
        mHandlerLooper->wake();
    }
}

Result<void> IoPerfCollection::processCollectionEvent(CollectionEvent event, CollectionInfo* info) {
    Mutex::Autolock lock(mMutex);
    // Messages sent to the looper are intrinsically racy such that a message from the previous
    // collection event may land in the looper after the current collection has already begun. Thus
    // verify the current collection event before starting the collection.
    if (mCurrCollectionEvent != event) {
        ALOGW("Skipping %s collection message on collection event %s", toString(event).c_str(),
              toString(mCurrCollectionEvent).c_str());
        return {};
    }
    if (info->maxCacheSize == 0) {
        return Error() << "Maximum cache size for " << toString(event) << " collection cannot be 0";
    }
    if (info->interval < kMinCollectionInterval) {
        return Error()
                << "Collection interval of "
                << std::chrono::duration_cast<std::chrono::seconds>(info->interval).count()
                << " seconds for " << toString(event) << " collection cannot be less than "
                << std::chrono::duration_cast<std::chrono::seconds>(kMinCollectionInterval).count()
                << " seconds";
    }
    auto ret = collectLocked(info);
    if (!ret) {
        return Error() << toString(event) << " collection failed: " << ret.error();
    }
    info->lastCollectionUptime += info->interval.count();
    mHandlerLooper->sendMessageAtTime(info->lastCollectionUptime, this, event);
    return {};
}

Result<void> IoPerfCollection::collectLocked(CollectionInfo* collectionInfo) {
    if (!mUidIoStats->enabled() && !mProcStat->enabled() && !mProcPidStat->enabled()) {
        return Error() << "No collectors enabled";
    }
    IoPerfRecord record{
            .time = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now()),
    };
    auto ret = collectSystemIoPerfDataLocked(&record.systemIoPerfData);
    if (!ret) {
        return ret;
    }
    ret = collectProcessIoPerfDataLocked(&record.processIoPerfData);
    if (!ret) {
        return ret;
    }
    ret = collectUidIoPerfDataLocked(&record.uidIoPerfData);
    if (!ret) {
        return ret;
    }
    if (collectionInfo->records.size() > collectionInfo->maxCacheSize) {
        collectionInfo->records.erase(collectionInfo->records.begin());  // Erase the oldest record.
    }
    collectionInfo->records.emplace_back(record);
    return {};
}

Result<void> IoPerfCollection::collectUidIoPerfDataLocked(UidIoPerfData* uidIoPerfData) {
    if (!mUidIoStats->enabled()) {
        // Don't return an error to avoid pre-mature termination. Instead, fetch data from other
        // collectors.
        return {};
    }

    const Result<std::unordered_map<uint32_t, UidIoUsage>>& usage = mUidIoStats->collect();
    if (!usage) {
        return Error() << "Failed to collect uid I/O usage: " << usage.error();
    }

    // Fetch only the top N reads and writes from the usage records.
    UidIoUsage tempUsage = {};
    std::vector<const UidIoUsage*> topNReads(mTopNStatsPerCategory, &tempUsage);
    std::vector<const UidIoUsage*> topNWrites(mTopNStatsPerCategory, &tempUsage);
    std::unordered_set<uint32_t> unmappedUids;

    for (const auto& uIt : *usage) {
        const UidIoUsage& curUsage = uIt.second;
        if (curUsage.ios.isZero()) {
            continue;
        }
        if (mUidToPackageNameMapping.find(curUsage.uid) == mUidToPackageNameMapping.end()) {
            unmappedUids.insert(curUsage.uid);
        }
        uidIoPerfData->total[READ_BYTES][FOREGROUND] +=
                curUsage.ios.metrics[READ_BYTES][FOREGROUND];
        uidIoPerfData->total[READ_BYTES][BACKGROUND] +=
                curUsage.ios.metrics[READ_BYTES][BACKGROUND];
        uidIoPerfData->total[WRITE_BYTES][FOREGROUND] +=
                curUsage.ios.metrics[WRITE_BYTES][FOREGROUND];
        uidIoPerfData->total[WRITE_BYTES][BACKGROUND] +=
                curUsage.ios.metrics[WRITE_BYTES][BACKGROUND];
        uidIoPerfData->total[FSYNC_COUNT][FOREGROUND] +=
                curUsage.ios.metrics[FSYNC_COUNT][FOREGROUND];
        uidIoPerfData->total[FSYNC_COUNT][BACKGROUND] +=
                curUsage.ios.metrics[FSYNC_COUNT][BACKGROUND];

        for (auto it = topNReads.begin(); it != topNReads.end(); ++it) {
            const UidIoUsage* curRead = *it;
            if (curRead->ios.sumReadBytes() > curUsage.ios.sumReadBytes()) {
                continue;
            }
            topNReads.erase(topNReads.end() - 1);
            topNReads.emplace(it, &curUsage);
            break;
        }
        for (auto it = topNWrites.begin(); it != topNWrites.end(); ++it) {
            const UidIoUsage* curWrite = *it;
            if (curWrite->ios.sumWriteBytes() > curUsage.ios.sumWriteBytes()) {
                continue;
            }
            topNWrites.erase(topNWrites.end() - 1);
            topNWrites.emplace(it, &curUsage);
            break;
        }
    }

    const auto& ret = updateUidToPackageNameMapping(unmappedUids);
    if (!ret) {
        ALOGW("%s", ret.error().message().c_str());
    }

    // Convert the top N I/O usage to UidIoPerfData.
    for (const auto& usage : topNReads) {
        if (usage->ios.isZero()) {
            // End of non-zero usage records. This case occurs when the number of UIDs with active
            // I/O operations is < |kTopNStatsPerCategory|.
            break;
        }
        UidIoPerfData::Stats stats = {
                .userId = multiuser_get_user_id(usage->uid),
                .packageName = std::to_string(usage->uid),
                .bytes = {usage->ios.metrics[READ_BYTES][FOREGROUND],
                          usage->ios.metrics[READ_BYTES][BACKGROUND]},
                .fsync = {usage->ios.metrics[FSYNC_COUNT][FOREGROUND],
                          usage->ios.metrics[FSYNC_COUNT][BACKGROUND]},
        };
        if (mUidToPackageNameMapping.find(usage->uid) != mUidToPackageNameMapping.end()) {
            stats.packageName = mUidToPackageNameMapping[usage->uid];
        }
        uidIoPerfData->topNReads.emplace_back(stats);
    }

    for (const auto& usage : topNWrites) {
        if (usage->ios.isZero()) {
            // End of non-zero usage records. This case occurs when the number of UIDs with active
            // I/O operations is < |kTopNStatsPerCategory|.
            break;
        }
        UidIoPerfData::Stats stats = {
                .userId = multiuser_get_user_id(usage->uid),
                .packageName = std::to_string(usage->uid),
                .bytes = {usage->ios.metrics[WRITE_BYTES][FOREGROUND],
                          usage->ios.metrics[WRITE_BYTES][BACKGROUND]},
                .fsync = {usage->ios.metrics[FSYNC_COUNT][FOREGROUND],
                          usage->ios.metrics[FSYNC_COUNT][BACKGROUND]},
        };
        if (mUidToPackageNameMapping.find(usage->uid) != mUidToPackageNameMapping.end()) {
            stats.packageName = mUidToPackageNameMapping[usage->uid];
        }
        uidIoPerfData->topNWrites.emplace_back(stats);
    }
    return {};
}

Result<void> IoPerfCollection::collectSystemIoPerfDataLocked(SystemIoPerfData* systemIoPerfData) {
    if (!mProcStat->enabled()) {
        // Don't return an error to avoid pre-mature termination. Instead, fetch data from other
        // collectors.
        return {};
    }

    const Result<ProcStatInfo>& procStatInfo = mProcStat->collect();
    if (!procStatInfo) {
        return Error() << "Failed to collect proc stats: " << procStatInfo.error();
    }

    systemIoPerfData->cpuIoWaitTime = procStatInfo->cpuStats.ioWaitTime;
    systemIoPerfData->totalCpuTime = procStatInfo->totalCpuTime();
    systemIoPerfData->ioBlockedProcessesCnt = procStatInfo->ioBlockedProcessesCnt;
    systemIoPerfData->totalProcessesCnt = procStatInfo->totalProcessesCnt();
    return {};
}

Result<void> IoPerfCollection::collectProcessIoPerfDataLocked(
        ProcessIoPerfData* processIoPerfData) {
    if (!mProcPidStat->enabled()) {
        // Don't return an error to avoid pre-mature termination. Instead, fetch data from other
        // collectors.
        return {};
    }

    const Result<std::vector<ProcessStats>>& processStats = mProcPidStat->collect();
    if (!processStats) {
        return Error() << "Failed to collect process stats: " << processStats.error();
    }

    const auto& uidProcessStats = getUidProcessStats(*processStats);

    std::unordered_set<uint32_t> unmappedUids;
    // Fetch only the top N I/O blocked UIDs and UIDs with most major page faults.
    UidProcessStats temp = {};
    std::vector<const UidProcessStats*> topNIoBlockedUids(mTopNStatsPerCategory, &temp);
    std::vector<const UidProcessStats*> topNMajorFaults(mTopNStatsPerCategory, &temp);
    processIoPerfData->totalMajorFaults = 0;
    for (const auto& it : uidProcessStats) {
        const UidProcessStats& curStats = it.second;
        if (mUidToPackageNameMapping.find(curStats.uid) == mUidToPackageNameMapping.end()) {
            unmappedUids.insert(curStats.uid);
        }
        processIoPerfData->totalMajorFaults += curStats.majorFaults;
        for (auto it = topNIoBlockedUids.begin(); it != topNIoBlockedUids.end(); ++it) {
            const UidProcessStats* topStats = *it;
            if (topStats->ioBlockedTasksCnt > curStats.ioBlockedTasksCnt) {
                continue;
            }
            topNIoBlockedUids.erase(topNIoBlockedUids.end() - 1);
            topNIoBlockedUids.emplace(it, &curStats);
            break;
        }
        for (auto it = topNMajorFaults.begin(); it != topNMajorFaults.end(); ++it) {
            const UidProcessStats* topStats = *it;
            if (topStats->majorFaults > curStats.majorFaults) {
                continue;
            }
            topNMajorFaults.erase(topNMajorFaults.end() - 1);
            topNMajorFaults.emplace(it, &curStats);
            break;
        }
    }

    const auto& ret = updateUidToPackageNameMapping(unmappedUids);
    if (!ret) {
        ALOGW("%s", ret.error().message().c_str());
    }

    // Convert the top N uid process stats to ProcessIoPerfData.
    for (const auto& it : topNIoBlockedUids) {
        if (it->ioBlockedTasksCnt == 0) {
            // End of non-zero elements. This case occurs when the number of UIDs with I/O blocked
            // processes is < |kTopNStatsPerCategory|.
            break;
        }
        ProcessIoPerfData::Stats stats = {
                .userId = multiuser_get_user_id(it->uid),
                .packageName = std::to_string(it->uid),
                .count = it->ioBlockedTasksCnt,
        };
        if (mUidToPackageNameMapping.find(it->uid) != mUidToPackageNameMapping.end()) {
            stats.packageName = mUidToPackageNameMapping[it->uid];
        }
        processIoPerfData->topNIoBlockedUids.emplace_back(stats);
        processIoPerfData->topNIoBlockedUidsTotalTaskCnt.emplace_back(it->totalTasksCnt);
    }
    for (const auto& it : topNMajorFaults) {
        if (it->majorFaults == 0) {
            // End of non-zero elements. This case occurs when the number of UIDs with major faults
            // is < |kTopNStatsPerCategory|.
            break;
        }
        ProcessIoPerfData::Stats stats = {
                .userId = multiuser_get_user_id(it->uid),
                .packageName = std::to_string(it->uid),
                .count = it->majorFaults,
        };
        if (mUidToPackageNameMapping.find(it->uid) != mUidToPackageNameMapping.end()) {
            stats.packageName = mUidToPackageNameMapping[it->uid];
        }
        processIoPerfData->topNMajorFaults.emplace_back(stats);
    }
    if (mLastMajorFaults == 0) {
        processIoPerfData->majorFaultsPercentChange = 0;
    } else {
        int64_t increase = processIoPerfData->totalMajorFaults - mLastMajorFaults;
        processIoPerfData->majorFaultsPercentChange =
                (static_cast<double>(increase) / static_cast<double>(mLastMajorFaults)) * 100.0;
    }
    mLastMajorFaults = processIoPerfData->totalMajorFaults;
    return {};
}

Result<void> IoPerfCollection::updateUidToPackageNameMapping(
    const std::unordered_set<uint32_t>& uids) {
    std::vector<int32_t> appUids;

    for (const auto& uid : uids) {
        if (uid >= AID_APP_START) {
            appUids.emplace_back(static_cast<int32_t>(uid));
            continue;
        }
        // System/native UIDs.
        passwd* usrpwd = getpwuid(uid);
        if (!usrpwd) {
            continue;
        }
        mUidToPackageNameMapping[uid] = std::string(usrpwd->pw_name);
    }

    if (appUids.empty()) {
        return {};
    }

    if (mPackageManager == nullptr) {
        auto ret = retrievePackageManager();
        if (!ret) {
            return Error() << "Failed to retrieve package manager: " << ret.error();
        }
    }

    std::vector<std::string> packageNames;
    const binder::Status& status = mPackageManager->getNamesForUids(appUids, &packageNames);
    if (!status.isOk()) {
        return Error() << "package_native::getNamesForUids failed: " << status.exceptionMessage();
    }

    for (uint32_t i = 0; i < appUids.size(); i++) {
        if (!packageNames[i].empty()) {
            mUidToPackageNameMapping[appUids[i]] = packageNames[i];
        }
    }

    return {};
}

Result<void> IoPerfCollection::retrievePackageManager() {
    const sp<IServiceManager> sm = defaultServiceManager();
    if (sm == nullptr) {
        return Error() << "Failed to retrieve defaultServiceManager";
    }

    sp<IBinder> binder = sm->getService(String16("package_native"));
    if (binder == nullptr) {
        return Error() << "Failed to get service package_native";
    }
    mPackageManager = interface_cast<IPackageManagerNative>(binder);
    return {};
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
