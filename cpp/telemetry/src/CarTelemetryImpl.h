/*
 * Copyright (c) 2021, The Android Open Source Project
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

#ifndef CPP_TELEMETRY_SRC_CARTELEMETRYIMPL_H_
#define CPP_TELEMETRY_SRC_CARTELEMETRYIMPL_H_

#include <android/frameworks/automotive/telemetry/BnCarTelemetry.h>
#include <android/frameworks/automotive/telemetry/CarData.h>
#include <utils/String16.h>
#include <utils/Vector.h>

#include <RingBuffer.h>

#include <memory>
#include <vector>

namespace android {
namespace automotive {
namespace telemetry {

// Implementation of android.frameworks.automotive.telemetry.ICarTelemetry.
class CarTelemetryImpl : public android::frameworks::automotive::telemetry::BnCarTelemetry {
public:
    // Doesn't own `buffer`.
    explicit CarTelemetryImpl(RingBuffer* buffer);

    android::binder::Status write(
            const std::vector<android::frameworks::automotive::telemetry::CarData>& dataList)
            override;

    status_t dump(int fd, const android::Vector<android::String16>& args) override;

private:
    RingBuffer* mRingBuffer;  // not owned
};

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

#endif  // CPP_TELEMETRY_SRC_CARTELEMETRYIMPL_H_
