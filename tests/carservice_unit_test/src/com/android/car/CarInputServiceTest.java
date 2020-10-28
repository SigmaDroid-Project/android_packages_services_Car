/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car;

import static android.car.CarOccupantZoneManager.DisplayTypeEnum;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.car.CarOccupantZoneManager;
import android.car.CarProjectionManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.service.voice.VoiceInteractionSession;
import android.telecom.TelecomManager;
import android.view.KeyEvent;

import androidx.test.core.app.ApplicationProvider;

import com.android.car.hal.InputHalService;
import com.android.car.user.CarUserService;
import com.android.internal.app.AssistUtils;

import com.google.common.collect.Range;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.testng.Assert;

import java.util.BitSet;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

@RunWith(MockitoJUnitRunner.class)
public class CarInputServiceTest {

    @Mock InputHalService mInputHalService;
    @Mock TelecomManager mTelecomManager;
    @Mock AssistUtils mAssistUtils;
    @Mock CarInputService.KeyEventListener mDefaultMainListener;
    @Mock CarInputService.KeyEventListener mInstrumentClusterKeyListener;
    @Mock Supplier<String> mLastCallSupplier;
    @Mock IntSupplier mLongPressDelaySupplier;
    @Mock InputCaptureClientController mCaptureController;
    @Mock CarOccupantZoneService mCarOccupantZoneService;

    @Spy Context mContext = ApplicationProvider.getApplicationContext();
    @Spy Handler mHandler = new Handler(Looper.getMainLooper());

    @Mock CarUserService mCarUserService;
    private CarInputService mCarInputService;

    @Before
    public void setUp() {
        mCarUserService = mock(CarUserService.class);

        mCarInputService = new CarInputService(mContext, mInputHalService, mCarUserService,
                mCarOccupantZoneService, mHandler, mTelecomManager, mAssistUtils,
                mDefaultMainListener, mLastCallSupplier, mLongPressDelaySupplier,
                mCaptureController);
        mCarInputService.setInstrumentClusterKeyListener(mInstrumentClusterKeyListener);

        when(mInputHalService.isKeyInputSupported()).thenReturn(true);
        mCarInputService.init();

        // Delay Handler callbacks until flushHandler() is called.
        doReturn(true).when(mHandler).sendMessageAtTime(any(), anyLong());
    }

    @Test
    public void ordinaryEvents_onMainDisplay_routedToInputManager() {
        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.MAIN);

        verify(mDefaultMainListener).onKeyEvent(event);
    }

    @Test
    public void ordinaryEvents_onInstrumentClusterDisplay_notRoutedToInputManager() {
        send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.INSTRUMENT_CLUSTER);

        verify(mDefaultMainListener, never()).onKeyEvent(any());
    }

    @Test
    public void ordinaryEvents_onInstrumentClusterDisplay_routedToListener() {
        CarInputService.KeyEventListener listener = mock(CarInputService.KeyEventListener.class);
        mCarInputService.setInstrumentClusterKeyListener(listener);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.INSTRUMENT_CLUSTER);
        verify(listener).onKeyEvent(event);
    }

    @Test
    public void customEventHandler_capturesDisplayMainEvent_capturedByInputController() {
        CarInputService.KeyEventListener instrumentClusterListener =
                setupInstrumentClusterListener();

        // Assume mCaptureController will consume every event.
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(true);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.MAIN);
        verify(instrumentClusterListener, never()).onKeyEvent(any(KeyEvent.class));
        verify(mCaptureController).onKeyEvent(CarOccupantZoneManager.DISPLAY_TYPE_MAIN, event);
        verify(mDefaultMainListener, never()).onKeyEvent(any(KeyEvent.class));
    }

    @Test
    public void customEventHandler_capturesDisplayMainEvent_missedByInputController() {
        CarInputService.KeyEventListener instrumentClusterListener =
                setupInstrumentClusterListener();

        // Assume mCaptureController will consume every event.
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(false);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.MAIN);
        verify(instrumentClusterListener, never()).onKeyEvent(any(KeyEvent.class));
        verify(mCaptureController).onKeyEvent(anyInt(), any(KeyEvent.class));
        verify(mDefaultMainListener).onKeyEvent(event);
    }

    @Test
    public void customEventHandler_capturesClusterEvents_capturedByInstrumentCluster() {
        CarInputService.KeyEventListener instrumentClusterListener =
                setupInstrumentClusterListener();

        // Assume mCaptureController will consume every event.
        when(mCaptureController.onKeyEvent(anyInt(), any(KeyEvent.class))).thenReturn(true);

        KeyEvent event = send(Key.DOWN, KeyEvent.KEYCODE_ENTER, Display.INSTRUMENT_CLUSTER);
        verify(instrumentClusterListener).onKeyEvent(event);
        verify(mCaptureController, never()).onKeyEvent(anyInt(), any(KeyEvent.class));
        verify(mDefaultMainListener, never()).onKeyEvent(any(KeyEvent.class));
    }

    private CarInputService.KeyEventListener setupInstrumentClusterListener() {
        CarInputService.KeyEventListener instrumentClusterListener =
                mock(CarInputService.KeyEventListener.class);
        mCarInputService.setInstrumentClusterKeyListener(instrumentClusterListener);
        return instrumentClusterListener;
    }

    @Test
    public void voiceKey_shortPress_withRegisteredEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
    }

    @Test
    public void voiceKey_longPress_withRegisteredEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP,
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        verify(eventHandler, never()).onKeyEvent(anyInt());

        // Simulate the long-press timer expiring.
        flushHandler();
        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);

        // Ensure that the short-press handler is *not* called.
        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        flushHandler();
        verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
    }

    @Test
    public void voiceKey_shortPress_withoutRegisteredEventHandler_triggersAssistUtils() {
        when(mAssistUtils.getAssistComponentForUser(anyInt()))
                .thenReturn(new ComponentName("pkg", "cls"));

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mAssistUtils).showSessionForActiveService(
                bundleCaptor.capture(),
                eq(VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK),
                any(),
                isNull());
        assertThat(bundleCaptor.getValue().getBoolean(CarInputService.EXTRA_CAR_PUSH_TO_TALK))
                .isTrue();
    }

    @Test
    public void voiceKey_longPress_withoutRegisteredEventHandler_triggersAssistUtils() {
        when(mAssistUtils.getAssistComponentForUser(anyInt()))
                .thenReturn(new ComponentName("pkg", "cls"));

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        flushHandler();

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mAssistUtils).showSessionForActiveService(
                bundleCaptor.capture(),
                eq(VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK),
                any(),
                isNull());
        assertThat(bundleCaptor.getValue().getBoolean(CarInputService.EXTRA_CAR_PUSH_TO_TALK))
                .isTrue();

        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        verifyNoMoreInteractions(ignoreStubs(mAssistUtils));
    }

    @Test
    public void voiceKey_keyDown_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_KEY_DOWN);
    }

    @Test
    public void voiceKey_keyUp_afterLongPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        flushHandler();
        verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);

        send(Key.UP, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        verify(eventHandler)
                .onKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);
    }

    @Test
    public void voiceKey_repeatedEvents_ignored() {
        // Pressing a key starts the long-press timer.
        send(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN);
        verify(mHandler).sendMessageAtTime(any(), anyLong());
        clearInvocations(mHandler);

        // Repeated KEY_DOWN events don't reset the timer.
        sendWithRepeat(Key.DOWN, KeyEvent.KEYCODE_VOICE_ASSIST, Display.MAIN, 1);
        verify(mHandler, never()).sendMessageAtTime(any(), anyLong());
    }

    @Test
    public void callKey_shortPress_withoutEventHandler_launchesDialer() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        doNothing().when(mContext).startActivityAsUser(any(), any(), any());

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mContext).startActivityAsUser(
                intentCaptor.capture(), any(), eq(UserHandle.CURRENT_OR_SELF));
        assertThat(intentCaptor.getValue().getAction()).isEqualTo(Intent.ACTION_DIAL);
    }

    @Test
    public void callKey_shortPress_withoutEventHandler_whenCallRinging_answersCall() {
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mTelecomManager).acceptRingingCall();
        // Ensure default handler does not run.
        verify(mContext, never()).startActivityAsUser(any(), any(), any());
    }

    @Test
    public void callKey_shortPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        // Ensure default handlers do not run.
        verify(mTelecomManager, never()).acceptRingingCall();
        verify(mContext, never()).startActivityAsUser(any(), any(), any());
    }

    @Test
    public void callKey_shortPress_withEventHandler_whenCallRinging_answersCall() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP);
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(mTelecomManager).acceptRingingCall();
        verify(eventHandler, never()).onKeyEvent(anyInt());
    }

    @Test
    public void callKey_longPress_withoutEventHandler_redialsLastCall() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);

        when(mLastCallSupplier.get()).thenReturn("1234567890");
        doNothing().when(mContext).startActivityAsUser(any(), any(), any());

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mContext).startActivityAsUser(
                intentCaptor.capture(), any(), eq(UserHandle.CURRENT_OR_SELF));

        Intent intent = intentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_CALL);
        assertThat(intent.getData()).isEqualTo(Uri.parse("tel:1234567890"));

        clearInvocations(mContext);
        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        verify(mContext, never()).startActivityAsUser(any(), any(), any());
    }

    @Test
    public void callKey_longPress_withoutEventHandler_withNoLastCall_doesNothing() {
        when(mLastCallSupplier.get()).thenReturn("");

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mContext, never()).startActivityAsUser(any(), any(), any());
    }

    @Test
    public void callKey_longPress_withoutEventHandler_whenCallRinging_answersCall() {
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mTelecomManager).acceptRingingCall();

        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        // Ensure that default handler does not run, either after accepting ringing call,
        // or as a result of key-up.
        verify(mContext, never()).startActivityAsUser(any(), any(), any());
    }

    @Test
    public void callKey_longPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);
        verify(mContext, never()).startActivityAsUser(any(), any(), any());
    }

    @Test
    public void callKey_longPress_withEventHandler_whenCallRinging_answersCall() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN);
        when(mTelecomManager.isRinging()).thenReturn(true);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();

        verify(mTelecomManager).acceptRingingCall();

        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        // Ensure that event handler does not run, either after accepting ringing call,
        // or as a result of key-up.
        verify(eventHandler, never()).onKeyEvent(anyInt());
    }

    @Test
    public void callKey_keyDown_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);

        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
    }

    @Test
    public void callKey_keyUp_afterLongPress_withEventHandler_triggersEventHandler() {
        CarProjectionManager.ProjectionKeyEventHandler eventHandler =
                registerProjectionKeyEventHandler(
                        CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);

        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        flushHandler();
        verify(eventHandler, never())
                .onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);

        send(Key.UP, KeyEvent.KEYCODE_CALL, Display.MAIN);
        verify(eventHandler).onKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);
    }

    @Test
    public void callKey_repeatedEvents_ignored() {
        // Pressing a key starts the long-press timer.
        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        verify(mHandler).sendMessageAtTime(any(), anyLong());
        clearInvocations(mHandler);

        // Repeated KEY_DOWN events don't reset the timer.
        sendWithRepeat(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN, 1);
        verify(mHandler, never()).sendMessageAtTime(any(), anyLong());
    }

    @Test
    public void longPressDelay_obeysValueFromSystem() {
        final int systemDelay = 4242;

        when(mLongPressDelaySupplier.getAsInt()).thenReturn(systemDelay);
        ArgumentCaptor<Long> timeCaptor = ArgumentCaptor.forClass(long.class);

        long then = SystemClock.uptimeMillis();
        send(Key.DOWN, KeyEvent.KEYCODE_CALL, Display.MAIN);
        long now = SystemClock.uptimeMillis();

        verify(mHandler).sendMessageAtTime(any(), timeCaptor.capture());

        // The message time must be the expected delay time (as provided by the supplier) after
        // the time the message was sent to the handler. We don't know that exact time, but we
        // can put a bound on it - it's no sooner than immediately before the call to send(), and no
        // later than immediately afterwards. Check to make sure the actual observed message time is
        // somewhere in the valid range.

        assertThat(timeCaptor.getValue()).isIn(Range.closed(then + systemDelay, now + systemDelay));
    }

    @Test
    public void injectKeyEvent_throwsSecurityExceptionWithoutInjectEventsPermission() {
        // Arrange
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);

        // Act and assert
        Assert.assertThrows(SecurityException.class,
                () -> mCarInputService.injectKeyEvent(event,
                        CarOccupantZoneManager.DISPLAY_TYPE_MAIN));
    }

    @Test
    public void injectKeyEvent_delegatesToOnKeyEvent() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        event.setDisplayId(android.view.Display.INVALID_DISPLAY);

        injectKeyEventAndVerify(event, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);

        verify(mDefaultMainListener).onKeyEvent(event);
        verify(mInstrumentClusterKeyListener, never()).onKeyEvent(event);
    }

    @Test
    public void injectKeyEvent_sendingKeyEventWithDefaultDisplayAgainstClusterDisplayType() {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        event.setDisplayId(android.view.Display.DEFAULT_DISPLAY);

        injectKeyEventAndVerify(event, CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);

        verify(mDefaultMainListener, never()).onKeyEvent(event);
        verify(mInstrumentClusterKeyListener).onKeyEvent(event);
    }

    private void injectKeyEventAndVerify(KeyEvent event, @DisplayTypeEnum int displayType) {
        // Arrange
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                android.Manifest.permission.INJECT_EVENTS);

        int anyDriverUserId = 1;
        int anyZoneId = 1;
        int someDisplayId = Integer.MAX_VALUE;
        when(mCarOccupantZoneService.getDriverUserId()).thenReturn(anyDriverUserId);
        when(mCarOccupantZoneService.getOccupantZoneIdForUserId(anyInt())).thenReturn(anyZoneId);
        when(mCarOccupantZoneService.getDisplayForOccupant(anyInt(), anyInt())).thenReturn(
                someDisplayId);

        assertThat(event.getDisplayId()).isNotEqualTo(someDisplayId);

        // Act
        mCarInputService.injectKeyEvent(event, displayType);

        // Assert display id was updated as expected
        assertThat(event.getDisplayId()).isEqualTo(someDisplayId);
    }

    private enum Key {DOWN, UP}

    private enum Display {MAIN, INSTRUMENT_CLUSTER}

    private KeyEvent send(Key action, int keyCode, Display display) {
        return sendWithRepeat(action, keyCode, display, 0);
    }

    private KeyEvent sendWithRepeat(Key action, int keyCode, Display display, int repeatCount) {
        KeyEvent event = new KeyEvent(
                /* downTime= */ 0L,
                /* eventTime= */ 0L,
                action == Key.DOWN ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                keyCode,
                repeatCount);
        mCarInputService.onKeyEvent(
                event,
                display == Display.MAIN
                        ? CarOccupantZoneManager.DISPLAY_TYPE_MAIN
                        : CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
        return event;
    }

    private CarProjectionManager.ProjectionKeyEventHandler registerProjectionKeyEventHandler(
            int... events) {
        BitSet eventSet = new BitSet();
        for (int event : events) {
            eventSet.set(event);
        }

        CarProjectionManager.ProjectionKeyEventHandler projectionKeyEventHandler =
                mock(CarProjectionManager.ProjectionKeyEventHandler.class);
        mCarInputService.setProjectionKeyEventHandler(projectionKeyEventHandler, eventSet);
        return projectionKeyEventHandler;
    }

    private void flushHandler() {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        verify(mHandler, atLeast(0)).sendMessageAtTime(messageCaptor.capture(), anyLong());

        for (Message message : messageCaptor.getAllValues()) {
            mHandler.dispatchMessage(message);
        }

        clearInvocations(mHandler);
    }
}
