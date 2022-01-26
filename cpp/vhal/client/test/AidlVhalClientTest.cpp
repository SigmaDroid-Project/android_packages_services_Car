/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <aidl/android/hardware/automotive/vehicle/BnVehicle.h>
#include <android/binder_ibinder.h>
#include <gtest/gtest.h>

#include <AidlHalPropValue.h>
#include <AidlVhalClient.h>
#include <VehicleHalTypes.h>
#include <VehicleUtils.h>

#include <atomic>
#include <condition_variable>  // NOLINT
#include <mutex>               // NOLINT
#include <thread>              // NOLINT

namespace android {
namespace frameworks {
namespace automotive {
namespace vhal {
namespace test {

using ::android::base::Result;
using ::android::hardware::automotive::vehicle::toInt;

using ::aidl::android::hardware::automotive::vehicle::BnVehicle;
using ::aidl::android::hardware::automotive::vehicle::GetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::GetValueRequests;
using ::aidl::android::hardware::automotive::vehicle::GetValueResult;
using ::aidl::android::hardware::automotive::vehicle::GetValueResults;
using ::aidl::android::hardware::automotive::vehicle::IVehicle;
using ::aidl::android::hardware::automotive::vehicle::IVehicleCallback;
using ::aidl::android::hardware::automotive::vehicle::RawPropValues;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequests;
using ::aidl::android::hardware::automotive::vehicle::SetValueResult;
using ::aidl::android::hardware::automotive::vehicle::SetValueResults;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfigs;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;

using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;

class MockVhal final : public BnVehicle {
public:
    using CallbackType = std::shared_ptr<IVehicleCallback>;

    ~MockVhal() {
        std::unique_lock<std::mutex> lk(mLock);
        mCv.wait_for(lk, std::chrono::milliseconds(1000), [this] { return mThreadCount == 0; });
    }

    ScopedAStatus getAllPropConfigs([[maybe_unused]] VehiclePropConfigs* returnConfigs) override {
        return ScopedAStatus::ok();
    }

    ScopedAStatus getValues(const CallbackType& callback,
                            const GetValueRequests& requests) override {
        mGetValueRequests = requests.payloads;

        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }

        if (mWaitTimeInMs == 0) {
            callback->onGetValues(GetValueResults{.payloads = mGetValueResults});
        } else {
            mThreadCount++;
            std::thread t([this, callback]() {
                std::this_thread::sleep_for(std::chrono::milliseconds(mWaitTimeInMs));
                callback->onGetValues(GetValueResults{.payloads = mGetValueResults});
                mThreadCount--;
                mCv.notify_one();
            });
            // Detach the thread here so we do not have to maintain the thread object. mThreadCount
            // and mCv make sure we wait for all threads to end before we exit.
            t.detach();
        }
        return ScopedAStatus::ok();
    }

    ScopedAStatus setValues(const CallbackType& callback,
                            const SetValueRequests& requests) override {
        mSetValueRequests = requests.payloads;

        if (mStatus != StatusCode::OK) {
            return ScopedAStatus::fromServiceSpecificError(toInt(mStatus));
        }

        if (mWaitTimeInMs == 0) {
            callback->onSetValues(SetValueResults{.payloads = mSetValueResults});
        } else {
            mThreadCount++;
            std::thread t([this, callback]() {
                std::this_thread::sleep_for(std::chrono::milliseconds(mWaitTimeInMs));
                callback->onSetValues(SetValueResults{.payloads = mSetValueResults});
                mThreadCount--;
                mCv.notify_one();
            });
            // Detach the thread here so we do not have to maintain the thread object. mThreadCount
            // and mCv make sure we wait for all threads to end before we exit.
            t.detach();
        }
        return ScopedAStatus::ok();
    }

    ScopedAStatus getPropConfigs([[maybe_unused]] const std::vector<int32_t>& props,
                                 [[maybe_unused]] VehiclePropConfigs* returnConfigs) override {
        return ScopedAStatus::ok();
    }

    ScopedAStatus subscribe([[maybe_unused]] const CallbackType& callback,
                            [[maybe_unused]] const std::vector<SubscribeOptions>& options,
                            [[maybe_unused]] int32_t maxSharedMemoryFileCount) override {
        return ScopedAStatus::ok();
    }

    ScopedAStatus unsubscribe([[maybe_unused]] const CallbackType& callback,
                              [[maybe_unused]] const std::vector<int32_t>& propIds) override {
        return ScopedAStatus::ok();
    }

    ScopedAStatus returnSharedMemory([[maybe_unused]] const CallbackType& callback,
                                     [[maybe_unused]] int64_t sharedMemoryId) override {
        return ScopedAStatus::ok();
    }

    // Test Functions

    void setGetValueResults(std::vector<GetValueResult> results) { mGetValueResults = results; }

    std::vector<GetValueRequest> getGetValueRequests() { return mGetValueRequests; }

    void setSetValueResults(std::vector<SetValueResult> results) { mSetValueResults = results; }

    std::vector<SetValueRequest> getSetValueRequests() { return mSetValueRequests; }

    void setWaitTimeInMs(int64_t waitTimeInMs) { mWaitTimeInMs = waitTimeInMs; }

    void setStatus(StatusCode status) { mStatus = status; }

private:
    std::mutex mLock;
    std::vector<GetValueResult> mGetValueResults;
    std::vector<GetValueRequest> mGetValueRequests;
    std::vector<SetValueResult> mSetValueResults;
    std::vector<SetValueRequest> mSetValueRequests;
    int64_t mWaitTimeInMs = 0;
    StatusCode mStatus = StatusCode::OK;
    std::condition_variable mCv;
    std::atomic<int> mThreadCount = 0;
};

class AidlVhalClientTest : public ::testing::Test {
protected:
    class TestLinkUnlinkImpl final : public AidlVhalClient::ILinkUnlinkToDeath {
    public:
        binder_status_t linkToDeath([[maybe_unused]] AIBinder* binder,
                                    [[maybe_unused]] AIBinder_DeathRecipient* recipient,
                                    void* cookie) override {
            mCookie = cookie;
            return STATUS_OK;
        }

        binder_status_t unlinkToDeath(AIBinder*, AIBinder_DeathRecipient*, void*) override {
            // DO nothing.
            return STATUS_OK;
        }

        void* getCookie() { return mCookie; }

    private:
        void* mCookie;
    };

    constexpr static int32_t TEST_PROP_ID = 1;
    constexpr static int32_t TEST_AREA_ID = 2;
    constexpr static int64_t TEST_TIMEOUT_IN_MS = 100;

    void SetUp() override {
        mVhal = SharedRefBase::make<MockVhal>();
        auto impl = std::make_unique<TestLinkUnlinkImpl>();
        // We are sure impl would be alive when we use mLinkUnlinkImpl.
        mLinkUnlinkImpl = impl.get();
        mVhalClient = std::unique_ptr<AidlVhalClient>(
                new AidlVhalClient(mVhal, TEST_TIMEOUT_IN_MS, std::move(impl)));
    }

    AidlVhalClient* getClient() { return mVhalClient.get(); }

    MockVhal* getVhal() { return mVhal.get(); }

    void triggerBinderDied() { AidlVhalClient::onBinderDied(mLinkUnlinkImpl->getCookie()); }

    void triggerBinderUnlinked() { AidlVhalClient::onBinderUnlinked(mLinkUnlinkImpl->getCookie()); }

    size_t countOnBinderDiedCallbacks() { return mVhalClient->countOnBinderDiedCallbacks(); }

private:
    std::shared_ptr<MockVhal> mVhal;
    std::unique_ptr<AidlVhalClient> mVhalClient;
    TestLinkUnlinkImpl* mLinkUnlinkImpl;
};

TEST_F(AidlVhalClientTest, testGetValueNormal) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setWaitTimeInMs(10);
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .value =
                                            RawPropValues{
                                                    .int32Values = {1},
                                            },
                            },
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    Result<std::unique_ptr<IHalPropValue>> result;
    Result<std::unique_ptr<IHalPropValue>>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
            [&lock, &cv, resultPtr, gotResultPtr](Result<std::unique_ptr<IHalPropValue>> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->getValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getInt32Values(), std::vector<int32_t>({1}));
}

TEST_F(AidlVhalClientTest, testGetValueTimeout) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    // The request will time-out before the response.
    getVhal()->setWaitTimeInMs(200);
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .value =
                                            RawPropValues{
                                                    .int32Values = {1},
                                            },
                            },
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    Result<std::unique_ptr<IHalPropValue>> result;
    Result<std::unique_ptr<IHalPropValue>>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
            [&lock, &cv, resultPtr, gotResultPtr](Result<std::unique_ptr<IHalPropValue>> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->getValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), toInt(StatusCode::TRY_AGAIN));
}

TEST_F(AidlVhalClientTest, testGetValueErrorStatus) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    Result<std::unique_ptr<IHalPropValue>> result;
    Result<std::unique_ptr<IHalPropValue>>* resultPtr = &result;

    getClient()->getValue(propValue,
                          std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
                                  [resultPtr](Result<std::unique_ptr<IHalPropValue>> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), toInt(StatusCode::INTERNAL_ERROR));
}

TEST_F(AidlVhalClientTest, testGetValueNonOkayResult) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    Result<std::unique_ptr<IHalPropValue>> result;
    Result<std::unique_ptr<IHalPropValue>>* resultPtr = &result;

    getClient()->getValue(propValue,
                          std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
                                  [resultPtr](Result<std::unique_ptr<IHalPropValue>> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), toInt(StatusCode::INTERNAL_ERROR));
}

TEST_F(AidlVhalClientTest, testGetValueIgnoreInvalidRequestId) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setGetValueResults({
            GetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
                    .prop =
                            VehiclePropValue{
                                    .prop = TEST_PROP_ID,
                                    .areaId = TEST_AREA_ID,
                                    .value =
                                            RawPropValues{
                                                    .int32Values = {1},
                                            },
                            },
            },
            // This result has invalid request ID and should be ignored.
            GetValueResult{
                    .requestId = 1,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    Result<std::unique_ptr<IHalPropValue>> result;
    Result<std::unique_ptr<IHalPropValue>>* resultPtr = &result;

    getClient()->getValue(propValue,
                          std::make_shared<AidlVhalClient::GetValueCallbackFunc>(
                                  [resultPtr](Result<std::unique_ptr<IHalPropValue>> r) {
                                      *resultPtr = std::move(r);
                                  }));

    ASSERT_EQ(getVhal()->getGetValueRequests(),
              std::vector<GetValueRequest>({GetValueRequest{.requestId = 0, .prop = testProp}}));
    ASSERT_TRUE(result.ok());
    auto gotValue = std::move(result.value());
    ASSERT_EQ(gotValue->getPropId(), TEST_PROP_ID);
    ASSERT_EQ(gotValue->getAreaId(), TEST_AREA_ID);
    ASSERT_EQ(gotValue->getInt32Values(), std::vector<int32_t>({1}));
}

TEST_F(AidlVhalClientTest, testSetValueNormal) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setWaitTimeInMs(10);
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    Result<void> result;
    Result<void>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
            [&lock, &cv, resultPtr, gotResultPtr](Result<void> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->setValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_TRUE(result.ok());
}

TEST_F(AidlVhalClientTest, testSetValueTimeout) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    // The request will time-out before the response.
    getVhal()->setWaitTimeInMs(200);
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    std::mutex lock;
    std::condition_variable cv;
    Result<void> result;
    Result<void>* resultPtr = &result;
    bool gotResult = false;
    bool* gotResultPtr = &gotResult;

    auto callback = std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
            [&lock, &cv, resultPtr, gotResultPtr](Result<void> r) {
                {
                    std::lock_guard<std::mutex> lockGuard(lock);
                    *resultPtr = std::move(r);
                    *gotResultPtr = true;
                }
                cv.notify_one();
            });
    getClient()->setValue(propValue, callback);

    std::unique_lock<std::mutex> lk(lock);
    cv.wait_for(lk, std::chrono::milliseconds(1000), [&gotResult] { return gotResult; });

    ASSERT_TRUE(gotResult);
    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), toInt(StatusCode::TRY_AGAIN));
}

TEST_F(AidlVhalClientTest, testSetValueErrorStatus) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setStatus(StatusCode::INTERNAL_ERROR);

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    Result<void> result;
    Result<void>* resultPtr = &result;

    getClient()->setValue(propValue,
                          std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
                                  [resultPtr](Result<void> r) { *resultPtr = std::move(r); }));

    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), toInt(StatusCode::INTERNAL_ERROR));
}

TEST_F(AidlVhalClientTest, testSetValueNonOkayResult) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    Result<void> result;
    Result<void>* resultPtr = &result;

    getClient()->setValue(propValue,
                          std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
                                  [resultPtr](Result<void> r) { *resultPtr = std::move(r); }));

    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_FALSE(result.ok());
    ASSERT_EQ(result.error().code(), toInt(StatusCode::INTERNAL_ERROR));
}

TEST_F(AidlVhalClientTest, testSetValueIgnoreInvalidRequestId) {
    VehiclePropValue testProp{
            .prop = TEST_PROP_ID,
            .areaId = TEST_AREA_ID,
    };
    getVhal()->setSetValueResults({
            SetValueResult{
                    .requestId = 0,
                    .status = StatusCode::OK,
            },
            // This result has invalid request ID and should be ignored.
            SetValueResult{
                    .requestId = 1,
                    .status = StatusCode::INTERNAL_ERROR,
            },
    });

    AidlHalPropValue propValue(TEST_PROP_ID, TEST_AREA_ID);
    Result<void> result;
    Result<void>* resultPtr = &result;

    getClient()->setValue(propValue,
                          std::make_shared<AidlVhalClient::SetValueCallbackFunc>(
                                  [resultPtr](Result<void> r) { *resultPtr = std::move(r); }));

    ASSERT_EQ(getVhal()->getSetValueRequests(),
              std::vector<SetValueRequest>({SetValueRequest{.requestId = 0, .value = testProp}}));
    ASSERT_TRUE(result.ok());
}

TEST_F(AidlVhalClientTest, testAddOnBinderDiedCallback) {
    struct Result {
        bool callbackOneCalled = false;
        bool callbackTwoCalled = false;
    } result;

    getClient()->addOnBinderDiedCallback(std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackOneCalled = true; }));
    getClient()->addOnBinderDiedCallback(std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackTwoCalled = true; }));
    triggerBinderDied();

    ASSERT_TRUE(result.callbackOneCalled);
    ASSERT_TRUE(result.callbackTwoCalled);

    triggerBinderUnlinked();

    ASSERT_EQ(countOnBinderDiedCallbacks(), static_cast<size_t>(0));
}

TEST_F(AidlVhalClientTest, testRemoveOnBinderDiedCallback) {
    struct Result {
        bool callbackOneCalled = false;
        bool callbackTwoCalled = false;
    } result;

    auto callbackOne = std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackOneCalled = true; });
    auto callbackTwo = std::make_shared<AidlVhalClient::OnBinderDiedCallbackFunc>(
            [&result] { result.callbackTwoCalled = true; });
    getClient()->addOnBinderDiedCallback(callbackOne);
    getClient()->addOnBinderDiedCallback(callbackTwo);
    getClient()->removeOnBinderDiedCallback(callbackOne);
    triggerBinderDied();

    ASSERT_FALSE(result.callbackOneCalled);
    ASSERT_TRUE(result.callbackTwoCalled);

    triggerBinderUnlinked();

    ASSERT_EQ(countOnBinderDiedCallbacks(), static_cast<size_t>(0));
}

}  // namespace test
}  // namespace vhal
}  // namespace automotive
}  // namespace frameworks
}  // namespace android
