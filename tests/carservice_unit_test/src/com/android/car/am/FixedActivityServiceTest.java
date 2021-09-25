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

package com.android.car.am;

import static android.car.test.mocks.AndroidMockitoHelper.mockAmGetCurrentUser;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.app.ActivityManagerHelper.OnTaskStackChangeListener;
import android.car.builtin.app.ActivityManagerHelper.TopTaskInfoContainer;
import android.car.hardware.power.CarPowerManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.user.CarUserManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.SparseArray;
import android.view.Display;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.user.CarUserService;
import com.android.car.user.UserHandleHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.stubbing.OngoingStubbing;

import java.util.ArrayList;
import java.util.Arrays;

public final class FixedActivityServiceTest extends AbstractExtendedMockitoTestCase {

    private static final long RECHECK_INTERVAL_MARGIN_MS = 600;

    private final int mValidDisplayId = 1;

    @Mock
    private Context mContext;
    @Mock
    private ActivityManagerHelper mActivityManager;
    @Mock
    private UserManager mUserManager;
    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private CarUserService mCarUserService;
    @Mock
    private CarPowerManager mCarPowerManager;
    @Mock
    private UserHandleHelper mUserHandleHelper;
    @Mock
    private Display mValidDisplay;

    private FixedActivityService mFixedActivityService;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder
            .spyStatic(ActivityManager.class)
            .spyStatic(CarLocalServices.class);
    }

    @Before
    public void setUp() {
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        doReturn(mCarUserService).when(() -> CarLocalServices.getService(CarUserService.class));
        doReturn(mCarPowerManager).when(() -> CarLocalServices.createCarPowerManager(mContext));
        when(mDisplayManager.getDisplay(mValidDisplayId)).thenReturn(mValidDisplay);
        mFixedActivityService = new FixedActivityService(mContext, mActivityManager, mUserManager,
                mDisplayManager, mUserHandleHelper);
    }

    @After
    public void tearDown() {
        if (mFixedActivityService != null) {
            mFixedActivityService.release();
        }
        CarServiceUtils.finishAllHandlerTasks();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_noRunningActivity()
            throws Exception {
        int userId = 10;
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_alreadyRunningActivity()
            throws Exception {
        int userId = 10;
        int taskId = 1234;
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, mValidDisplayId, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();

        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        // startActivityAsUser should not called at this time. So, total called count is 1.
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_runNewActivity() throws Exception {
        int userId = 10;
        int taskId = 1234;
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        Intent anotherIntent = expectComponentAvailable("test_package_II", "com.test.dude_II",
                userId);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, mValidDisplayId, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        assertThat(ret).isTrue();

        // Start activity with new package
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(anotherIntent,
                options, mValidDisplayId, userId);
        verify(mContext).startActivityAsUser(eq(anotherIntent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplay_relaunchWithPackageUpdated() throws Exception {
        int userId = 10;
        int taskId = 1234;
        String packageName = "test_package";
        String className = "com.test.dude";
        ActivityOptions options = new ActivityOptions(new Bundle());
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        Intent intent = expectComponentAvailable(packageName, className, userId);
        mockAmGetCurrentUser(userId);
        expectRootTaskInfo(
                createEmptyTaskInfo(),
                createRootTaskInfo(intent, userId, mValidDisplayId, taskId)
        );

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        verify(mContext).registerReceiverForAllUsers(receiverCaptor.capture(),
                any(IntentFilter.class), eq(null), eq(null), anyInt());
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();

        // Update package
        SystemClock.sleep(RECHECK_INTERVAL_MARGIN_MS);
        int appId = 987;
        BroadcastReceiver receiver = receiverCaptor.getValue();
        Intent packageIntent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
        packageIntent.setData(new Uri.Builder().path("Any package").build());
        packageIntent.putExtra(Intent.EXTRA_UID, UserHandle.getUid(userId, appId));
        receiver.onReceive(mContext, packageIntent);
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));

        SystemClock.sleep(RECHECK_INTERVAL_MARGIN_MS);
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        // Activity should not be launched.
        verify(mContext).startActivityAsUser(eq(intent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }


    @Test
    public void testStartFixedActivityModeForDisplayAndUser_runOnDifferentDisplay()
            throws Exception {
        int userId = 10;
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        Intent anotherIntent = expectComponentAvailable("test_package_II", "com.test.dude_II",
                userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        assertThat(ret).isTrue();

        int anotherValidDisplayId = mValidDisplayId + 1;
        when(mDisplayManager.getDisplay(anotherValidDisplayId)).thenReturn(mValidDisplay);
        ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(anotherIntent,
                options, anotherValidDisplayId, userId);
        verify(mContext).startActivityAsUser(eq(anotherIntent), any(Bundle.class),
                eq(UserHandle.of(userId)));
        assertThat(ret).isTrue();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_invalidDisplay() {
        int userId = 10;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ActivityOptions options = new ActivityOptions(new Bundle());
        int invalidDisplayId = Display.DEFAULT_DISPLAY;

        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent, options,
                invalidDisplayId, userId);
        assertThat(ret).isFalse();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_unavailableDisplay() {
        int userId = 10;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ActivityOptions options = new ActivityOptions(new Bundle());
        int unavailableDisplayId = mValidDisplayId + 1;

        boolean started = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, unavailableDisplayId, userId);
        assertThat(started).isFalse();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_displayRemoved()
            throws Exception {
        int displayToBeRemoved = mValidDisplayId + 1;
        when(mDisplayManager.getDisplay(displayToBeRemoved)).thenReturn(
                mValidDisplay, // for startFixedActivityModeForDisplayAndUser
                mValidDisplay, // for launchIf
                null);
        int userId = 10;
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        boolean started = mFixedActivityService.startFixedActivityModeForDisplayAndUser(
                intent, options, displayToBeRemoved, userId);
        assertThat(started).isTrue();
        assertThat(mFixedActivityService.getRunningFixedActivity(displayToBeRemoved)).isNotNull();

        // The display is still valid.
        mFixedActivityService.launchIfNecessary();
        assertThat(mFixedActivityService.getRunningFixedActivity(displayToBeRemoved)).isNotNull();

        // The display is removed.
        mFixedActivityService.launchIfNecessary();
        assertThat(mFixedActivityService.getRunningFixedActivity(displayToBeRemoved)).isNull();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_notAllowedUser() {
        int currentUserId = 10;
        int notAllowedUserId = 11;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ActivityOptions options = new ActivityOptions(new Bundle());
        int displayId = mValidDisplayId;
        mockAmGetCurrentUser(currentUserId);
        expectNoProfileUser(currentUserId);

        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent, options,
                displayId, notAllowedUserId);
        assertThat(ret).isFalse();
    }

    @Test
    public void testStartFixedActivityModeForDisplayAndUser_invalidComponent() throws Exception {
        int userId = 10;
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent invalidIntent = expectComponentUnavailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);

        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(invalidIntent,
                options, mValidDisplayId, userId);
        assertThat(ret).isFalse();
    }

    @Test
    public void testStopFixedActivityMode() throws Exception {
        int userId = 10;
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", userId);
        mockAmGetCurrentUser(userId);
        expectNoActivityStack();

        // Start an activity
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, userId);
        // To check if monitoring is started.
        verify(mActivityManager).registerTaskStackChangeListener(
                any(OnTaskStackChangeListener.class));
        assertThat(ret).isTrue();

        mFixedActivityService.stopFixedActivityMode(mValidDisplayId);
        verify(mActivityManager).unregisterTaskStackChangeListener(
                any(OnTaskStackChangeListener.class));
    }

    @Test
    public void testStopFixedActivityMode_invalidDisplayId() throws Exception {
        mFixedActivityService.stopFixedActivityMode(Display.DEFAULT_DISPLAY);
        verify(mActivityManager, never()).unregisterTaskStackChangeListener(
                any(OnTaskStackChangeListener.class));
    }

    @Test
    public void onUserSwitch_clearsRunningActivities() throws Exception {
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ 10,
                /* toUserId = */ 11,
                /* runningFixedActivityExpected = */ false);
    }

    @Test
    public void onUserSwitchFromSystemUser_noChangeInRunningActivities() throws Exception {
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ UserHandle.USER_SYSTEM,
                /* toUserId = */ 11,
                /* runningFixedActivityExpected = */ true);
    }

    @Test
    public void onUserSwitchToUserWithEnabledProfile_noChangeInRunningActivities()
            throws Exception {
        when(mUserHandleHelper.getEnabledProfiles(11)).thenReturn(Arrays.asList(UserHandle.of(10)));
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ 10,
                /* toUserId = */ 11,
                /* runningFixedActivityExpected = */ true);
    }

    @Test
    public void onUserSwitchToSameUser_noChangeInRunningActivities() throws Exception {
        testClearingOfRunningActivitiesOnUserSwitch(
                /* fromUserId = */ 11,
                /* toUserId = */ 11,
                /* runningFixedActivityExpected = */ true);
    }

    private void testClearingOfRunningActivitiesOnUserSwitch(int fromUserId, int toUserId,
            boolean runningFixedActivityExpected) throws Exception {
        ActivityOptions options = new ActivityOptions(new Bundle());
        Intent intent = expectComponentAvailable("test_package", "com.test.dude", fromUserId);
        mockAmGetCurrentUser(fromUserId);
        expectNoActivityStack();
        doAnswer(invocation -> {
            CarUserManager.UserLifecycleListener userLifecycleListener =
                    (CarUserManager.UserLifecycleListener) invocation.getArgument(0);
            mockAmGetCurrentUser(toUserId);
            userLifecycleListener.onEvent(new CarUserManager.UserLifecycleEvent(
                    CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, toUserId));
            return null;
        }).when(mCarUserService).addUserLifecycleListener(any());

        // No running activities
        boolean ret = mFixedActivityService.startFixedActivityModeForDisplayAndUser(intent,
                options, mValidDisplayId, fromUserId);
        assertThat(ret).isTrue();
        verify(mCarUserService).addUserLifecycleListener(any());

        if (runningFixedActivityExpected) {
            assertThat(mFixedActivityService.getRunningFixedActivity(mValidDisplayId))
                    .isNotNull();
        } else {
            assertThat(mFixedActivityService.getRunningFixedActivity(mValidDisplayId))
                    .isNull();
        }
    }


    private void expectNoProfileUser(@UserIdInt int userId) {
        when(mUserHandleHelper.getEnabledProfiles(userId)).thenReturn(new ArrayList<UserHandle>());
    }

    private Intent expectComponentUnavailable(String pkgName, String className,
            @UserIdInt int userId) throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName component = new ComponentName(pkgName, className);
        intent.setComponent(component);
        ActivityInfo activityInfo = new ActivityInfo();
        // To make sure there is no matched activity
        activityInfo.name = component.getClassName() + ".unavailable";
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.activities = new ActivityInfo[] { activityInfo };
        when(mPackageManager.getPackageInfoAsUser(component.getPackageName(),
                PackageManager.GET_ACTIVITIES, userId)).thenReturn(packageInfo);
        return intent;
    }

    private Intent expectComponentAvailable(String pkgName, String className, @UserIdInt int userId)
            throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        ComponentName component = new ComponentName(pkgName, className);
        intent.setComponent(component);
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.name = component.getClassName();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.activities = new ActivityInfo[] { activityInfo };
        when(mPackageManager.getPackageInfoAsUser(component.getPackageName(),
                PackageManager.GET_ACTIVITIES, userId)).thenReturn(packageInfo);
        return intent;
    }

    private void expectNoActivityStack() throws Exception {
        when(mActivityManager.getTopTasks()).thenReturn(createEmptyTaskInfo());
    }

    private void expectRootTaskInfo(SparseArray<TopTaskInfoContainer>... taskInfos)
            throws Exception {
        OngoingStubbing<SparseArray<TopTaskInfoContainer>> stub = when(
                mActivityManager.getTopTasks());
        for (SparseArray<TopTaskInfoContainer> taskInfo : taskInfos) {
            stub = stub.thenReturn(taskInfo);
        }
    }

    private SparseArray<TopTaskInfoContainer> createEmptyTaskInfo() {
        return new SparseArray<>();
    }

    private SparseArray<TopTaskInfoContainer> createRootTaskInfo(Intent intent,
            @UserIdInt int userId,
            int displayId, int taskId) {
        TopTaskInfoContainer taskInfo = new TopTaskInfoContainer(intent.getComponent().clone(),
                taskId, userId, /* rootTaskId= */ -1, /* rootTaskUserId= */ -1, displayId,
                /* position= */ 0, /* baseActivity= */ null, /* childTaskIds= */ new int[]{taskId},
                /* childTaskNames= */null);
        SparseArray<TopTaskInfoContainer> topTasks = new SparseArray<>();
        topTasks.put(displayId, taskInfo);
        return topTasks;
    }
}
