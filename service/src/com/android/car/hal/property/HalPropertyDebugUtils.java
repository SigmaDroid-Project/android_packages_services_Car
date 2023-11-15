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

package com.android.car.hal.property;

import static java.lang.Integer.toHexString;

import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;
import android.hardware.automotive.vehicle.VehiclePropertyType;
import android.util.Slog;

import com.android.car.internal.util.ConstantDebugUtils;

/**
 * Utility class for converting {@link VehicleProperty} related information to human-readable names.
 */
public final class HalPropertyDebugUtils {
    private static final String TAG = HalPropertyDebugUtils.class.getSimpleName();

    /**
     * HalPropertyDebugUtils only contains static fields and methods and must never be
     * instantiated.
     */
    private HalPropertyDebugUtils() {
        throw new UnsupportedOperationException("Must never be called");
    }

    /**
     * Gets a user-friendly representation string representation of {@link VehicleArea}
     * constant for the passed {@code propertyId}.
     */
    public static String toAreaTypeString(int propertyId) {
        int areaType = propertyId & VehicleArea.MASK;
        return toDebugString(VehicleArea.class, areaType);
    }

    /**
     * Gets a user-friendly representation string representation of {@link VehiclePropertyGroup}
     * constant for the passed {@code propertyId}.
     */
    public static String toGroupString(int propertyId) {
        int group = propertyId & VehiclePropertyGroup.MASK;
        return toDebugString(VehiclePropertyGroup.class, group);
    }

    /**
     * Gets a user-friendly representation string representation of {@link VehiclePropertyType}
     * constant for the passed {@code propertyId}.
     */
    public static String toValueTypeString(int propertyId) {
        int valueType = propertyId & VehiclePropertyType.MASK;
        return toDebugString(VehiclePropertyType.class, valueType);
    }

    /**
     * Gets a user-friendly representation string representation of {@link VehiclePropertyAccess}
     * constant.
     */
    public static String toAccessString(int access) {
        return toDebugString(VehiclePropertyAccess.class, access);
    }

    /**
     * Gets a user-friendly representation string representation of
     * {@link VehiclePropertyChangeMode} constant.
     */
    public static String toChangeModeString(int changeMode) {
        return toDebugString(VehiclePropertyChangeMode.class, changeMode);
    }

    private static String toDebugString(Class<?> clazz, int constantValue) {
        String hexSuffix = "(0x" + toHexString(constantValue) + ")";
        if (ConstantDebugUtils.toName(clazz, constantValue) == null) {
            String invalidConstantValue = "INVALID_" + clazz.getSimpleName() + hexSuffix;
            Slog.e(TAG, invalidConstantValue);
            return invalidConstantValue;
        }
        return ConstantDebugUtils.toName(clazz, constantValue) + hexSuffix;
    }
}
