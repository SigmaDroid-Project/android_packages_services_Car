/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.portraitlauncher.controlbar.dialer;

import android.telecom.Call;

import com.android.car.carlauncher.homescreen.audio.InCallModel;

import java.time.Clock;

/** A wrapper around InCallModel to track when an active call is in progress. */
public class DialerCardModel extends InCallModel {

    private boolean mHasActiveCall;

    public DialerCardModel(Clock elapsedTimeClock) {
        super(elapsedTimeClock);
    }

    /** Indicates whether there is an active call or not. */
    public boolean hasActiveCall() {
        return mHasActiveCall;
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        mHasActiveCall = call != null;
    }

    @Override
    public void onCallRemoved(Call call) {
        mHasActiveCall = false;
        super.onCallRemoved(call);
    }
}