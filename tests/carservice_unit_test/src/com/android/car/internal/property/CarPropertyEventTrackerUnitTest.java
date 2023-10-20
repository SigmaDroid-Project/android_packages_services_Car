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

package com.android.car.internal.property;

import static com.google.common.truth.Truth.assertThat;

import android.car.hardware.CarPropertyValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;

@RunWith(MockitoJUnitRunner.class)
public final class CarPropertyEventTrackerUnitTest {
    private static final int FIRST_PROPERTY_ID = 1234;
    private static final int AREA_ID_1 = 908;
    private static final float FIRST_UPDATE_RATE_HZ = 1F;
    private static final long TIMESTAMP_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long FRESH_TIMESTAMP_NANOS = Duration.ofSeconds(2).toNanos();
    private static final long TOO_EARLY_TIMESTAMP_NANOS = Duration.ofMillis(1899).toNanos();
    private static final long ALMOST_FRESH_TIMESTAMP_NANOS = Duration.ofMillis(1999).toNanos();
    private static final long STALE_TIMESTAMP_NANOS = Duration.ofMillis(500).toNanos();
    private static final Integer INTEGER_VALUE_1 = 8438;
    private static final CarPropertyValue<Integer> GOOD_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TIMESTAMP_NANOS, INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> TOO_EARLY_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, TOO_EARLY_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> ALMOST_FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, ALMOST_FRESH_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> STALE_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(FIRST_PROPERTY_ID, AREA_ID_1, STALE_TIMESTAMP_NANOS,
                    INTEGER_VALUE_1);
    private CarPropertyEventTracker mCarPropertyEventTracker;

    @Before
    public void setup() {
        mCarPropertyEventTracker = new CarPropertyEventTracker(FIRST_UPDATE_RATE_HZ);
    }

    @Test
    public void hasNextUpdateTimeArrived_returnsTrueForFirstEvent() {
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(GOOD_CAR_PROPERTY_VALUE))
                .isTrue();
    }

    @Test
    public void hasNextUpdateTimeArrived_returnsFalseForStaleEvent() {
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(GOOD_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(
                STALE_CAR_PROPERTY_VALUE)).isFalse();
    }

    @Test
    public void hasNextUpdateTimeArrived_returnsFalseForEventArrivedTooEarly() {
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(GOOD_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(
                TOO_EARLY_CAR_PROPERTY_VALUE)).isFalse();
    }

    @Test
    public void hasNextUpdateTimeArrived_returnsTrueForEventWithinOffset() {
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(GOOD_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(
                ALMOST_FRESH_CAR_PROPERTY_VALUE)).isTrue();
    }

    @Test
    public void hasNextUpdateTimeArrived_returnsTrueForFreshEvent() {
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(GOOD_CAR_PROPERTY_VALUE))
                .isTrue();
        assertThat(mCarPropertyEventTracker.hasNextUpdateTimeArrived(FRESH_CAR_PROPERTY_VALUE))
                .isTrue();
    }
}
