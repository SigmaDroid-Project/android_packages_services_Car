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

package com.android.car.audio;

import static android.car.media.CarAudioManager.INVALID_REQUEST_ID;

import android.annotation.Nullable;
import android.car.CarOccupantZoneManager;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.car.media.IMediaAudioRequestStatusCallback;
import android.car.media.IPrimaryZoneMediaAudioRequestCallback;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class MediaRequestHandler {

    private static final String TAG = CarLog.TAG_AUDIO;
    private static final String REQUEST_HANDLER_THREAD_NAME = "CarAudioMediaRequest";

    private final HandlerThread mHandlerThread = CarServiceUtils.getHandlerThread(
            REQUEST_HANDLER_THREAD_NAME);
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArrayMap<Long, InternalMediaAudioRequest> mMediaAudioRequestIdToCallback =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private long mMediaRequestCounter;
    @GuardedBy("mLock")
    private final ArraySet<Long> mUsedMediaRequestIds = new ArraySet<>();
    @GuardedBy("mLock")
    private final ArraySet<CarOccupantZoneManager.OccupantZoneInfo> mAssignedOccupants =
            new ArraySet<>();
    @GuardedBy("mLock")
    private final ArrayMap<Long, IBinder> mRequestIdToApprover = new ArrayMap<>();
    @GuardedBy("mLock")
    private final RemoteCallbackList<IPrimaryZoneMediaAudioRequestCallback>
            mPrimaryZoneMediaAudioRequestCallbacks = new RemoteCallbackList<>();

    boolean registerPrimaryZoneMediaAudioRequestCallback(
            IPrimaryZoneMediaAudioRequestCallback callback) {
        Objects.requireNonNull(callback, "Media request callback can not be null");

        synchronized (mLock) {
            return mPrimaryZoneMediaAudioRequestCallbacks.register(callback);
        }
    }

    boolean unregisterPrimaryZoneMediaAudioRequestCallback(
            IPrimaryZoneMediaAudioRequestCallback callback) {
        Objects.requireNonNull(callback, "Media request callback can not be null");

        synchronized (mLock) {
            return mPrimaryZoneMediaAudioRequestCallbacks.unregister(callback);
        }
    }

    boolean isAudioMediaCallbackRegistered(IBinder token) {
        boolean contains = false;

        synchronized (mLock) {
            int n = mPrimaryZoneMediaAudioRequestCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                IPrimaryZoneMediaAudioRequestCallback callback =
                        mPrimaryZoneMediaAudioRequestCallbacks.getBroadcastItem(i);
                if (callback.asBinder().equals(token)) {
                    contains = true;
                    break;
                }
            }
            mPrimaryZoneMediaAudioRequestCallbacks.finishBroadcast();
        }
        return contains;
    }

    long requestMediaAudioOnPrimaryZone(IMediaAudioRequestStatusCallback callback,
            CarOccupantZoneManager.OccupantZoneInfo info) {
        Objects.requireNonNull(callback, "Media audio request status callback can not be null");
        Objects.requireNonNull(info, "Occupant zone info can not be null");
        long requestId = generateMediaRequestId();
        Slogf.v(TAG, "requestMediaAudioOnPrimaryZone " + requestId);

        synchronized (mLock) {
            if (callbackAlreadyPresentLocked(callback)) {
                Slogf.e(TAG, "Can not register media request callback, do not re-use callbacks");
                return INVALID_REQUEST_ID;
            }

            mMediaAudioRequestIdToCallback.put(requestId,
                    new InternalMediaAudioRequest(callback, info));
        }

        mHandler.post(() -> handleMediaAudioRequest(info, requestId));
        return requestId;
    }

    boolean acceptMediaAudioRequest(IBinder token, long requestId) {
        Objects.requireNonNull(token, "Media request token can not be null");
        InternalMediaAudioRequest request;
        synchronized (mLock) {
            request = mMediaAudioRequestIdToCallback.get(requestId);
            if (request == null) {
                Slogf.w(TAG, "Request %d was remove before it was accepted", requestId);
                return false;
            }
            mAssignedOccupants.add(request.mOccupantZoneInfo);
            mRequestIdToApprover.put(requestId, token);
        }

        return informMediaAudioRequestCallbackAndApprovers(
                request.mIMediaAudioRequestStatusCallback, request.mOccupantZoneInfo,
                "acceptance", requestId, /* allowed= */ true);
    }

    boolean rejectMediaAudioRequest(long requestId) {
        InternalMediaAudioRequest request = removeAudioMediaRequest(requestId);
        if (request == null) {
            Slogf.w(TAG, "Request %d was remove before it was rejected", requestId);
            return false;
        }

        mHandler.post(() -> informMediaAudioRequestCallbackAndApprovers(
                request.mIMediaAudioRequestStatusCallback, request.mOccupantZoneInfo,
                "rejection", requestId, /* allowed= */ false));
        return true;
    }

    boolean cancelMediaAudioOnPrimaryZone(long requestId) {
        InternalMediaAudioRequest request = removeAudioMediaRequest(requestId);
        if (request == null) {
            Slogf.w(TAG, "Request %d was remove before it was cancelled", requestId);
            return false;
        }

        try {
            request.mIMediaAudioRequestStatusCallback.onMediaAudioRequestStatusChanged(
                    request.mOccupantZoneInfo,
                    requestId, CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "Could not inform callback about request %d changed", requestId);
        }

        return broadcastToCallbacks(requestId, request,
                CarAudioManager.AUDIO_REQUEST_STATUS_CANCELLED);
    }

    boolean stopMediaAudioOnPrimaryZone(long requestId) {
        InternalMediaAudioRequest request = removeAudioMediaRequest(requestId);
        if (request == null) {
            return false;
        }

        try {
            request.mIMediaAudioRequestStatusCallback.onMediaAudioRequestStatusChanged(
                    request.mOccupantZoneInfo,
                    requestId, CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "Could not inform callback about request %d changed", requestId);
        }

        return broadcastToCallbacks(requestId, request,
                CarAudioManager.AUDIO_REQUEST_STATUS_STOPPED);
    }

    CarOccupantZoneManager.OccupantZoneInfo getOccupantForRequest(long requestId) {
        InternalMediaAudioRequest request;
        synchronized (mLock) {
            request = mMediaAudioRequestIdToCallback.get(requestId);
        }
        return request == null ? null : request.mOccupantZoneInfo;
    }

    long getRequestIdForOccupant(CarOccupantZoneManager.OccupantZoneInfo info) {
        synchronized (mLock) {
            for (int index = 0; index < mMediaAudioRequestIdToCallback.size(); index++) {
                InternalMediaAudioRequest request = mMediaAudioRequestIdToCallback.valueAt(index);
                if (request.mOccupantZoneInfo.equals(info)) {
                    return mMediaAudioRequestIdToCallback.keyAt(index);
                }
            }
        }
        return INVALID_REQUEST_ID;
    }

    boolean isMediaAudioAllowedInPrimaryZone(
            @Nullable CarOccupantZoneManager.OccupantZoneInfo info) {
        if (info == null) {
            return false;
        }
        synchronized (mLock) {
            return mAssignedOccupants.contains(info);
        }
    }

    List<Long> getRequestsOwnedByApprover(IPrimaryZoneMediaAudioRequestCallback callback) {
        List<Long> ownedRequests = new ArrayList<>();
        synchronized (mLock) {
            for (int index = 0; index < mRequestIdToApprover.size(); index++) {
                if (callback.asBinder().equals(mRequestIdToApprover.valueAt(index))) {
                    ownedRequests.add(mRequestIdToApprover.keyAt(index));
                }
            }
        }
        return ownedRequests;
    }

    @GuardedBy("mLock")
    private boolean callbackAlreadyPresentLocked(IMediaAudioRequestStatusCallback callback) {
        for (int index = 0; index < mMediaAudioRequestIdToCallback.size(); index++) {
            InternalMediaAudioRequest request = mMediaAudioRequestIdToCallback.valueAt(index);
            if (request.mIMediaAudioRequestStatusCallback.asBinder().equals(callback.asBinder())) {
                return true;
            }
        }
        return false;
    }

    private void handleMediaAudioRequest(CarOccupantZoneManager.OccupantZoneInfo info,
            long requestId) {
        boolean handled = false;
        int n;

        synchronized (mLock) {
            n = mPrimaryZoneMediaAudioRequestCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                IPrimaryZoneMediaAudioRequestCallback callback =
                        mPrimaryZoneMediaAudioRequestCallbacks.getBroadcastItem(i);
                try {
                    Slogf.v(TAG, "handleMediaAudioRequest " + requestId + " occupant " + info);
                    callback.onRequestMediaOnPrimaryZone(info, requestId);
                    handled = true;
                } catch (RemoteException e) {
                    Slogf.e(TAG, e, "Could not handle Media request for request id "
                                    + "%d and occupant zone info %s"
                                    + ", there are %d of request callback registered",
                            requestId, info, n);
                }
            }
            mPrimaryZoneMediaAudioRequestCallbacks.finishBroadcast();
        }
        if (!handled) {
            Slogf.e(TAG,
                    "Could not handle Media request for request id %d and occupant zone info %s"
                            + ", there are %d of request callback registered", requestId, info, n);
            rejectMediaAudioRequest(requestId);
        }
    }

    private boolean broadcastToCallbacks(long requestId, InternalMediaAudioRequest request,
            int status) {
        boolean handled = false;

        synchronized (mLock) {
            int n = mPrimaryZoneMediaAudioRequestCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                IPrimaryZoneMediaAudioRequestCallback callback =
                        mPrimaryZoneMediaAudioRequestCallbacks.getBroadcastItem(i);
                try {
                    Slogf.v(TAG, "cancelMediaAudioOnPrimaryZone " + requestId + " occupant "
                            + request.mOccupantZoneInfo);
                    callback.onMediaAudioRequestStatusChanged(request.mOccupantZoneInfo, requestId,
                            status);
                    handled = true;
                } catch (RemoteException e) {
                    Slogf.e(TAG, e,
                            "Could not handle Media request for request id %d "
                                    + "and occupant zone info %s"
                                    + ", there are %d of request callback registered",
                            requestId, request.mOccupantZoneInfo, n);
                }
            }
            mPrimaryZoneMediaAudioRequestCallbacks.finishBroadcast();
        }

        return handled;
    }

    @Nullable
    private InternalMediaAudioRequest removeAudioMediaRequest(long requestId) {
        InternalMediaAudioRequest request;
        synchronized (mLock) {
            request = mMediaAudioRequestIdToCallback.remove(requestId);
            mUsedMediaRequestIds.remove(requestId);
            if (request == null) {
                return null;
            }
            mAssignedOccupants.remove(request.mOccupantZoneInfo);
            mRequestIdToApprover.remove(requestId);

            // Reset counter back to lower value,
            // on request the search will automatically assign this value or a newer one.
            if (mMediaRequestCounter > requestId) {
                mMediaRequestCounter = requestId;
            }
        }
        return request;
    }

    private boolean informMediaAudioRequestCallbackAndApprovers(
            IMediaAudioRequestStatusCallback callback, CarOccupantZoneManager.OccupantZoneInfo info,
            String message, long requestId, boolean allowed) {
        if (callback == null) {
            Slogf.w(TAG, "Request's %d callback was removed before being handled for %s",
                    requestId, message);
            return false;
        }

        int status = allowed ? CarAudioManager.AUDIO_REQUEST_STATUS_APPROVED :
                CarAudioManager.AUDIO_REQUEST_STATUS_REJECTED;
        try {
            callback.onMediaAudioRequestStatusChanged(info, requestId, status);
        } catch (RemoteException e) {
            Slogf.e(TAG, e, "Request's %d callback error",
                    requestId);
        }

        boolean handled = false;

        synchronized (mLock) {
            int n = mPrimaryZoneMediaAudioRequestCallbacks.beginBroadcast();
            for (int i = 0; i < n; i++) {
                IPrimaryZoneMediaAudioRequestCallback primaryCallback =
                        mPrimaryZoneMediaAudioRequestCallbacks.getBroadcastItem(i);
                try {
                    Slogf.v(TAG,
                            "informMediaAudioRequestCallbackAndApprovers %s occupant %s status %s",
                            requestId, info, status);
                    primaryCallback.onMediaAudioRequestStatusChanged(info, requestId, status);
                    handled = true;
                } catch (RemoteException e) {
                    Slogf.e(TAG, e,
                            "Could not handle Media request for request id %d "
                                    + "and occupant zone info %s"
                                    + ", there are %d of request callback registered",
                            requestId, info, n);
                }
            }
            mPrimaryZoneMediaAudioRequestCallbacks.finishBroadcast();
        }

        return handled;
    }

    private long generateMediaRequestId() {
        synchronized (mLock) {
            while (mMediaRequestCounter < Long.MAX_VALUE) {
                if (mUsedMediaRequestIds.contains(mMediaRequestCounter++)) {
                    continue;
                }

                mUsedMediaRequestIds.add(mMediaRequestCounter);
                return mMediaRequestCounter;
            }

            mMediaRequestCounter = 0;
        }

        throw new IllegalStateException("Could not generate media request id");
    }

    private static class InternalMediaAudioRequest {
        private final IMediaAudioRequestStatusCallback mIMediaAudioRequestStatusCallback;
        private final CarOccupantZoneManager.OccupantZoneInfo mOccupantZoneInfo;

        InternalMediaAudioRequest(IMediaAudioRequestStatusCallback callback,
                CarOccupantZoneManager.OccupantZoneInfo info) {
            mIMediaAudioRequestStatusCallback = callback;
            mOccupantZoneInfo = info;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Occupant zone info: ");
            builder.append(mOccupantZoneInfo);
            builder.append(" Callback: ");
            builder.append(mIMediaAudioRequestStatusCallback);
            return builder.toString();
        }
    }
}