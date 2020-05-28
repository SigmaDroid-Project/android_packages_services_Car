/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include <android/hardware/automotive/sv/1.0/types.h>
#include <android/hardware/automotive/sv/1.0/ISurroundViewStream.h>
#include <android/hardware/automotive/sv/1.0/ISurroundView3dSession.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

#include "CoreLibSetupHelper.h"
#include <thread>

#include <ui/GraphicBuffer.h>

using namespace ::android::hardware::automotive::sv::V1_0;
using ::android::hardware::Return;
using ::android::hardware::hidl_vec;
using ::android::sp;

using std::condition_variable;

using namespace android_auto::surround_view;

namespace android {
namespace hardware {
namespace automotive {
namespace sv {
namespace V1_0 {
namespace implementation {

class SurroundView3dSession : public ISurroundView3dSession {
public:
    SurroundView3dSession();

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundViewSession.
    Return<SvResult> startStream(
        const sp<ISurroundViewStream>& stream) override;
    Return<void> stopStream() override;
    Return<void> doneWithFrames(const SvFramesDesc& svFramesDesc) override;

    // Methods from ISurroundView3dSession follow.
    Return<SvResult> setViews(const hidl_vec<View3d>& views) override;
    Return<SvResult> set3dConfig(const Sv3dConfig& sv3dConfig) override;
    Return<void> get3dConfig(get3dConfig_cb _hidl_cb) override;
    Return<SvResult>  updateOverlays(const OverlaysData& overlaysData);
    Return<void> projectCameraPointsTo3dSurface(
        const hidl_vec<Point2dInt>& cameraPoints,
        const hidl_string& cameraId,
        projectCameraPointsTo3dSurface_cb _hidl_cb);

private:
    bool initialize();

    void generateFrames();
    void processFrames();

    bool handleFrames(int sequenceId);

    enum StreamStateValues {
        STOPPED,
        RUNNING,
        STOPPING,
        DEAD,
    };

    // Stream subscribed for the session.
    sp<ISurroundViewStream> mStream GUARDED_BY(mAccessLock);
    StreamStateValues mStreamState GUARDED_BY(mAccessLock);

    thread mCaptureThread; // The thread we'll use to synthesize frames
    thread mProcessThread; // The thread we'll use to process frames

    // Used to signal a set of frames is ready
    condition_variable mSignal GUARDED_BY(mAccessLock);
    bool framesAvailable GUARDED_BY(mAccessLock);

    int sequenceId;

    struct FramesRecord {
        SvFramesDesc frames;
        bool inUse = false;
    };

    FramesRecord framesRecord GUARDED_BY(mAccessLock);

    // Synchronization necessary to deconflict mCaptureThread from the main service thread
    mutex mAccessLock;

    vector<View3d> mViews GUARDED_BY(mAccessLock);

    Sv3dConfig mConfig GUARDED_BY(mAccessLock);

    vector<string> mEvsCameraIds GUARDED_BY(mAccessLock);

    unique_ptr<SurroundView> mSurroundView GUARDED_BY(mAccessLock);

    vector<SurroundViewInputBufferPointers>
        mInputPointers GUARDED_BY(mAccessLock);
    SurroundViewResultPointer mOutputPointer GUARDED_BY(mAccessLock);
    int mOutputWidth, mOutputHeight GUARDED_BY(mAccessLock);

    sp<GraphicBuffer> mSvTexture GUARDED_BY(mAccessLock);

    bool mIsInitialized GUARDED_BY(mAccessLock) = false;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace sv
}  // namespace automotive
}  // namespace hardware
}  // namespace android
