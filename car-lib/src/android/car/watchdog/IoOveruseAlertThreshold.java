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

package android.car.watchdog;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.car.annotation.AddedInOrBefore;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;

/**
 * System-wide disk I/O overuse alert threshold.
 *
 * @hide
 */
@SystemApi
@DataClass(genToString = true, genHiddenConstructor = true)
public final class IoOveruseAlertThreshold implements Parcelable {
    /**
     * Duration over which the given written bytes per second should be checked against.
     *
     * <p>Non-zero duration must provided in seconds.
     */
    @SuppressLint({"MethodNameUnits"})
    private long mDurationInSeconds;

    /**
     * Alert I/O overuse on reaching the written bytes/second over {@link #mDurationInSeconds}.
     *
     * <p>Must provide non-zero bytes.
     */
    private long mWrittenBytesPerSecond;



    // Code below generated by codegen v1.0.22.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/watchdog/IoOveruseAlertThreshold.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new IoOveruseAlertThreshold.
     *
     * @param durationInSeconds
     *   Duration over which the given written bytes per second should be checked against.
     *
     *   <p>Non-zero duration must provided in seconds.
     * @param writtenBytesPerSecond
     *   Alert I/O overuse on reaching the written bytes/second over {@link #mDurationInSeconds}.
     *
     *   <p>Must provide non-zero bytes.
     * @hide
     */
    @DataClass.Generated.Member
    public IoOveruseAlertThreshold(
            @SuppressLint({ "MethodNameUnits" }) long durationInSeconds,
            long writtenBytesPerSecond) {
        this.mDurationInSeconds = durationInSeconds;
        this.mWrittenBytesPerSecond = writtenBytesPerSecond;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * Duration over which the given written bytes per second should be checked against.
     *
     * <p>Non-zero duration must provided in seconds.
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public @SuppressLint({ "MethodNameUnits" }) long getDurationInSeconds() {
        return mDurationInSeconds;
    }

    /**
     * Alert I/O overuse on reaching the written bytes/second over {@link #mDurationInSeconds}.
     *
     * <p>Must provide non-zero bytes.
     */
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public long getWrittenBytesPerSecond() {
        return mWrittenBytesPerSecond;
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "IoOveruseAlertThreshold { " +
                "durationInSeconds = " + mDurationInSeconds + ", " +
                "writtenBytesPerSecond = " + mWrittenBytesPerSecond +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeLong(mDurationInSeconds);
        dest.writeLong(mWrittenBytesPerSecond);
    }

    @Override
    @DataClass.Generated.Member
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    /* package-private */ IoOveruseAlertThreshold(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        long durationInSeconds = in.readLong();
        long writtenBytesPerSecond = in.readLong();

        this.mDurationInSeconds = durationInSeconds;
        this.mWrittenBytesPerSecond = writtenBytesPerSecond;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    @AddedInOrBefore(majorVersion = 33)
    public static final @NonNull Parcelable.Creator<IoOveruseAlertThreshold> CREATOR
            = new Parcelable.Creator<IoOveruseAlertThreshold>() {
        @Override
        public IoOveruseAlertThreshold[] newArray(int size) {
            return new IoOveruseAlertThreshold[size];
        }

        @Override
        public IoOveruseAlertThreshold createFromParcel(@NonNull android.os.Parcel in) {
            return new IoOveruseAlertThreshold(in);
        }
    };

    @DataClass.Generated(
            time = 1615312119067L,
            codegenVersion = "1.0.22",
            sourceFile = "packages/services/Car/car-lib/src/android/car/watchdog/IoOveruseAlertThreshold.java",
            inputSignatures = "private @android.annotation.SuppressLint long mDurationInSeconds\nprivate  long mWrittenBytesPerSecond\nclass IoOveruseAlertThreshold extends java.lang.Object implements [android.os.Parcelable]\n@com.android.car.internal.util.DataClass(genToString=true, genHiddenConstructor=true)")
    @Deprecated
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
