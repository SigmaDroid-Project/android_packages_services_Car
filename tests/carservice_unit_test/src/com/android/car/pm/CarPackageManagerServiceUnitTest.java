/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.pm;

import static android.Manifest.permission.QUERY_ALL_PACKAGES;
import static android.car.content.pm.CarPackageManager.CAR_TARGET_VERSION_UNDEFINED;
import static android.car.content.pm.CarPackageManager.MANIFEST_METADATA_TARGET_CAR_MAJOR_VERSION;
import static android.car.content.pm.CarPackageManager.MANIFEST_METADATA_TARGET_CAR_MINOR_VERSION;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.PendingIntent;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.CarOccupantZoneService;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.am.CarActivityService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link CarPackageManagerService}.
 */
@RunWith(AndroidJUnit4.class)
public class CarPackageManagerServiceUnitTest extends AbstractExtendedMockitoTestCase {
    CarPackageManagerService mService;

    private Context mSpiedContext;

    private final UserHandle mUserHandle = UserHandle.of(666);

    @Mock
    private Context mUserContext;

    @Mock
    private CarUxRestrictionsManagerService mMockUxrService;
    @Mock
    private CarActivityService mMockActivityService;
    @Mock
    private CarOccupantZoneService mMockCarOccupantZoneService;
    @Mock
    private PendingIntent mMockPendingIntent;

    public CarPackageManagerServiceUnitTest() {
        super(CarPackageManagerService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        builder
            .spyStatic(ActivityManagerHelper.class)
            .spyStatic(Binder.class)
            // Need to mock service itself because of getTargetCarVersion() - it doesn't make sense
            // to test all variations of the methods that call it
            .spyStatic(CarPackageManagerService.class);
    }

    @Before
    public void setUp() {
        mSpiedContext = spy(InstrumentationRegistry.getInstrumentation().getTargetContext());

        doReturn(mUserContext).when(mSpiedContext).createContextAsUser(mUserHandle, /* flags= */ 0);

        mService = new CarPackageManagerService(mSpiedContext,
                mMockUxrService, mMockActivityService, mMockCarOccupantZoneService);
    }

    @Test
    public void testParseConfigList_SingleActivity() {
        String config = "com.android.test/.TestActivity";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertThat(map.get("com.android.test")).containsExactly(".TestActivity");
    }

    @Test
    public void testParseConfigList_Package() {
        String config = "com.android.test";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertThat(map.get("com.android.test")).isEmpty();
    }

    @Test
    public void testParseConfigList_MultipleActivities() {
        String config = "com.android.test/.TestActivity0,com.android.test/.TestActivity1";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertThat(map.get("com.android.test")).containsExactly(".TestActivity0", ".TestActivity1");
    }

    @Test
    public void testParseConfigList_PackageAndActivity() {
        String config = "com.android.test/.TestActivity0,com.android.test";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertThat(map.get("com.android.test")).isEmpty();
    }

    @Test
    public void test_checkQueryPermission_noPermission() {
        mockQueryPermission(false);

        assertThat(mService.callerCanQueryPackage("blah")).isFalse();
    }

    @Test
    public void test_checkQueryPermission_correctPermission() {
        mockQueryPermission(true);

        assertThat(mService.callerCanQueryPackage("blah")).isTrue();
    }

    @Test
    public void test_checkQueryPermission_samePackage() {
        mockQueryPermission(false);

        assertThat(mService.callerCanQueryPackage(
                "com.android.car.carservice_unittest")).isTrue();
    }

    @Test
    public void testIsPendingIntentDistractionOptimised_withoutActivity() {
        when(mMockPendingIntent.isActivity()).thenReturn(false);

        assertThat(mService.isPendingIntentDistractionOptimized(mMockPendingIntent)).isFalse();
    }

    @Test
    public void testIsPendingIntentDistractionOptimised_noIntentComponents() {
        when(mMockPendingIntent.isActivity()).thenReturn(true);
        when(mMockPendingIntent.queryIntentComponents(MATCH_DEFAULT_ONLY)).thenReturn(
                new ArrayList<>());

        assertThat(mService.isPendingIntentDistractionOptimized(mMockPendingIntent)).isFalse();
    }

    @Test
    public void testGetTargetCarMajorVersion() {
        String pkgName = "bond.james.bond";
        int version = 0x07;
        mockGetTargetCarVersion(MANIFEST_METADATA_TARGET_CAR_MAJOR_VERSION, pkgName, version);
        mockCallingUser();

        assertWithMessage("getTargetCarMajorVersion(%s)", pkgName)
                .that(mService.getTargetCarMajorVersion(pkgName)).isEqualTo(version);
    }

    @Test
    public void testGetTargetCarMinorVersion() {
        String pkgName = "bond.james.bond";
        int version = 0x07;
        mockGetTargetCarVersion(MANIFEST_METADATA_TARGET_CAR_MINOR_VERSION, pkgName, version);
        mockCallingUser();

        assertWithMessage("getTargetCarMinorVersion(%s)", pkgName)
                .that(mService.getTargetCarMinorVersion(pkgName)).isEqualTo(version);
    }

    @Test
    public void testGetTargetCarVersion_null() {
        assertThrows(NullPointerException.class,
                () -> CarPackageManagerService.getTargetCarVersion(mUserContext, "anyAttribute",
                        null));
    }

    @Test
    public void testGetTargetCarVersion_noPermission() {
        mockQueryPermission(/* granted= */ false);

        assertThrows(SecurityException.class,
                () -> CarPackageManagerService.getTargetCarVersion(mUserContext, "anyAttribute",
                        "anyPackage"));
    }


    @Test
    public void testGetTargetCarVersion_noApp() throws Exception {
        mockQueryPermission(/* granted= */ true);
        mockGetApplicationInfoThrowsNotFound(mUserContext, "of.life");

        assertWithMessage("static getTargetCarVersion() call")
                .that(CarPackageManagerService.getTargetCarVersion(mUserContext,
                        "meaning", "of.life"))
                .isEqualTo(CAR_TARGET_VERSION_UNDEFINED);
    }

    @Test
    public void testGetTargetCarVersion_noMetadata() throws Exception {
        mockQueryPermission(/* granted= */ true);
        mockGetApplicationInfo(mUserContext, "of.life");

        assertWithMessage("static getTargetCarVersion() call")
                .that(CarPackageManagerService.getTargetCarVersion(mUserContext,
                        "meaning", "of.life"))
                .isEqualTo(0);
    }

    @Test
    public void testGetTargetCarVersion_ok() throws Exception {
        mockQueryPermission(/* granted= */ true);
        ApplicationInfo info = mockGetApplicationInfo(mUserContext, "of.life");
        info.metaData = new Bundle();
        info.metaData.putInt("meaning", 42);

        assertWithMessage("static getTargetCarVersion() call")
                .that(CarPackageManagerService.getTargetCarVersion(mUserContext,
                        "meaning", "of.life"))
                .isEqualTo(42);
    }

    private void mockQueryPermission(boolean granted) {
        int result = android.content.pm.PackageManager.PERMISSION_DENIED;
        if (granted) {
            result = android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        doReturn(result).when(() -> ActivityManagerHelper.checkComponentPermission(
                eq(QUERY_ALL_PACKAGES), anyInt(), anyInt(), anyBoolean()));
        when(mUserContext.checkCallingOrSelfPermission(QUERY_ALL_PACKAGES)).thenReturn(result);
    }

    private void mockGetTargetCarVersion(String attribute, String packageName, int version) {
        doReturn(version)
                .when(() -> CarPackageManagerService.getTargetCarVersion(mUserContext, attribute,
                        packageName));
    }

    private void mockCallingUser() {
        doReturn(mUserHandle).when(() -> Binder.getCallingUserHandle());
    }

    private static void mockGetApplicationInfoThrowsNotFound(Context context, String packageName)
            throws NameNotFoundException {
        PackageManager pm = mock(PackageManager.class);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getApplicationInfo(eq(packageName), any()))
                .thenThrow(new NameNotFoundException("D'OH!"));
    }

    private static ApplicationInfo mockGetApplicationInfo(Context context, String packageName)
            throws NameNotFoundException {
        ApplicationInfo info = new ApplicationInfo();
        PackageManager pm = mock(PackageManager.class);
        when(context.getPackageManager()).thenReturn(pm);
        when(pm.getApplicationInfo(eq(packageName), any())).thenReturn(info);
        return info;
    }
}
