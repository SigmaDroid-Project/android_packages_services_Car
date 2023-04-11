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

package android.car.builtin.window;

import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.annotation.SystemApi;
import android.car.builtin.annotation.AddedIn;
import android.car.builtin.annotation.PlatformVersion;
import android.os.Build;
import android.view.WindowManager;

/**
 * A helper class to access hidden functionality in {@link WindowManager}.
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class WindowManagerHelper {
    /**
     * See {@link WindowManager.LayoutParams#inputFeatures}}.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @AddedIn(PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public static void setInputFeatureSpy(@NonNull WindowManager.LayoutParams p) {
        p.inputFeatures = WindowManager.LayoutParams.INPUT_FEATURE_SPY;
    }

    /**
     * See {@link WindowManager.LayoutParams#privateFlags}}.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @AddedIn(PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public static void setTrustedOverlay(@NonNull WindowManager.LayoutParams p) {
        p.setTrustedOverlay();
    }

    private WindowManagerHelper() {
        throw new UnsupportedOperationException();
    }
}
