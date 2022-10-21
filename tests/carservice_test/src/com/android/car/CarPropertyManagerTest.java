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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.car.Car;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarInternalErrorException;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.PropertyAccessDeniedSecurityException;
import android.car.hardware.property.PropertyNotAvailableAndRetryException;
import android.car.hardware.property.PropertyNotAvailableException;
import android.car.hardware.property.VehicleHalStatusCode;
import android.hardware.automotive.vehicle.RawPropValues;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleAreaSeat;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.hardware.automotive.vehicle.VehicleVendorPermission;
import android.os.Build;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.hal.test.AidlMockedVehicleHal.VehicleHalPropertyHandler;

import com.google.common.truth.Truth;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test for {@link android.car.hardware.property.CarPropertyManager}
 */
@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarPropertyManagerTest extends MockedCarTestBase {

    private static final String TAG = CarPropertyManagerTest.class.getSimpleName();

    /**
     * configArray[0], 1 indicates the property has a String value
     * configArray[1], 1 indicates the property has a Boolean value .
     * configArray[2], 1 indicates the property has an Integer value
     * configArray[3], the number indicates the size of Integer[]  in the property.
     * configArray[4], 1 indicates the property has a Long value .
     * configArray[5], the number indicates the size of Long[]  in the property.
     * configArray[6], 1 indicates the property has a Float value .
     * configArray[7], the number indicates the size of Float[] in the property.
     * configArray[8], the number indicates the size of byte[] in the property.
     */
    private static final java.util.Collection<Integer> CONFIG_ARRAY_1 =
            Arrays.asList(1, 0, 1, 0, 1, 0, 0, 0, 0);
    private static final java.util.Collection<Integer> CONFIG_ARRAY_2 =
            Arrays.asList(1, 1, 1, 0, 0, 0, 0, 2, 0);
    private static final java.util.Collection<Integer> CONFIG_ARRAY_3 =
            Arrays.asList(0, 1, 1, 0, 0, 0, 1, 0, 0);
    private static final Object[] EXPECTED_VALUE_1 = {"android", 1, 1L};
    private static final Object[] EXPECTED_VALUE_2 = {"android", true, 3, 1.1f, 2f};
    private static final Object[] EXPECTED_VALUE_3 = {true, 1, 2.2f};

    private static final int CUSTOM_SEAT_INT_PROP_1 =
            0x1201 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.SEAT;
    private static final int CUSTOM_SEAT_INT_PROP_2 =
            0x1202 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.SEAT;

    private static final int CUSTOM_SEAT_MIXED_PROP_ID_1 =
            0x1101 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.MIXED | VehicleArea.SEAT;
    private static final int CUSTOM_GLOBAL_MIXED_PROP_ID_2 =
            0x1102 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.MIXED | VehicleArea.GLOBAL;
    private static final int CUSTOM_GLOBAL_MIXED_PROP_ID_3 =
            0x1110 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.MIXED | VehicleArea.GLOBAL;

    private static final int CUSTOM_GLOBAL_INT_ARRAY_PROP =
            0x1103 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32_VEC
                    | VehicleArea.GLOBAL;
    private static final Integer[] FAKE_INT_ARRAY_VALUE = {1, 2};

    private static final int INT_ARRAY_PROP_STATUS_ERROR =
            0x1104 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32_VEC
                    | VehicleArea.GLOBAL;

    private static final int BOOLEAN_PROP_STATUS_ERROR =
            0x1105 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.BOOLEAN
                    | VehicleArea.GLOBAL;
    private static final boolean FAKE_BOOLEAN_PROPERTY_VALUE = true;
    private static final int FLOAT_PROP_STATUS_UNAVAILABLE =
            0x1106 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.FLOAT
                    | VehicleArea.GLOBAL;
    private static final float FAKE_FLOAT_PROPERTY_VALUE = 3f;
    private static final int INT_PROP_STATUS_UNAVAILABLE =
            0x1107 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32
                    | VehicleArea.GLOBAL;
    private static final int FAKE_INT_PROPERTY_VALUE = 3;
    // A property that always returns null to simulate an unavailable property.
    private static final int NULL_VALUE_PROP =
            0x1108 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32
                    | VehicleArea.GLOBAL;

    // Vendor properties for testing exceptions.
    private static final int PROP_CAUSE_STATUS_CODE_TRY_AGAIN =
            0x1201 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_INVALID_ARG =
            0x1202 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE =
            0x1203 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR =
            0x1204 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_CAUSE_STATUS_CODE_ACCESS_DENIED =
            0x1205 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;

    // Vendor properties for testing permissions
    private static final int PROP_WITH_READ_ONLY_PERMISSION =
            0x1301 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int PROP_WITH_WRITE_ONLY_PERMISSION =
            0x1302 | VehiclePropertyGroup.VENDOR | VehiclePropertyType.INT32 | VehicleArea.GLOBAL;
    private static final int SUPPORT_CUSTOM_PERMISSION = 287313669;
    private static final java.util.Collection<Integer> VENDOR_PERMISSION_CONFIG =
            Collections.unmodifiableList(
                    Arrays.asList(PROP_WITH_READ_ONLY_PERMISSION,
                            VehicleVendorPermission.PERMISSION_GET_VENDOR_CATEGORY_1,
                            VehicleVendorPermission.PERMISSION_NOT_ACCESSIBLE,
                            PROP_WITH_WRITE_ONLY_PERMISSION,
                            VehicleVendorPermission.PERMISSION_NOT_ACCESSIBLE,
                            VehicleVendorPermission.PERMISSION_SET_VENDOR_CATEGORY_1));


    // Use FAKE_PROPERTY_ID to test api return null or throw exception.
    private static final int FAKE_PROPERTY_ID = 0x111;

    private static final int DRIVER_SIDE_AREA_ID = VehicleAreaSeat.ROW_1_LEFT
            | VehicleAreaSeat.ROW_2_LEFT;
    private static final int PASSENGER_SIDE_AREA_ID = VehicleAreaSeat.ROW_1_RIGHT
            | VehicleAreaSeat.ROW_2_CENTER
            | VehicleAreaSeat.ROW_2_RIGHT;
    private static final float INIT_TEMP_VALUE = 16f;
    private static final float CHANGED_TEMP_VALUE = 20f;
    private static final int CALLBACK_SHORT_TIMEOUT_MS = 350; // ms
    // Wait for CarPropertyManager register/unregister listener
    private static final long WAIT_FOR_NO_EVENTS = 50;

    private static final List<Integer> USER_HAL_PROPERTIES = Arrays.asList(
            VehiclePropertyIds.INITIAL_USER_INFO,
            VehiclePropertyIds.SWITCH_USER,
            VehiclePropertyIds.CREATE_USER,
            VehiclePropertyIds.REMOVE_USER,
            VehiclePropertyIds.USER_IDENTIFICATION_ASSOCIATION
    );

    private CarPropertyManager mManager;

    @Rule
    public TestName mTestName = new TestName();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setUpTargetSdk();
        mManager = (CarPropertyManager) getCar().getCarManager(Car.PROPERTY_SERVICE);
        assertThat(mManager).isNotNull();
    }

    private void setUpTargetSdk() {
        if (mTestName.getMethodName().endsWith("InQ")) {
            getContext().getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.Q;
        } else if (mTestName.getMethodName().endsWith("AfterQ")) {
            getContext().getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.R;
        } else if (mTestName.getMethodName().endsWith("AfterR")) {
            getContext().getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.S;
        }
    }

    @Test
    public void testMixedPropertyConfigs() {
        List<CarPropertyConfig> configs = mManager.getPropertyList();
        for (CarPropertyConfig cfg : configs) {
            switch (cfg.getPropertyId()) {
                case CUSTOM_SEAT_MIXED_PROP_ID_1:
                    assertThat(cfg.getConfigArray()).containsExactlyElementsIn(CONFIG_ARRAY_1)
                            .inOrder();
                    break;
                case CUSTOM_GLOBAL_MIXED_PROP_ID_2:
                    assertThat(cfg.getConfigArray()).containsExactlyElementsIn(CONFIG_ARRAY_2)
                            .inOrder();
                    break;
                case CUSTOM_GLOBAL_MIXED_PROP_ID_3:
                    assertThat(cfg.getConfigArray()).containsExactlyElementsIn(CONFIG_ARRAY_3)
                            .inOrder();
                    break;
                case VehiclePropertyIds.HVAC_TEMPERATURE_SET:
                case PROP_CAUSE_STATUS_CODE_ACCESS_DENIED:
                case PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR:
                case PROP_CAUSE_STATUS_CODE_TRY_AGAIN:
                case PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE:
                case PROP_CAUSE_STATUS_CODE_INVALID_ARG:
                case CUSTOM_SEAT_INT_PROP_1:
                case CUSTOM_SEAT_INT_PROP_2:
                case CUSTOM_GLOBAL_INT_ARRAY_PROP:
                case INT_ARRAY_PROP_STATUS_ERROR:
                case BOOLEAN_PROP_STATUS_ERROR:
                case INT_PROP_STATUS_UNAVAILABLE:
                case FLOAT_PROP_STATUS_UNAVAILABLE:
                case VehiclePropertyIds.INFO_VIN:
                case NULL_VALUE_PROP:
                case SUPPORT_CUSTOM_PERMISSION:
                case PROP_WITH_READ_ONLY_PERMISSION:
                case PROP_WITH_WRITE_ONLY_PERMISSION:
                case VehiclePropertyIds.TIRE_PRESSURE:
                    break;
                default:
                    Assert.fail("Unexpected CarPropertyConfig: " + cfg);
            }
        }
    }

    @Test
    public void testGetMixTypeProperty() {
        mManager.setProperty(Object[].class, CUSTOM_SEAT_MIXED_PROP_ID_1,
                DRIVER_SIDE_AREA_ID, EXPECTED_VALUE_1);
        CarPropertyValue<Object[]> result = mManager.getProperty(
                CUSTOM_SEAT_MIXED_PROP_ID_1, DRIVER_SIDE_AREA_ID);
        assertThat(result.getValue()).isEqualTo(EXPECTED_VALUE_1);

        mManager.setProperty(Object[].class, CUSTOM_GLOBAL_MIXED_PROP_ID_2,
                0, EXPECTED_VALUE_2);
        result = mManager.getProperty(
                CUSTOM_GLOBAL_MIXED_PROP_ID_2, 0);
        assertThat(result.getValue()).isEqualTo(EXPECTED_VALUE_2);

        mManager.setProperty(Object[].class, CUSTOM_GLOBAL_MIXED_PROP_ID_3,
                0, EXPECTED_VALUE_3);
        result = mManager.getProperty(
                CUSTOM_GLOBAL_MIXED_PROP_ID_3, 0);
        assertThat(result.getValue()).isEqualTo(EXPECTED_VALUE_3);
    }

    /**
     * Test {@link android.car.hardware.property.CarPropertyManager#getIntArrayProperty(int, int)}
     */
    @Test
    public void testGetIntArrayProperty() {
        mManager.setProperty(Integer[].class, CUSTOM_GLOBAL_INT_ARRAY_PROP, 0,
                FAKE_INT_ARRAY_VALUE);

        int[] result = mManager.getIntArrayProperty(CUSTOM_GLOBAL_INT_ARRAY_PROP, 0);
        assertThat(result).asList().containsExactlyElementsIn(FAKE_INT_ARRAY_VALUE);
    }

    /**
     * Test {@link CarPropertyManager#getIntArrayProperty(int, int)} when vhal returns a value with
     * error status.
     */
    @Test
    public void testGetIntArrayPropertyWithErrorStatusAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isGreaterThan(Build.VERSION_CODES.R);
        mManager.setProperty(Integer[].class, INT_ARRAY_PROP_STATUS_ERROR,
                0, FAKE_INT_ARRAY_VALUE);
        assertThrows(CarInternalErrorException.class,
                () -> mManager.getIntArrayProperty(INT_ARRAY_PROP_STATUS_ERROR,
                        0));
    }

    /**
     * Test {@link CarPropertyManager#getIntProperty(int, int)} when vhal returns a value with
     * unavailable status.
     */
    @Test
    public void testGetIntPropertyWithUnavailableStatusAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isGreaterThan(Build.VERSION_CODES.R);
        mManager.setProperty(Integer.class, INT_PROP_STATUS_UNAVAILABLE,
                0, FAKE_INT_PROPERTY_VALUE);
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getIntProperty(INT_PROP_STATUS_UNAVAILABLE, 0));

    }

    /**
     * Test {@link CarPropertyManager#getBooleanProperty(int, int)} when vhal returns a value with
     * error status.
     */
    @Test
    public void testGetBooleanPropertyWithErrorStatusAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isGreaterThan(Build.VERSION_CODES.R);
        mManager.setProperty(Boolean.class, BOOLEAN_PROP_STATUS_ERROR,
                0, FAKE_BOOLEAN_PROPERTY_VALUE);
        assertThrows(CarInternalErrorException.class,
                () -> mManager.getBooleanProperty(BOOLEAN_PROP_STATUS_ERROR, 0));
    }

    /**
     * Test {@link CarPropertyManager#getFloatProperty(int, int)} when vhal returns a value with
     * unavailable status.
     */
    @Test
    public void testGetFloatPropertyWithUnavailableStatusAfterR() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isGreaterThan(Build.VERSION_CODES.R);
        mManager.setProperty(Float.class, FLOAT_PROP_STATUS_UNAVAILABLE,
                0, FAKE_FLOAT_PROPERTY_VALUE);
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getFloatProperty(FLOAT_PROP_STATUS_UNAVAILABLE, 0));
    }

    /**
     * Test {@link CarPropertyManager#getProperty(Class, int, int)}
     */
    @Test
    public void testGetPropertyWithClass() {
        mManager.setProperty(Integer[].class, CUSTOM_GLOBAL_INT_ARRAY_PROP, 0,
                FAKE_INT_ARRAY_VALUE);

        CarPropertyValue<Integer[]> result = mManager.getProperty(Integer[].class,
                CUSTOM_GLOBAL_INT_ARRAY_PROP, 0);
        assertThat(result.getValue()).asList().containsExactlyElementsIn(FAKE_INT_ARRAY_VALUE);
    }

    /**
     * Test {@link CarPropertyManager#isPropertyAvailable(int, int)}
     */
    @Test
    public void testIsPropertyAvailable() {
        assertThat(mManager.isPropertyAvailable(CUSTOM_GLOBAL_INT_ARRAY_PROP, 0))
                .isTrue();
    }

    /**
     * Test {@link CarPropertyManager#getWritePermission(int)}
     * and {@link CarPropertyManager#getWritePermission(int)}
     */
    @Test
    public void testGetPermission() {
        String hvacReadPermission = mManager.getReadPermission(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        assertThat(hvacReadPermission).isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);
        String hvacWritePermission = mManager.getWritePermission(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        assertThat(hvacWritePermission).isEqualTo(Car.PERMISSION_CONTROL_CAR_CLIMATE);

        // For read-only property
        String vinReadPermission = mManager.getReadPermission(VehiclePropertyIds.INFO_VIN);
        assertThat(vinReadPermission).isEqualTo(Car.PERMISSION_IDENTIFICATION);
        String vinWritePermission = mManager.getWritePermission(VehiclePropertyIds.INFO_VIN);
        assertThat(vinWritePermission).isNull();
    }

    @Test
    public void testGetPropertyConfig() {
        CarPropertyConfig<?> config = mManager.getCarPropertyConfig(CUSTOM_SEAT_MIXED_PROP_ID_1);
        assertThat(config.getPropertyId()).isEqualTo(CUSTOM_SEAT_MIXED_PROP_ID_1);
        // returns null if it cannot find the propertyConfig for the property.
        assertThat(mManager.getCarPropertyConfig(FAKE_PROPERTY_ID)).isNull();
    }

    @Test
    public void testGetPropertyConfig_withReadOnlyPermission() {
        CarPropertyConfig<?> configForReadOnlyProperty = mManager
                .getCarPropertyConfig(PROP_WITH_READ_ONLY_PERMISSION);

        assertThat(configForReadOnlyProperty).isNotNull();
        assertThat(configForReadOnlyProperty.getPropertyId())
                .isEqualTo(PROP_WITH_READ_ONLY_PERMISSION);
    }

    @Test
    public void testGetPropertyConfig_withWriteOnlyPermission() {
        CarPropertyConfig<?> configForWriteOnlyProperty = mManager
                .getCarPropertyConfig(PROP_WITH_WRITE_ONLY_PERMISSION);

        assertThat(configForWriteOnlyProperty).isNotNull();
        assertThat(configForWriteOnlyProperty.getPropertyId())
                .isEqualTo(PROP_WITH_WRITE_ONLY_PERMISSION);
    }

    @Test
    public void testGetAreaId() {
        int result = mManager.getAreaId(CUSTOM_SEAT_MIXED_PROP_ID_1, VehicleAreaSeat.ROW_1_LEFT);
        assertThat(result).isEqualTo(DRIVER_SIDE_AREA_ID);
        //test for the GLOBAL property
        int globalAreaId =
                mManager.getAreaId(CUSTOM_GLOBAL_MIXED_PROP_ID_2, VehicleAreaSeat.ROW_1_LEFT);
        assertThat(globalAreaId).isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        //test exception
        assertThrows(IllegalArgumentException.class, () -> mManager.getAreaId(
                CUSTOM_SEAT_MIXED_PROP_ID_1, VehicleAreaSeat.ROW_3_CENTER));
        assertThrows(IllegalArgumentException.class, () -> mManager.getAreaId(FAKE_PROPERTY_ID,
                VehicleAreaSeat.ROW_1_LEFT));
    }

    @Test
    public void testRegisterPropertyUnavailable() {
        TestSequenceCallback callback = new TestSequenceCallback(1);
        // Registering a property which has an unavailable initial value
        // won't throw ServiceSpecificException.
        mManager.registerCallback(callback, PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        // initial value is unavailable, should not get any callback.
        assertThrows(IllegalStateException.class, callback::assertOnChangeEventCalled);
    }

    @Test
    public void testNotReceiveOnErrorEvent() throws Exception {
        TestErrorCallback callback = new TestErrorCallback();
        mManager.registerCallback(callback, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        callback.assertRegisterCompleted();
        injectErrorEvent(VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        // app never change the value of HVAC_TEMPERATURE_SET, it won't get an error code.
        callback.assertOnErrorEventNotCalled();
    }

    @Test
    public void testReceiveOnErrorEvent() throws Exception {
        TestErrorCallback callback = new TestErrorCallback();
        mManager.registerCallback(callback, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        callback.assertRegisterCompleted();
        mManager.setFloatProperty(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                CHANGED_TEMP_VALUE);
        injectErrorEvent(VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        callback.assertOnErrorEventCalled();
        assertThat(callback.mReceivedErrorEventWithErrorCode).isTrue();
        assertThat(callback.mErrorCode).isEqualTo(
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        assertThat(callback.mReceivedErrorEventWithOutErrorCode).isFalse();
    }

    @Test
    public void testNotReceiveOnErrorEventAfterUnregister() throws Exception {
        TestErrorCallback callback1 = new TestErrorCallback();
        mManager.registerCallback(callback1, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        callback1.assertRegisterCompleted();
        TestErrorCallback callback2 = new TestErrorCallback();
        mManager.registerCallback(callback2, VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
        mManager.setFloatProperty(
                VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                CHANGED_TEMP_VALUE);
        mManager.unregisterCallback(callback1, VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        SystemClock.sleep(WAIT_FOR_NO_EVENTS);
        injectErrorEvent(VehiclePropertyIds.HVAC_TEMPERATURE_SET, PASSENGER_SIDE_AREA_ID,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        // callback1 is unregistered
        callback1.assertOnErrorEventNotCalled();
        callback2.assertOnErrorEventCalled();
    }

    @Test
    public void testSetterExceptionsInQ() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isEqualTo(Build.VERSION_CODES.Q);

        assertThrows(IllegalStateException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalStateException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalStateException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(RuntimeException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
    }

    @Test
    public void testSetterExceptionsAfterQ() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isGreaterThan(Build.VERSION_CODES.Q);

        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(CarInternalErrorException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.setProperty(Integer.class, PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL, 1));
    }

    @Test
    public void testGetterExceptionsInQ() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isEqualTo(Build.VERSION_CODES.Q);

        assertThrows(IllegalStateException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(IllegalStateException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(IllegalArgumentException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(IllegalStateException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(IllegalStateException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(IllegalStateException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(IllegalStateException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(IllegalStateException.class,
                () -> mManager.getProperty(NULL_VALUE_PROP,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        Truth.assertThat(mManager.getProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)).isNull();

    }

    @Test
    public void testGetterExceptionsAfterQ() {
        Truth.assertThat(getContext().getApplicationInfo().targetSdkVersion)
                .isAtLeast(Build.VERSION_CODES.R);

        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(PropertyAccessDeniedSecurityException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(IllegalArgumentException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(PropertyNotAvailableAndRetryException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(CarInternalErrorException.class,
                () -> mManager.getProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
        assertThrows(CarInternalErrorException.class,
                () -> mManager.getIntProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        assertThrows(PropertyNotAvailableException.class,
                () -> mManager.getProperty(NULL_VALUE_PROP,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));
    }

    @Test
    public void testOnChangeEventWithSameAreaId() throws Exception {
        // init
        mManager.setProperty(Integer.class,
                CUSTOM_SEAT_INT_PROP_1, DRIVER_SIDE_AREA_ID, 1);
        TestSequenceCallback callback = new TestSequenceCallback(1);
        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_1, 0);
        callback.assertRegisterCompleted();

        VehiclePropValue firstFakeValueDriveSide = new VehiclePropValue();
        firstFakeValueDriveSide.prop = CUSTOM_SEAT_INT_PROP_1;
        firstFakeValueDriveSide.areaId = DRIVER_SIDE_AREA_ID;
        firstFakeValueDriveSide.value = new RawPropValues();
        firstFakeValueDriveSide.value.int32Values = new int[]{2};
        firstFakeValueDriveSide.timestamp = SystemClock.elapsedRealtimeNanos();
        VehiclePropValue secFakeValueDriveSide = new VehiclePropValue();
        secFakeValueDriveSide.prop = CUSTOM_SEAT_INT_PROP_1;
        secFakeValueDriveSide.areaId = DRIVER_SIDE_AREA_ID;
        secFakeValueDriveSide.value = new RawPropValues();
        secFakeValueDriveSide.value.int32Values = new int[]{3}; // 0 in HAL indicate false;
        secFakeValueDriveSide.timestamp = SystemClock.elapsedRealtimeNanos();
        // inject the new event first
        getAidlMockedVehicleHal().injectEvent(secFakeValueDriveSide);
        // inject the old event
        getAidlMockedVehicleHal().injectEvent(firstFakeValueDriveSide);
        callback.assertOnChangeEventCalled();
        // Client should only get the new event
        assertThat((int) callback.getLastCarPropertyValue(CUSTOM_SEAT_INT_PROP_1).getValue())
                .isEqualTo(3);
        assertThat(callback.getEventCounter()).isEqualTo(1);

    }

    @Test
    public void testOnChangeEventWithDifferentAreaId() throws Exception {
        // init
        mManager.setProperty(Integer.class,
                CUSTOM_SEAT_INT_PROP_2, DRIVER_SIDE_AREA_ID, 1);
        TestSequenceCallback callback = new TestSequenceCallback(2);
        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_2, 0);
        callback.assertRegisterCompleted();
        VehiclePropValue fakeValueDriveSide = new VehiclePropValue();
        fakeValueDriveSide.prop = CUSTOM_SEAT_INT_PROP_2;
        fakeValueDriveSide.areaId = DRIVER_SIDE_AREA_ID;
        fakeValueDriveSide.value = new RawPropValues();
        fakeValueDriveSide.value.int32Values = new int[]{4};
        fakeValueDriveSide.timestamp = SystemClock.elapsedRealtimeNanos();

        VehiclePropValue fakeValuePsgSide = new VehiclePropValue();
        fakeValuePsgSide.prop = CUSTOM_SEAT_INT_PROP_2;
        fakeValuePsgSide.areaId = PASSENGER_SIDE_AREA_ID;
        fakeValuePsgSide.value = new RawPropValues();
        fakeValuePsgSide.value.int32Values = new int[]{5};
        fakeValuePsgSide.timestamp = SystemClock.elapsedRealtimeNanos();

        // inject passenger event before driver event
        getAidlMockedVehicleHal().injectEvent(fakeValuePsgSide);
        getAidlMockedVehicleHal().injectEvent(fakeValueDriveSide);
        callback.assertOnChangeEventCalled();

        // both events should be received by listener
        assertThat((int) callback.getLastCarPropertyValue(CUSTOM_SEAT_INT_PROP_2).getValue())
                .isEqualTo(4);
        assertThat(callback.getEventCounter()).isEqualTo(2);
    }

    @Test
    public void testOnChangeEventInvalidPayload() throws Exception {
        // init
        mManager.setProperty(Integer.class, CUSTOM_SEAT_INT_PROP_1, DRIVER_SIDE_AREA_ID, 1);
        TestSequenceCallback callback = new TestSequenceCallback(0);
        mManager.registerCallback(callback, CUSTOM_SEAT_INT_PROP_1, 0);
        callback.assertRegisterCompleted();

        List<VehiclePropValue> props = new ArrayList<>();
        VehiclePropValue emptyProp = new VehiclePropValue();
        emptyProp.prop = CUSTOM_SEAT_INT_PROP_1;
        props.add(emptyProp);

        VehiclePropValue twoIntsProp = new VehiclePropValue();
        twoIntsProp.prop = CUSTOM_SEAT_INT_PROP_1;
        twoIntsProp.value = new RawPropValues();
        twoIntsProp.value.int32Values = new int[]{0, 1};
        props.add(twoIntsProp);

        VehiclePropValue propWithFloat = new VehiclePropValue();
        propWithFloat.prop = CUSTOM_SEAT_INT_PROP_1;
        propWithFloat.value = new RawPropValues();
        propWithFloat.value.floatValues = new float[]{0f};
        props.add(propWithFloat);

        VehiclePropValue propWithString = new VehiclePropValue();
        propWithString.prop = CUSTOM_SEAT_INT_PROP_1;
        propWithString.value = new RawPropValues();
        propWithString.value.stringValue = "1234";
        props.add(propWithString);

        for (VehiclePropValue prop : props) {
            // inject passenger event before driver event
            getAidlMockedVehicleHal().injectEvent(prop);
            assertThat(callback.getEventCounter()).isEqualTo(0);
        }
    }

    @Test
    public void registerCallback_handlesContinuousPropertyUpdateRate() {
        float wheelLeftFrontValue = 11.11f;
        long wheelLeftFrontTimestampNanos = Duration.ofSeconds(1).toNanos();

        float notNewEnoughWheelLeftFrontValue = 22.22f;
        long notNewEnoughWheelLeftFrontTimestampNanos = Duration.ofMillis(1999).toNanos();

        float newEnoughWheelLeftFrontValue = 33.33f;
        long newEnoughWheelLeftFrontTimestampNanos = Duration.ofSeconds(2).toNanos();

        TestCallback testCallback = new TestCallback(2);
        assertThat(mManager.registerCallback(testCallback, VehiclePropertyIds.TIRE_PRESSURE,
                1f)).isTrue();

        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        wheelLeftFrontValue, wheelLeftFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        notNewEnoughWheelLeftFrontValue, notNewEnoughWheelLeftFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        newEnoughWheelLeftFrontValue, newEnoughWheelLeftFrontTimestampNanos));

        List<CarPropertyValue<?>> carPropertyValues = testCallback.getCarPropertyValues();
        assertThat(carPropertyValues).hasSize(2);

        assertTirePressureCarPropertyValue(carPropertyValues.get(0),
                VehicleAreaWheel.WHEEL_LEFT_FRONT, wheelLeftFrontValue,
                wheelLeftFrontTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(1),
                VehicleAreaWheel.WHEEL_LEFT_FRONT, newEnoughWheelLeftFrontValue,
                newEnoughWheelLeftFrontTimestampNanos);
    }

    @Test
    public void registerCallback_handlesOutOfTimeOrderEventsWithDifferentAreaIds() {
        float wheelLeftFrontValue = 11.11f;
        long wheelLeftFrontTimestampNanos = Duration.ofSeconds(4).toNanos();

        float wheelRightFrontValue = 22.22f;
        long wheelRightFrontTimestampNanos = Duration.ofSeconds(3).toNanos();

        float wheelLeftRearValue = 33.33f;
        long wheelLeftRearTimestampNanos = Duration.ofSeconds(2).toNanos();

        float wheelRightRearValue = 44.44f;
        long wheelRightRearTimestampNanos = Duration.ofSeconds(1).toNanos();

        TestCallback testCallback = new TestCallback(4);
        assertThat(mManager.registerCallback(testCallback, VehiclePropertyIds.TIRE_PRESSURE,
                1f)).isTrue();

        // inject events in time order from newest to oldest
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_FRONT,
                        wheelLeftFrontValue, wheelLeftFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_RIGHT_FRONT,
                        wheelRightFrontValue, wheelRightFrontTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_LEFT_REAR,
                        wheelLeftRearValue, wheelLeftRearTimestampNanos));
        getAidlMockedVehicleHal().injectEvent(
                newTirePressureVehiclePropValue(VehicleAreaWheel.WHEEL_RIGHT_REAR,
                        wheelRightRearValue, wheelRightRearTimestampNanos));

        List<CarPropertyValue<?>> carPropertyValues = testCallback.getCarPropertyValues();
        assertThat(carPropertyValues).hasSize(4);

        assertTirePressureCarPropertyValue(carPropertyValues.get(0),
                VehicleAreaWheel.WHEEL_LEFT_FRONT, wheelLeftFrontValue,
                wheelLeftFrontTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(1),
                VehicleAreaWheel.WHEEL_RIGHT_FRONT, wheelRightFrontValue,
                wheelRightFrontTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(2),
                VehicleAreaWheel.WHEEL_LEFT_REAR, wheelLeftRearValue, wheelLeftRearTimestampNanos);

        assertTirePressureCarPropertyValue(carPropertyValues.get(3),
                VehicleAreaWheel.WHEEL_RIGHT_REAR, wheelRightRearValue,
                wheelRightRearTimestampNanos);
    }

    @Override
    protected void configureMockedHal() {
        PropertyHandler handler = new PropertyHandler();
        addAidlProperty(CUSTOM_SEAT_MIXED_PROP_ID_1, handler).setConfigArray(CONFIG_ARRAY_1)
                .addAreaConfig(DRIVER_SIDE_AREA_ID).addAreaConfig(PASSENGER_SIDE_AREA_ID);
        addAidlProperty(CUSTOM_GLOBAL_MIXED_PROP_ID_2, handler).setConfigArray(CONFIG_ARRAY_2);
        addAidlProperty(CUSTOM_GLOBAL_MIXED_PROP_ID_3, handler).setConfigArray(CONFIG_ARRAY_3);
        addAidlProperty(CUSTOM_GLOBAL_INT_ARRAY_PROP, handler);

        addAidlProperty(INT_ARRAY_PROP_STATUS_ERROR, handler);
        addAidlProperty(INT_PROP_STATUS_UNAVAILABLE, handler);
        addAidlProperty(FLOAT_PROP_STATUS_UNAVAILABLE, handler);
        addAidlProperty(BOOLEAN_PROP_STATUS_ERROR, handler);
        addAidlProperty(VehiclePropertyIds.TIRE_PRESSURE, handler).addAreaConfig(
                VehicleAreaWheel.WHEEL_LEFT_REAR).addAreaConfig(
                VehicleAreaWheel.WHEEL_RIGHT_REAR).addAreaConfig(
                VehicleAreaWheel.WHEEL_RIGHT_FRONT).addAreaConfig(
                VehicleAreaWheel.WHEEL_LEFT_FRONT).setChangeMode(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS).setMaxSampleRate(
                10).setMinSampleRate(1);

        VehiclePropValue tempValue = new VehiclePropValue();
        tempValue.value = new RawPropValues();
        tempValue.value.floatValues = new float[]{INIT_TEMP_VALUE};
        tempValue.prop = VehiclePropertyIds.HVAC_TEMPERATURE_SET;
        addAidlProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET, tempValue)
                .addAreaConfig(DRIVER_SIDE_AREA_ID).addAreaConfig(PASSENGER_SIDE_AREA_ID);
        addAidlProperty(VehiclePropertyIds.INFO_VIN);

        addAidlProperty(PROP_CAUSE_STATUS_CODE_ACCESS_DENIED, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_TRY_AGAIN, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_INVALID_ARG, handler);
        addAidlProperty(PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE, handler);

        addAidlProperty(CUSTOM_SEAT_INT_PROP_1, handler).addAreaConfig(DRIVER_SIDE_AREA_ID)
                .addAreaConfig(PASSENGER_SIDE_AREA_ID);
        addAidlProperty(CUSTOM_SEAT_INT_PROP_2, handler).addAreaConfig(DRIVER_SIDE_AREA_ID)
                .addAreaConfig(PASSENGER_SIDE_AREA_ID);

        addAidlProperty(NULL_VALUE_PROP, handler);

        // Add properties for permission testing.
        addAidlProperty(SUPPORT_CUSTOM_PERMISSION, handler).setConfigArray(
                VENDOR_PERMISSION_CONFIG);
        addAidlProperty(PROP_WITH_READ_ONLY_PERMISSION, handler);
        addAidlProperty(PROP_WITH_WRITE_ONLY_PERMISSION, handler);
    }

    private static class PropertyHandler implements VehicleHalPropertyHandler {
        HashMap<Integer, VehiclePropValue> mMap = new HashMap<>();

        @Override
        public synchronized void onPropertySet(VehiclePropValue value) {
            // Simulate VehicleHal.set() behavior.
            int statusCode = mapPropertyToVhalStatusCode(value.prop);
            if (statusCode != VehicleHalStatusCode.STATUS_OK) {
                // The ServiceSpecificException here would pass the statusCode back to caller.
                throw new ServiceSpecificException(statusCode);
            }

            mMap.put(value.prop, value);
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            // Simulate VehicleHal.get() behavior.
            int vhalStatusCode = mapPropertyToVhalStatusCode(value.prop);
            if (vhalStatusCode != VehicleHalStatusCode.STATUS_OK) {
                // The ServiceSpecificException here would pass the statusCode back to caller.
                throw new ServiceSpecificException(vhalStatusCode);
            }

            int propertyStatus = mapPropertyToCarPropertyStatusCode(value.prop);
            if (value.prop == NULL_VALUE_PROP) {
                // Return null to simulate an unavailable property.
                // HAL implementation should return STATUS_TRY_AGAIN when a property is unavailable,
                // however, it may also return null with STATUS_OKAY, and we want to handle this
                // properly.
                return null;
            }
            VehiclePropValue currentValue = mMap.get(value.prop);
            if (currentValue == null) {
                return value;
            } else {
                currentValue.status = propertyStatus;
            }
            return currentValue;
        }

        @Override
        public synchronized void onPropertySubscribe(int property, float sampleRate) {
            Log.d(TAG, "onPropertySubscribe property "
                    + property + " sampleRate " + sampleRate);
        }

        @Override
        public synchronized void onPropertyUnsubscribe(int property) {
            Log.d(TAG, "onPropertyUnSubscribe property " + property);
        }
    }

    private static String propToString(int propertyId) {
        return VehiclePropertyIds.toString(propertyId) + " (" + propertyId + ")";
    }

    private static int mapPropertyToVhalStatusCode(int propId) {
        switch (propId) {
            case PROP_CAUSE_STATUS_CODE_TRY_AGAIN:
                return VehicleHalStatusCode.STATUS_TRY_AGAIN;
            case PROP_CAUSE_STATUS_CODE_NOT_AVAILABLE:
                return VehicleHalStatusCode.STATUS_NOT_AVAILABLE;
            case PROP_CAUSE_STATUS_CODE_ACCESS_DENIED:
                return VehicleHalStatusCode.STATUS_ACCESS_DENIED;
            case PROP_CAUSE_STATUS_CODE_INVALID_ARG:
                return VehicleHalStatusCode.STATUS_INVALID_ARG;
            case PROP_CAUSE_STATUS_CODE_INTERNAL_ERROR:
                return VehicleHalStatusCode.STATUS_INTERNAL_ERROR;
            default:
                return VehicleHalStatusCode.STATUS_OK;
        }
    }

    private static int mapPropertyToCarPropertyStatusCode(int propId) {
        switch (propId) {
            case INT_ARRAY_PROP_STATUS_ERROR:
            case BOOLEAN_PROP_STATUS_ERROR:
                return CarPropertyValue.STATUS_ERROR;
            case INT_PROP_STATUS_UNAVAILABLE:
            case FLOAT_PROP_STATUS_UNAVAILABLE:
                return CarPropertyValue.STATUS_UNAVAILABLE;
            default:
                return CarPropertyValue.STATUS_AVAILABLE;
        }
    }

    private static VehiclePropValue newTirePressureVehiclePropValue(int areaId, float floatValue,
            long timestampNanos) {
        VehiclePropValue vehiclePropValue = new VehiclePropValue();
        vehiclePropValue.prop = VehiclePropertyIds.TIRE_PRESSURE;
        vehiclePropValue.areaId = areaId;
        vehiclePropValue.value = new RawPropValues();
        vehiclePropValue.value.floatValues = new float[]{floatValue};
        vehiclePropValue.timestamp = timestampNanos;
        return vehiclePropValue;
    }

    private static void assertTirePressureCarPropertyValue(CarPropertyValue<?> carPropertyValue,
            int areaId, float floatValue, long timestampNanos) {
        assertThat(carPropertyValue.getPropertyId()).isEqualTo(VehiclePropertyIds.TIRE_PRESSURE);
        assertThat(carPropertyValue.getAreaId()).isEqualTo(areaId);
        assertThat(carPropertyValue.getStatus()).isEqualTo(CarPropertyValue.STATUS_AVAILABLE);
        assertThat(carPropertyValue.getTimestamp()).isEqualTo(timestampNanos);
        assertThat(carPropertyValue.getValue()).isEqualTo(floatValue);
    }

    private static class TestErrorCallback implements CarPropertyManager.CarPropertyEventCallback {

        private static final String CALLBACK_TAG = "ErrorEventTest";
        private boolean mReceivedErrorEventWithErrorCode = false;
        private boolean mReceivedErrorEventWithOutErrorCode = false;
        private int mErrorCode;
        private final CountDownLatch mEventsCountDownLatch = new CountDownLatch(1);
        private final CountDownLatch mRegisterCountDownLatch = new CountDownLatch(2);

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            Log.d(CALLBACK_TAG, "onChangeEvent: " + value);
            mRegisterCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            mReceivedErrorEventWithOutErrorCode = true;
            Log.d(CALLBACK_TAG, "onErrorEvent, propId: " + propId + " zone: " + zone);
            mEventsCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int areaId, int errorCode) {
            mReceivedErrorEventWithErrorCode = true;
            mErrorCode = errorCode;
            Log.d(CALLBACK_TAG, "onErrorEvent, propId: " + propId + " areaId: " + areaId
                    + "errorCode: " + errorCode);
            mEventsCountDownLatch.countDown();
        }

        public void assertOnErrorEventCalled() throws InterruptedException {
            if (!mEventsCountDownLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Callback is not called in "
                        + CALLBACK_SHORT_TIMEOUT_MS + " ms.");
            }
        }

        public void assertOnErrorEventNotCalled() throws InterruptedException {
            if (mEventsCountDownLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Callback is called in " + CALLBACK_SHORT_TIMEOUT_MS
                        + " ms.");
            }
        }

        public void assertRegisterCompleted() throws InterruptedException {
            if (!mRegisterCountDownLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Register failed in " + CALLBACK_SHORT_TIMEOUT_MS
                        + " ms.");
            }
        }
    }

    private static class TestCallback implements CarPropertyManager.CarPropertyEventCallback {
        private final List<CarPropertyValue<?>> mCarPropertyValues = new ArrayList<>();
        private final int mNumberOfExpectedCarPropertyValues;
        private final CountDownLatch mCountDownLatch;

        TestCallback(int numberOfExpectedCarPropertyValues) {
            mNumberOfExpectedCarPropertyValues = numberOfExpectedCarPropertyValues;
            mCountDownLatch = new CountDownLatch(numberOfExpectedCarPropertyValues);
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            mCarPropertyValues.add(carPropertyValue);
            mCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propertyId, int areaId) {
            Log.e(TAG, "TestCallback onErrorEvent - property ID: " + propertyId + " areaId: "
                    + areaId);
        }

        public List<CarPropertyValue<?>> getCarPropertyValues() {
            try {
                assertWithMessage("Expected " + mNumberOfExpectedCarPropertyValues
                        + " CarPropertyValues before timeout, but only received: "
                        + mCarPropertyValues.size()).that(
                        mCountDownLatch.await(CALLBACK_SHORT_TIMEOUT_MS,
                                TimeUnit.MILLISECONDS)).isTrue();

            } catch (InterruptedException e) {
                fail("TestCallback was interrupted: " + e);
            }
            return mCarPropertyValues;
        }
    }

    private static class TestSequenceCallback implements
            CarPropertyManager.CarPropertyEventCallback {

        private final ConcurrentHashMap<Integer, CarPropertyValue> mRecorder =
                new ConcurrentHashMap<>();
        private int mCounter;
        private final CountDownLatch mEventsCountDownLatch;
        private final CountDownLatch mRegisterCountDownLatch = new CountDownLatch(2);

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            Log.e(TAG, "onChanged get a event " + value);
            mRecorder.put(value.getPropertyId(), value);
            mRegisterCountDownLatch.countDown();
            // Skip initial events
            if (value.getTimestamp() != 0) {
                mCounter++;
                mEventsCountDownLatch.countDown();
            }
        }

        TestSequenceCallback(int expectedTimes) {
            mEventsCountDownLatch = new CountDownLatch(expectedTimes);
        }

        @Override
        public void onErrorEvent(int properId, int zone) {
            Log.e(TAG, "TestSequenceCallback get an onErrorEvent");
        }

        public CarPropertyValue getLastCarPropertyValue(int propId) {
            return mRecorder.get(propId);
        }

        public int getEventCounter() {
            return mCounter;
        }

        public void assertOnChangeEventCalled() throws InterruptedException {
            if (!mEventsCountDownLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Callback is not called in "
                        + CALLBACK_SHORT_TIMEOUT_MS + " ms.");
            }
        }

        public void assertRegisterCompleted() throws InterruptedException {
            if (!mRegisterCountDownLatch.await(CALLBACK_SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Register failed in " + CALLBACK_SHORT_TIMEOUT_MS
                        + " ms.");
            }
        }
    }

}
