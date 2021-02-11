/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.custominput.sample;

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;

import android.app.ActivityOptions;
import android.car.CarOccupantZoneManager;
import android.car.input.CustomInputEvent;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Handles incoming {@link CustomInputEvent}. In this implementation, incoming events are expected
 * to have the display id and the function set.
 */
public final class CustomInputEventListener {

    private static final String TAG = CustomInputEventListener.class.getSimpleName();

    private final SampleCustomInputService mService;
    private final Context mContext;
    private final CarAudioManager mCarAudioManager;
    private final CarOccupantZoneManager mCarOccupantZoneManager;

    /** List of defined actions for this reference service implementation */
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventAction {

        /** Launches Map action. */
        int LAUNCH_MAPS_ACTION = CustomInputEvent.INPUT_CODE_F1;

        /** Accepts incoming call action. */
        int ACCEPT_INCOMING_CALL_ACTION = CustomInputEvent.INPUT_CODE_F2;

        /** Rejects incoming call action. */
        int REJECT_INCOMING_CALL_ACTION = CustomInputEvent.INPUT_CODE_F3;

        /** Increases media volume action. */
        int INCREASE_MEDIA_VOLUME_ACTION = CustomInputEvent.INPUT_CODE_F4;

        /** Increases media volume action. */
        int DECREASE_MEDIA_VOLUME_ACTION = CustomInputEvent.INPUT_CODE_F5;

        /** Increases alarm volume action. */
        int INCREASE_ALARM_VOLUME_ACTION = CustomInputEvent.INPUT_CODE_F6;

        /** Increases alarm volume action. */
        int DECREASE_ALARM_VOLUME_ACTION = CustomInputEvent.INPUT_CODE_F7;

        /** Simulates the HOME button (re-injects the HOME KeyEvent against Car Input API. */
        int BACK_HOME_ACTION = CustomInputEvent.INPUT_CODE_F8;
    }

    public CustomInputEventListener(
            @NonNull Context context,
            @NonNull CarAudioManager carAudioManager,
            @NonNull CarOccupantZoneManager carOccupantZoneManager,
            @NonNull SampleCustomInputService service) {
        mContext = context;
        mCarAudioManager = carAudioManager;
        mCarOccupantZoneManager = carOccupantZoneManager;
        mService = service;
    }

    public void handle(int targetDisplayType, CustomInputEvent event) {
        if (!isValidTargetDisplayType(targetDisplayType)) {
            return;
        }
        int targetDisplayId = getDisplayIdForDisplayType(targetDisplayType);
        @EventAction int action = event.getInputCode();
        switch (action) {
            case EventAction.LAUNCH_MAPS_ACTION:
                launchMap(targetDisplayId);
                break;
            case EventAction.ACCEPT_INCOMING_CALL_ACTION:
                acceptIncomingCall(targetDisplayType);
                break;
            case EventAction.REJECT_INCOMING_CALL_ACTION:
                rejectIncomingCall(targetDisplayType);
                break;
            case EventAction.INCREASE_MEDIA_VOLUME_ACTION:
                increaseVolume(targetDisplayId, AudioAttributes.USAGE_MEDIA);
                break;
            case EventAction.DECREASE_MEDIA_VOLUME_ACTION:
                decreaseVolume(targetDisplayId, AudioAttributes.USAGE_MEDIA);
                break;
            case EventAction.INCREASE_ALARM_VOLUME_ACTION:
                increaseVolume(targetDisplayId, AudioAttributes.USAGE_ALARM);
                break;
            case EventAction.DECREASE_ALARM_VOLUME_ACTION:
                decreaseVolume(targetDisplayId, AudioAttributes.USAGE_ALARM);
                break;
            case EventAction.BACK_HOME_ACTION:
                launchHome(targetDisplayType);
                break;
            default:
                Log.e(TAG, "Ignoring event [" + action + "]");
        }
    }

    private int getDisplayIdForDisplayType(int targetDisplayType) {
        int displayId = mCarOccupantZoneManager.getDisplayIdForDriver(targetDisplayType);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Resolved display id {" + displayId + "} for display type {"
                    + targetDisplayType + "}");
        }
        return displayId;
    }

    private int getOccupantZoneIdForDisplayId(/* unused for now */ int displayId) {
        // TODO(b/170975186): Use CarOccupantZoneManager to retrieve the associated zoneId with
        //                  the display id passed as parameter.
        return PRIMARY_AUDIO_ZONE;
    }

    private static boolean isValidTargetDisplayType(int displayType) {
        if (displayType == CarOccupantZoneManager.DISPLAY_TYPE_MAIN) {
            return true;
        }
        Log.w(TAG,
                "This service implementation can only handle CustomInputEvent with "
                        + "targetDisplayType set to main display (main display type is {"
                        + CarOccupantZoneManager.DISPLAY_TYPE_MAIN + "}), current display type is {"
                        + displayType + "})");
        return false;
    }

    private void launchMap(int targetDisplayId) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Launching Maps on display id {" + targetDisplayId + "}");
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(targetDisplayId);
        Intent mapsIntent = new Intent(Intent.ACTION_VIEW);
        mapsIntent.setClassName(mContext.getString(R.string.maps_app_package),
                mContext.getString(R.string.maps_activity_class));
        mapsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mService.startActivity(mapsIntent, options.toBundle());
    }

    private void acceptIncomingCall(int targetDisplayType) {
        Log.i(TAG, "Entering acceptIncomingCall");
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Accepting incoming call on display id {" + targetDisplayType + "}");
        }
        injectKeyEvent(targetDisplayType, KeyEvent.KEYCODE_CALL);
    }

    private void rejectIncomingCall(int targetDisplayType) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Rejecting incoming call on display id {" + targetDisplayType + "}");
        }
        injectKeyEvent(targetDisplayType, KeyEvent.KEYCODE_ENDCALL);
    }

    private void increaseVolume(int targetDisplayId, int usage) {
        int zoneId = getOccupantZoneIdForDisplayId(targetDisplayId);
        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(zoneId, usage);
        int maxVolume = mCarAudioManager.getGroupMaxVolume(zoneId, volumeGroupId);
        int volume = mCarAudioManager.getGroupVolume(zoneId, volumeGroupId);
        if (volume > maxVolume) {
            throw new IllegalStateException("Volume (" + volume + ") is higher than MaxVolume ("
                    + maxVolume + ") for zoneId {" + zoneId + "} and volumeGroupId {"
                    + volumeGroupId + "}");
        }
        String usageName = AudioAttributes.usageToString(usage);
        if (volume == maxVolume) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Current " + usageName + " volume is already equal to max volume ("
                        + maxVolume + ")");
            }
            return;
        }
        volume++;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Increasing " + usageName + " volume to: " + volume + " (max volume is "
                    + maxVolume + ")");
        }
        mCarAudioManager.setGroupVolume(volumeGroupId, volume, AudioManager.FLAG_SHOW_UI);
    }

    private void decreaseVolume(int targetDisplayId, int usage) {
        int zoneId = getOccupantZoneIdForDisplayId(targetDisplayId);
        int volumeGroupId = mCarAudioManager.getVolumeGroupIdForUsage(zoneId, usage);
        int minVolume = mCarAudioManager.getGroupMinVolume(zoneId, volumeGroupId);
        int volume = mCarAudioManager.getGroupVolume(zoneId, volumeGroupId);
        if (volume < minVolume) {
            throw new IllegalStateException("Volume (" + volume + ") is lower than MinVolume ("
                    + minVolume + ") for zoneId {" + zoneId + "} and volumeGroupId {"
                    + volumeGroupId + "}");
        }
        String usageName = AudioAttributes.usageToString(usage);
        if (volume == minVolume) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Current " + usageName + " volume is already equal to min volume ("
                        + minVolume + ")");
            }
            return;
        }
        volume--;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Decreasing " + usageName + " volume to: " + volume + " (min volume is "
                    + minVolume + ")");
        }
        mCarAudioManager.setGroupVolume(volumeGroupId, volume, AudioManager.FLAG_SHOW_UI);
    }

    private void launchHome(int targetDisplayType) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Injecting HOME KeyEvent on display type {" + targetDisplayType + "}");
        }
        injectKeyEvent(targetDisplayType, KeyEvent.KEYCODE_HOME);
    }

    private void injectKeyEvent(int targetDisplayType, int keyCode) {
        long currentTime = SystemClock.uptimeMillis();
        KeyEvent keyDown = new KeyEvent(/* downTime= */ currentTime, /* eventTime= */ currentTime,
                KeyEvent.ACTION_DOWN, keyCode, /* repeat= */ 0);
        mService.injectKeyEvent(keyDown, targetDisplayType);

        KeyEvent keyUp = new KeyEvent(/* downTime= */ currentTime, /* eventTime= */ currentTime,
                KeyEvent.ACTION_UP, keyCode, /* repeat= */ 0);
        mService.injectKeyEvent(keyUp, targetDisplayType);
    }
}
