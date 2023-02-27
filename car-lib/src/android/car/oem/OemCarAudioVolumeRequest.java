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

package android.car.oem;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.util.AnnotationValidations.validate;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.annotation.ApiRequirements;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.DataClass;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to encapsulate car volume audio evaluation request
 *
 * @hide
 */
@SystemApi
@ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
        minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
@DataClass(
        genToString = true,
        genHiddenConstructor = true,
        genHiddenConstDefs = true,
        genBuilder = true,
        genEqualsHashCode = true)
public final class OemCarAudioVolumeRequest implements Parcelable {
    private final int mAudioZoneId;
    private final int mCallState;
    @NonNull
    private final List<AudioAttributes> mActivePlaybackAttributes;
    @NonNull
    private final List<AudioAttributes> mDuckedAudioAttributes;
    @NonNull
    private final List<CarVolumeGroupInfo> mCarVolumeGroupInfos;



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/packages/services/Car/car-lib/src/android/car/oem
    // /OemCarAudioVolumeRequest.java
    // Added AddedInOrBefore or ApiRequirement Annotation manually
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    /**
     * Creates a new OemCarAudioVolumeRequest
     *
     * @hide
     */
    @DataClass.Generated.Member
    @VisibleForTesting()
    public OemCarAudioVolumeRequest(
            int audioZoneId,
            int callState,
            @NonNull List<AudioAttributes> activePlaybackAttributes,
            @NonNull List<AudioAttributes> duckedAudioAttributes,
            @NonNull List<CarVolumeGroupInfo> carVolumeGroupInfos) {
        this.mAudioZoneId = audioZoneId;
        this.mCallState = callState;
        this.mActivePlaybackAttributes = activePlaybackAttributes;
        validate(NonNull.class, null, mActivePlaybackAttributes);
        this.mDuckedAudioAttributes = duckedAudioAttributes;
        validate(NonNull.class, null, mDuckedAudioAttributes);
        this.mCarVolumeGroupInfos = carVolumeGroupInfos;
        validate(NonNull.class, null, mCarVolumeGroupInfos);

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * @return the audio zone id where the request belongs
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @DataClass.Generated.Member
    public int getAudioZoneId() {
        return mAudioZoneId;
    }

    /**
     * @return the current phone call state
     *
     * <p>Will be one of {@link TelephonyManager.CALL_STATE_IDLE},
     * {@link TelephonyManager.CALL_STATE_RINGING}, {@link TelephonyManager.CALL_STATE_OFFHOOK},
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @DataClass.Generated.Member
    public int getCallState() {
        return mCallState;
    }

    /**
     * @return audio attributes which are actively playing in the zone obtain by
     * {@code #getAudioZoneId()}
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @DataClass.Generated.Member
    public @NonNull List<AudioAttributes> getActivePlaybackAttributes() {
        return mActivePlaybackAttributes;
    }

    /**
     * @return the current ducked audio attributes
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @DataClass.Generated.Member
    public @NonNull List<AudioAttributes> getDuckedAudioAttributes() {
        return mDuckedAudioAttributes;
    }

    /**
     * @return the zone's volume infos, which can be used to determine the current state for a
     * particular volume change
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @DataClass.Generated.Member
    public @NonNull List<CarVolumeGroupInfo> getCarVolumeGroupInfos() {
        return mCarVolumeGroupInfos;
    }

    @Override
    @DataClass.Generated.Member
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "OemCarAudioVolumeRequest { " +
                "audioZoneId = " + mAudioZoneId + ", " +
                "callState = " + mCallState + ", " +
                "activePlaybackAttributes = " + mActivePlaybackAttributes + ", " +
                "duckedAudioAttributes = " + mDuckedAudioAttributes + ", " +
                "carVolumeGroupInfos = " + mCarVolumeGroupInfos +
        " }";
    }

    @Override
    @DataClass.Generated.Member
    public boolean equals(@android.annotation.Nullable Object o) {
        // You can override field equality logic by defining either of the methods like:
        // boolean fieldNameEquals(OemCarAudioVolumeRequest other) { ... }
        // boolean fieldNameEquals(FieldType otherValue) { ... }

        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        @SuppressWarnings("unchecked")
        OemCarAudioVolumeRequest that = (OemCarAudioVolumeRequest) o;
        //noinspection PointlessBooleanExpression
        return true
                && mAudioZoneId == that.mAudioZoneId
                && mCallState == that.mCallState
                && Objects.equals(mActivePlaybackAttributes, that.mActivePlaybackAttributes)
                && Objects.equals(mDuckedAudioAttributes, that.mDuckedAudioAttributes)
                && Objects.equals(mCarVolumeGroupInfos, that.mCarVolumeGroupInfos);
    }

    @Override
    @DataClass.Generated.Member
    public int hashCode() {
        // You can override field hashCode logic by defining methods like:
        // int fieldNameHashCode() { ... }

        int _hash = 1;
        _hash = 31 * _hash + mAudioZoneId;
        _hash = 31 * _hash + mCallState;
        _hash = 31 * _hash + Objects.hashCode(mActivePlaybackAttributes);
        _hash = 31 * _hash + Objects.hashCode(mDuckedAudioAttributes);
        _hash = 31 * _hash + Objects.hashCode(mCarVolumeGroupInfos);
        return _hash;
    }

    // TODO(b/260757994): Remove ApiRequirements for overridden methods
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @Override
    @DataClass.Generated.Member
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        dest.writeInt(mAudioZoneId);
        dest.writeInt(mCallState);
        dest.writeParcelableList(mActivePlaybackAttributes, flags);
        dest.writeParcelableList(mDuckedAudioAttributes, flags);
        dest.writeParcelableList(mCarVolumeGroupInfos, flags);
    }

    // TODO(b/260757994): Remove ApiRequirements for overridden methods
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @Override
    @DataClass.Generated.Member
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    @DataClass.Generated.Member
    OemCarAudioVolumeRequest(@NonNull Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        int audioZoneId = in.readInt();
        int callState = in.readInt();
        List<AudioAttributes> activePlaybackAttributes = new ArrayList<>();
        in.readParcelableList(activePlaybackAttributes, AudioAttributes.class.getClassLoader());
        List<AudioAttributes> duckedAudioAttributes = new ArrayList<>();
        in.readParcelableList(duckedAudioAttributes, AudioAttributes.class.getClassLoader());
        List<CarVolumeGroupInfo> carVolumeGroupInfos = new ArrayList<>();
        in.readParcelableList(carVolumeGroupInfos, CarVolumeGroupInfo.class.getClassLoader());

        this.mAudioZoneId = audioZoneId;
        this.mCallState = callState;
        this.mActivePlaybackAttributes = activePlaybackAttributes;
        validate(NonNull.class, null, mActivePlaybackAttributes);
        this.mDuckedAudioAttributes = duckedAudioAttributes;
        validate(NonNull.class, null, mDuckedAudioAttributes);
        this.mCarVolumeGroupInfos = carVolumeGroupInfos;
        validate(NonNull.class, null, mCarVolumeGroupInfos);

        // onConstructed(); // You can define this method to get a callback
    }

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @DataClass.Generated.Member
    public static final @NonNull Parcelable.Creator<OemCarAudioVolumeRequest> CREATOR
            = new Parcelable.Creator<OemCarAudioVolumeRequest>() {
        @Override
        public OemCarAudioVolumeRequest[] newArray(int size) {
            return new OemCarAudioVolumeRequest[size];
        }

        @Override
        public OemCarAudioVolumeRequest createFromParcel(@NonNull Parcel in) {
            return new OemCarAudioVolumeRequest(in);
        }
    };

    /**
     * A builder for {@link OemCarAudioVolumeRequest}
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @SuppressWarnings("WeakerAccess")
    @DataClass.Generated.Member
    public static final class Builder {

        private int mAudioZoneId;
        private int mCallState;
        private @NonNull List<AudioAttributes> mActivePlaybackAttributes = new ArrayList<>();
        private @NonNull List<AudioAttributes> mDuckedAudioAttributes = new ArrayList<>();;
        private @NonNull List<CarVolumeGroupInfo> mCarVolumeGroupInfos = new ArrayList<>();;

        private long mBuilderFieldsSet = 0L;

        /**
         * Creates a new Builder.
         *
         * @hide
         */
        public Builder(int audioZoneId) {
            mAudioZoneId = audioZoneId;
        }

        /**
         * Creates a new Builder based on a current request
         *
         * @hide
         */
        public Builder(OemCarAudioVolumeRequest volumeRequest) {
            mAudioZoneId = volumeRequest.mAudioZoneId;
            mActivePlaybackAttributes = volumeRequest.mActivePlaybackAttributes;
            mDuckedAudioAttributes = volumeRequest.mDuckedAudioAttributes;
            mCarVolumeGroupInfos = volumeRequest.mCarVolumeGroupInfos;
        }

        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @DataClass.Generated.Member
        public @NonNull Builder setCallState(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= 0x4;
            mCallState = value;
            return this;
        }

        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @DataClass.Generated.Member
        public @NonNull Builder setActivePlaybackAttributes(@NonNull List<AudioAttributes> value) {
            validate(NonNull.class, null, value);
            checkNotUsed();
            mBuilderFieldsSet |= 0x8;
            mActivePlaybackAttributes = value;
            return this;
        }

        /** @see #setActivePlaybackAttributes */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @DataClass.Generated.Member
        public @NonNull Builder addActivePlaybackAttributes(@NonNull AudioAttributes value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...
            validate(NonNull.class, null, value);
            if (mActivePlaybackAttributes == null) setActivePlaybackAttributes(new ArrayList<>());
            mActivePlaybackAttributes.add(value);
            return this;
        }

        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @DataClass.Generated.Member
        public @NonNull Builder setDuckedAudioAttributes(@NonNull List<AudioAttributes> value) {
            validate(NonNull.class, null, value);
            checkNotUsed();
            mBuilderFieldsSet |= 0x10;
            mDuckedAudioAttributes = value;
            return this;
        }

        /** @see #setDuckedAudioAttributes */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @DataClass.Generated.Member
        public @NonNull Builder addDuckedAudioAttributes(@NonNull AudioAttributes value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...
            validate(NonNull.class, null, value);
            if (mDuckedAudioAttributes == null) setDuckedAudioAttributes(new ArrayList<>());
            mDuckedAudioAttributes.add(value);
            return this;
        }

        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @DataClass.Generated.Member
        public @NonNull Builder setCarVolumeGroupInfos(@NonNull List<CarVolumeGroupInfo> value) {
            validate(NonNull.class, null, value);
            checkNotUsed();
            mBuilderFieldsSet |= 0x20;
            mCarVolumeGroupInfos = value;
            return this;
        }

        /** @see #setCarVolumeGroupInfos */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @DataClass.Generated.Member
        public @NonNull Builder addCarVolumeGroupInfos(@NonNull CarVolumeGroupInfo value) {
            // You can refine this method's name by providing item's singular name, e.g.:
            // @DataClass.PluralOf("item")) mItems = ...
            validate(NonNull.class, null, value);
            if (mCarVolumeGroupInfos == null) setCarVolumeGroupInfos(new ArrayList<>());
            mCarVolumeGroupInfos.add(value);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull
        OemCarAudioVolumeRequest build() {
            checkNotUsed();
            mBuilderFieldsSet |= 0x40; // Mark builder used

            OemCarAudioVolumeRequest o = new OemCarAudioVolumeRequest(
                    mAudioZoneId,
                    mCallState,
                    mActivePlaybackAttributes,
                    mDuckedAudioAttributes,
                    mCarVolumeGroupInfos);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & 0x40) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    @SuppressWarnings("unused")
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @DataClass.Generated(
            time = 1669862081880L,
            codegenVersion = "1.0.23",
            sourceFile = "packages/services/Car/car-lib/src/android"
                    + "/car/oem/OemCarAudioVolumeRequest.java",
            inputSignatures = "private final  int mAudioZoneId\nprivate final  int mCallState\n"
                    + "private final @android.annotation.NonNull "
                    + "java.util.List<android.media.AudioAttributes> mActivePlaybackAttributes\n"
                    + "private final @android.annotation.NonNull "
                    + "java.util.List<android.media.AudioAttributes> mDuckedAudioAttributes\n"
                    + "private final @android.annotation.NonNull "
                    + "java.util.List<android.car.media.CarVolumeGroupInfo> mCarVolumeGroupInfos\n"
                    + "class OemCarAudioVolumeRequest extends java.lang.Object "
                    + "implements [android.os.Parcelable]\n@com.android.car.internal.util"
                    + ".DataClass(genToString=true, genHiddenConstructor=true,"
                    + "genHiddenConstDefs=true, genBuilder=true, genEqualsHashCode=true)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
