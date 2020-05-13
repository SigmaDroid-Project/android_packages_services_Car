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
package android.car.test.mocks;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.test.util.UserTestingHelper;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Provides common Mockito calls for core Android classes.
 */
public final class AndroidMockitoHelper {

    /**
     * Mocks a call to {@link ActivityManager#getCurrentUser()}.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(ActivityManager.class)}.
     *
     * @param userId result of such call
     */
    public static void mockAmGetCurrentUser(@UserIdInt int userId) {
        doReturn(userId).when(() -> ActivityManager.getCurrentUser());
    }

    /**
     * Mocks a call to {@link UserManager#isHeadlessSystemUserMode()}.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@linkcom.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(UserManager.class)}.
     *
     * @param mode result of such call
     */
    public static void mockUmIsHeadlessSystemUserMode(boolean mode) {
        doReturn(mode).when(() -> UserManager.isHeadlessSystemUserMode());
    }

    /**
     * Mocks {@code UserManager.getUserInfo(userId)} to return a {@link UserInfo} with the given
     * {@code flags}.
     */
    @NonNull
    public static UserInfo mockUmGetUserInfo(@NonNull UserManager um, @UserIdInt int userId,
            @UserInfoFlag int flags) {
        Objects.requireNonNull(um);
        UserInfo user = new UserTestingHelper.UserInfoBuilder(userId).setFlags(flags).build();
        mockUmGetUserInfo(um, user);
        return user;
    }

    /**
     * Mocks {@code UserManager.getUserInfo(userId)} to return the given {@link UserInfo}.
     */
    @NonNull
    public static void mockUmGetUserInfo(@NonNull UserManager um, @NonNull UserInfo user) {
        when(um.getUserInfo(user.id)).thenReturn(user);
    }

    /**
     * Mocks {@code UserManager.getUserInfo(userId)} when the {@code userId} is the system user's.
     */
    @NonNull
    public static void mockUmGetSystemUser(@NonNull UserManager um) {
        UserInfo user = new UserTestingHelper.UserInfoBuilder(UserHandle.USER_SYSTEM)
                .setFlags(UserInfo.FLAG_SYSTEM).build();
        when(um.getUserInfo(UserHandle.USER_SYSTEM)).thenReturn(user);
    }

    /**
     * Mocks {@code UserManager.getUsers(excludeDying)} to return the given users.
     */
    public static void mockUmGetUsers(@NonNull UserManager um, @NonNull UserInfo... users) {
        Objects.requireNonNull(um);
        List<UserInfo> testUsers = Arrays.stream(users).collect(Collectors.toList());
        when(um.getUsers(/* excludeDying= */ true)).thenReturn(testUsers);
    }

    /**
     * Mocks {@code UserManager.getUsers(excludeDying)} to return simple users with the given ids.
     */
    public static void mockUmGetUsers(@NonNull UserManager um, @NonNull @UserIdInt int... userIds) {
        List<UserInfo> users = UserTestingHelper.newUsers(userIds);
        when(um.getUsers(/* excludeDying= */ true)).thenReturn(users);
    }

    /**
     * Mocks a call to {@code UserManager.getUsers()}.
     */
    public static void mockUmGetUsers(@NonNull UserManager um, @NonNull List<UserInfo> userInfos) {
        when(um.getUsers()).thenReturn(userInfos);
    }

    /**
     * Mocks a call to {@code UserManager.isUserRunning(userId)}.
     */
    public static void mockUmIsUserRunning(@NonNull UserManager um, @UserIdInt int userId,
            boolean isRunning) {
        when(um.isUserRunning(userId)).thenReturn(isRunning);
    }

    /**
     * Mocks a call to {@link ServiceManager#getService(name)}.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(ServiceManager.class)}.
     *
     * @param name interface name of the service
     * @param binder result of such call
     */
    public static void mockSmGetService(@NonNull String name, @NonNull IBinder binder) {
        doReturn(binder).when(() -> ServiceManager.getService(name));
    }

    /**
     * Returns mocked binder implementation from the given interface name.
     *
     * <p><b>Note: </b>it must be made inside a
     * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession} built with
     * {@code spyStatic(ServiceManager.class)}.
     *
     * @param name interface name of the service
     * @param binder mocked return of ServiceManager.getService
     * @param service binder implementation
     */
    public static <T extends IInterface> void mockQueryService(@NonNull String name,
            @NonNull IBinder binder, @NonNull T service) {
        doReturn(binder).when(() -> ServiceManager.getService(name));
        when(binder.queryLocalInterface(anyString())).thenReturn(service);
    }

    private AndroidMockitoHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
