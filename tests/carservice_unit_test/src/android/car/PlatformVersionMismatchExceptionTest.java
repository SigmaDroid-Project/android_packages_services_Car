/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.car;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;

import org.junit.Test;

public final class PlatformVersionMismatchExceptionTest {

    @Test
    public void testExpectedVersionAndMessage() {
        int expectedMajorVersion = 33;
        int expectedMinorVersion = 1;

        PlatformVersionMismatchException exception = new PlatformVersionMismatchException(
                PlatformApiVersion.forMajorAndMinorVersions(expectedMajorVersion,
                        expectedMinorVersion));

        PlatformApiVersion expectedApiVersion = exception.getExpectedPlatformApiVersion();

        assertThat(expectedApiVersion.getMajorVersion()).isEqualTo(expectedMajorVersion);
        assertThat(expectedApiVersion.getMinorVersion()).isEqualTo(expectedMinorVersion);

        String message = exception.getMessage();
        assertThat(message).contains("Expected version: " + expectedApiVersion);
        assertThat(message).contains("Current version: " + Car.getPlatformApiVersion());
    }

    @Test
    public void testExceptionParceable() {
        int expectedMajorVersion = 33;
        int expectedMinorVersion = 1;

        PlatformVersionMismatchException exception = new PlatformVersionMismatchException(
                PlatformApiVersion.forMajorAndMinorVersions(expectedMajorVersion,
                        expectedMinorVersion));

        Parcel parcel = Parcel.obtain();
        try {
            exception.writeToParcel(parcel, 0);

            // reset parcel position
            parcel.setDataPosition(0);

            PlatformVersionMismatchException exceptionFromParcel =
                    PlatformVersionMismatchException.CREATOR.createFromParcel(parcel);

            assertThat(exceptionFromParcel.getExpectedPlatformApiVersion())
                    .isEqualTo(exception.getExpectedPlatformApiVersion());
        } finally {
            parcel.recycle();
        }
    }
}