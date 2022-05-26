/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.apitest;

import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.test.suitebuilder.annotation.MediumTest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Before;
import org.junit.Test;

@MediumTest
public class CarPackageManagerTest extends CarApiTestBase {

    private CarPackageManager mCarPackageManager;

    @Before
    public void setFixtures() {
        mCarPackageManager = (CarPackageManager) getCar().getCarManager(Car.PACKAGE_SERVICE);
    }

    @Test
    public void testCreate() throws Exception {
        assertThat(mCarPackageManager).isNotNull();
    }

    @Test
    public void testGetTargetCarMajorAndMinorVersion_notSet() throws Exception {
        String pkg = "com.android.car";

        assertWithMessage("getTargetCarMajorVersion(%s)", pkg)
                .that(mCarPackageManager.getTargetCarMajorVersion(pkg))
                .isEqualTo(CarPackageManager.CAR_TARGET_VERSION_UNDEFINED);
        assertWithMessage("getTargetCarMinorVersion(%s)", pkg)
                .that(mCarPackageManager.getTargetCarMinorVersion(pkg))
                .isEqualTo(CarPackageManager.CAR_TARGET_VERSION_UNDEFINED);
    }

    @Test
    public void testGetTargetCarMajorAndMinorVersion_set() throws Exception {
        String pkg = sContext.getPackageName();

        assertWithMessage("getTargetCarMajorVersion(%s)", pkg)
                .that(mCarPackageManager.getTargetCarMajorVersion(pkg))
                .isEqualTo(108);
        assertWithMessage("getTargetCarMinorVersion(%s)", pkg)
                .that(mCarPackageManager.getTargetCarMinorVersion(pkg))
                .isEqualTo(42);
    }
}
