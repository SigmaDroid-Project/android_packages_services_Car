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

package android.car.occupantconnection;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager.OccupantZoneState;
import android.car.annotation.ApiRequirements;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * API for communication between different endpoints on the occupant zones in the car.
 * <p>
 * Unless specified explicitly, a client means an app that uses this API and runs as a
 * foreground user on an occupant zone, while a peer client means an app that has the same package
 * name as the caller app and runs as another foreground user (on another occupant zone or even
 * another Android system).
 * An endpoint means a component (such as a Fragment or an Activity) that has an instance of
 * {@link CarOccupantConnectionManager}.
 * <p>
 * Communication between apps with different package names is not supported.
 * <p>
 * A common use case of this API is like:
 * <pre>
 *     ==========================================        =========================================
 *     =        client1 (occupantZone1)         =        =        client2 (occupantZone2)        =
 *     =                                        =        =                                       =
 *     =    ************     ************       =        =    ************      ************     =
 *     =    * sender1A *     * sender1B *       =        =    * sender2A *      * sender2B *     =
 *     =    ************     ************       =        =    ************      ************     =
 *     =                                        =        =                                       =
 *     =    ****************************        =        =    ****************************       =
 *     =    *     ReceiverService1     *        =        =    *     ReceiverService2     *       =
 *     =    ****************************        =        =    ****************************       =
 *     =                                        =        =                                       =
 *     =    **************    **************    =        =    **************   **************    =
 *     =    * receiver1A *    * receiver1B *    =        =    * receiver2A *   * receiver2B *    =
 *     =    **************    **************    =        =    **************   **************    =
 *     ==========================================        =========================================
 * </pre>
 * <ul>
 *   <li> Client1 and client2 must have the same package name. Client1 runs on occupantZone1
 *        while client2 runs on occupantZone2. Sender1A (an endpoint in client1) wants to
 *        send a {@link Payload} to receiver2A (an endpoint in client2).
 *   <li> Pre-connection:
 *     <ul>
 *       <li> The client app inherits {@link AbstractReceiverService} and declares the service in
 *            its manifest file.
 *     </ul>
 *   <li> Establish connection:
 *     <ul>
 *       <li> Sender1A monitors occupantZone2 by calling {@link
 *            android.car.CarRemoteDeviceManager#registerOccupantZoneStateCallback}.
 *       <li> Sender1A waits until the {@link OccupantZoneState} of occupantZone2 becomes
 *            {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_CONNECTION_READY} (and {@link
 *            android.car.CarRemoteDeviceManager#FLAG_SCREEN_UNLOCKED} and {@link
 *            android.car.CarRemoteDeviceManager#FLAG_CLIENT_IN_FOREGROUND} if UI is needed to
 *            establish the connection), then requests a connection to occupantZone2 by calling
 *            {@link #requestConnection}.
 *       <li> ReceiverService2 is started and bound by car service ({@link
 *            com.android.car.occupantconnection.CarOccupantConnectionService} automatically.
 *            ReceiverService2 is notified via {@link
 *            AbstractReceiverService#onConnectionInitiated}.
 *       <li> ReceiverService2 accepts the connection by calling {@link
 *            AbstractReceiverService#acceptConnection}.
 *       <li> Then the one-way connection is established. Sender1A is notified via {@link
 *            ConnectionRequestCallback#onConnected}, and ReceiverService2 is notified via
 *            {@link AbstractReceiverService#onConnected}.
 *     </ul>
 *   <li> Send Payload:
 *     <ul>
 *       <li> Sender1A sends a Payload to occupantZone2 by calling {@link #sendPayload}.
 *       <li> ReceiverService2 is notified for the Payload via {@link
 *            AbstractReceiverService#onPayloadReceived}.
 *            In this method, ReceiverService2 can forward the Payload to client2's receiver
 *            endpoints (if any), or cache the Payload and forward it later once a new receiver
 *            endpoint is registered.
 *     </ul>
 *   <li> Register receiver:
 *     <ul>
 *       <li> Receiver2A calls {@link #registerReceiver}. Then ReceiverService2 is notified
 *            via {@link AbstractReceiverService#onReceiverRegistered}. In that method,
 *            ReceiverService2 forwards the cached Payload to Receiver2A via {@link
 *            AbstractReceiverService#forwardPayload}.
 *            <p>
 *            Note: this step can be done before "Establish connection". In this case,
 *            ReceiverService2 will be started and bound by car service early.
 *            Once sender1A sends a Payload to occupantZone2, ReceiverService2 will be notified
 *            via {@link AbstractReceiverService#onReceiverRegistered}. In that method,
 *            ReceiverService2 can forward the Payload to Receiver2A without caching.
 *       <li> Receiver2A is notified for the Payload via {@link PayloadCallback#onPayloadReceived}.
 *     </ul>
 *   <li> Terminate the connection:
 *   <ul>
 *     <li> Sender1A terminates the connection to occupantZone2:
 *          Once sender1A no longer needs to send Payload to occupantZone2, it terminates the
 *          connection by calling {@link #disconnect}. Then sender1A is notified via
 *          {@link ConnectionRequestCallback#onDisconnected}, and ReceiverService2 is notified via
 *          {@link AbstractReceiverService#onDisconnected}.
 *     <li> Unregister receiver2A:
 *          Once receiver2A no longer needs to receive Payload from any other occupant zones,
 *          it calls {@link #unregisterReceiver}.
 *    <li> Unbound and destroy ReceiverService2:
 *         Since all the senders have disconnected from occupantZone2 and there is no receiver
 *         registered on occupantZone2, ReceiverService2 will be unbound and destroyed
 *         automatically.
 *   </ul>
 *   <li> Sender1A stops monitoring other occupant zones by calling {@link
 *        android.car.CarRemoteDeviceManager#unregisterOccupantZoneStateCallback}. This step can
 *        be done before or after "Terminate the connection".
 * </ul>
 * <p>
 * For a given {@link android.car.Car} instance, the CarOccupantConnectionManager is a singleton.
 * However, the client app may create multiple {@link android.car.Car} instances thus create
 * multiple CarOccupantConnectionManager instances. These CarOccupantConnectionManager instances
 * are treated as the same instance for the client app. For example:
 * <ul>
 *   <li> Sender1A creates a CarOccupantConnectionManager instance (managerA), while sender1B
 *        creates a different CarOccupantConnectionManager instance (managerB). Then sender1A uses
 *        managerA to request a connection to occupantZone2. Once connected, sender1B can use
 *        managerB to send Payload to occupantZone2 without requesting a new connection.
 *        To know whether it is connected to occupantZone2, sender1B can call {@link #isConnected}.
 *   <li> Besides, sender1B can terminate the connection by calling managerB#disconnect(), despite
 *        that the connection was requested by sender1A. Once the connection is terminated, sender1A
 *        will be notified via {@link ConnectionRequestCallback#onDisconnected}, and sender1B will
 *        not be notified since it didn't register register the {@link ConnectionRequestCallback}.
 * </ul>
 *
 * @hide
 */
// TODO(b/257117236): Change it to system API once it's ready to release.
// @SystemApi
public final class CarOccupantConnectionManager extends CarManagerBase {

    private static final String TAG = CarOccupantConnectionManager.class.getSimpleName();

    /** The connection request failed because of a different error than the errors listed below. */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public static final int CONNECTION_ERROR_UNKNOWN = 0;

    /**
     * The connection request failed because the {@link OccupantZoneState} of the peer occupant zone
     * was not {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_CONNECTION_READY}. To avoid
     * this error, the caller endpoint should ensure its state is {@link
     * android.car.CarRemoteDeviceManager#FLAG_CLIENT_CONNECTION_READY} before requesting a
     * connection to it.
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public static final int CONNECTION_ERROR_NOT_READY = 1;

    /**
     * Flags for the error type of connection request.
     *
     * @hide
     */
    @IntDef(flag = false, prefix = {"CONNECTION_ERROR_"}, value = {
            CONNECTION_ERROR_UNKNOWN,
            CONNECTION_ERROR_NOT_READY
    })
    @Retention(RetentionPolicy.SOURCE)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public @interface ConnectionError {
    }

    /**
     * A callback for lifecycle events of a connection request. When the endpoint (sender) calls
     * {@link #requestConnection} to connect to its peer client, it will be notified for the events.
     * The sender may call {@link #cancelConnection} if none of the events are triggered for a
     * long time.
     */
    public interface ConnectionRequestCallback {
        /**
         * Invoked when the one-way connection has been established.
         * <p>
         * In order to establish the connection, the receiver {@link AbstractReceiverService}
         * must accept the connection, and the sender must not cancel the request before the
         * connection is established.
         * Once the connection is established, the sender can send {@link Payload} to the
         * receiver client.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
        void onConnected(@NonNull OccupantZoneInfo receiverZone);

        /**
         * Invoked when the connection request has been rejected by the receiver client.
         *
         * @param rejectionReason the reason for rejection, such as the user rejected, UX
         *                        restricted. The client app is responsible for defining this value.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
        void onRejected(@NonNull OccupantZoneInfo receiverZone, int rejectionReason);

        /**
         * Invoked when there was an error when establishing the connection. For example, the
         * receiver client is not ready for connection.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
        void onFailed(@NonNull OccupantZoneInfo receiverZone,
                @ConnectionError int connectionError);

        /**
         * Invoked when the connection is terminated. For example, the receiver {@link
         * AbstractReceiverService} is unbound and destroyed, is crashed, or the receiver client
         * has become unreachable.
         * <p>
         * Once disconnected, the sender can no longer send {@link Payload} to the receiver
         * client.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
        void onDisconnected(@NonNull OccupantZoneInfo receiverZone);
    }

    /** A callback to listen to connection state changes. */
    public interface ConnectionStateCallback {
        /**
         * Invoked when the callback is registered, or when the connection to the occupant zone
         * is established or terminated.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
        void onConnectionChanged(@NonNull OccupantZoneInfo receiverZone, boolean isConnected);
    }

    /** A callback to receive a {@link Payload}. */
    public interface PayloadCallback {
        /**
         * Invoked when the receiver endpoint has received a {@link Payload} from {@code
         * senderZone}.
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
                minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
        void onPayloadReceived(@NonNull OccupantZoneInfo senderZone,
                @NonNull Payload payload);
    }

    /** An exception to indicate that it failed to send the {@link Payload}. */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public final class PayloadTransferException extends Exception {
    }

    private static final int ICONNECTION_REQUEST_CALLBACK_ON_CONNECTED = 1;
    private static final int ICONNECTION_REQUEST_CALLBACK_ON_REJECTED = 2;
    private static final int ICONNECTION_REQUEST_CALLBACK_ON_FAILED = 3;
    private static final int ICONNECTION_REQUEST_CALLBACK_ON_DISCONNECTED = 4;

    private static final int UNUSED_INT_PARAMETER = 0;

    private final ICarOccupantConnection mService;

    private final Object mLock = new Object();

    /**
     * A map of connection requests. The key is the ID of the request, and the value is the callback
     * and executor.
     */
    @GuardedBy("mLock")
    private final SparseArray<Pair<ConnectionRequestCallback, Executor>>
            mConnectionRequestMap = new SparseArray<>();

    private final IConnectionRequestCallback mBinderConnectionRequestCallback =
            new IConnectionRequestCallback.Stub() {
                @Override
                public void onConnected(int requestId, OccupantZoneInfo receiverZone) {
                    onConnectionRequestCallbackInvoked(ICONNECTION_REQUEST_CALLBACK_ON_CONNECTED,
                            requestId,
                            receiverZone,
                            /* rejectionReason= */ UNUSED_INT_PARAMETER,
                            /* connectionError= */ UNUSED_INT_PARAMETER);
                }

                @Override
                public void onRejected(int requestId, OccupantZoneInfo receiverZone,
                        int rejectionReason) {
                    onConnectionRequestCallbackInvoked(ICONNECTION_REQUEST_CALLBACK_ON_REJECTED,
                            requestId,
                            receiverZone,
                            rejectionReason,
                            /* connectionError= */ UNUSED_INT_PARAMETER);
                }

                @Override
                public void onFailed(int requestId, OccupantZoneInfo receiverZone,
                        int connectionError) {
                    onConnectionRequestCallbackInvoked(ICONNECTION_REQUEST_CALLBACK_ON_FAILED,
                            requestId,
                            receiverZone,
                            /* rejectionReason= */ UNUSED_INT_PARAMETER,
                            connectionError);
                }

                @Override
                public void onDisconnected(int requestId, OccupantZoneInfo receiverZone) {
                    onConnectionRequestCallbackInvoked(ICONNECTION_REQUEST_CALLBACK_ON_DISCONNECTED,
                            requestId,
                            receiverZone,
                            /* rejectionReason= */ UNUSED_INT_PARAMETER,
                            /* connectionError= */ UNUSED_INT_PARAMETER);
                }
            };

    /**
     * The ID for the current connection request. It will increase by one each time {@link
     * #requestConnection} is called.
     */
    @GuardedBy("mLock")
    private int mCurrentRequestId;

    /** @hide */
    public CarOccupantConnectionManager(Car car, IBinder service) {
        super(car);
        mService = ICarOccupantConnection.Stub.asInterface(service);
        // mCurrentRequestId can start with any value, such as 0. The non-zero starting value here
        // is mainly for debugging.
        int userId = Process.myUserHandle().getIdentifier();
        mCurrentRequestId = userId * 10000;
    }

    /** @hide */
    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    public void onCarDisconnected() {
        synchronized (mLock) {
            mConnectionRequestMap.clear();
        }
    }

    /**
     * Registers a {@link PayloadCallback} to receive {@link Payload}. If the {@link
     * AbstractReceiverService} in the caller app was not started yet, it will be started and
     * bound by car service automatically.
     * <p>
     * The caller endpoint must call {@link #unregisterReceiver} before it is destroyed.
     *
     * @param receiverEndpointId the ID of this receiver endpoint. Since there might be multiple
     *                           receiver endpoints in the client app, the ID can be used by
     *                           {@link AbstractReceiverService#onPayloadReceived} to decide which
     *                           endpoint(s) to dispatch the Payload to.
     * @param executor           the Executor to run the callback
     * @param callback           the callback notified when this endpoint receives a Payload
     * @throws IllegalStateException if the {@code receiverEndpointId} had a {@link PayloadCallback}
     *                               registered
     */
    // TODO(b/257118072): this method should save the callback like in CarRemoteDeviceManager.
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void registerReceiver(@NonNull String receiverEndpointId,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull PayloadCallback callback) {
        Objects.requireNonNull(receiverEndpointId, "receiverEndpointId cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        // TODO(b/257117236): implement this method.
    }

    /**
     * Unregisters the existing {@link PayloadCallback} for {@code receiverEndpointId}.
     * <p>
     * This method can be called after calling {@link #registerReceiver} once the receiver
     * endpoint no longer needs to receive Payload, or becomes inactive.
     * This method must be called before the receiver endpoint is destroyed. Failing to call this
     * method might cause the AbstractReceiverService to persist.
     *
     * @throws IllegalStateException if the {@code receiverEndpointId} had no {@link
     *                               PayloadCallback} registered
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void unregisterReceiver(@NonNull String receiverEndpointId) {
        Objects.requireNonNull(receiverEndpointId, "receiverEndpointId cannot be null");
        // TODO(b/257117236): implement this method.
    }

    /**
     * Sends a request to connect to the peer client on {@code receiverZone}. The {@link
     * AbstractReceiverService} in the peer client will be started and bound automatically if it
     * was not started yet.
     * <p>
     * This method should only be called when the state of the {@code receiverZone} is
     * {@link android.car.CarRemoteDeviceManager#FLAG_CLIENT_CONNECTION_READY} (and
     * {@link android.car.CarRemoteDeviceManager#FLAG_SCREEN_UNLOCKED} and {@link
     * android.car.CarRemoteDeviceManager#FLAG_CLIENT_IN_FOREGROUND} if UI is needed to
     * establish the connection). Otherwise, errors may occur.
     * <p>
     * The caller may call {@link #cancelConnection} to cancel the request.
     * <p>
     * The connection is one-way. In other words, the receiver can't send {@link Payload} to the
     * sender. If the receiver wants to send {@link Payload}, it must call this method to become
     * a sender.
     * <p>
     * The caller endpoint must call {@link #disconnect} before it is destroyed.
     *
     * @param receiverZone the occupant zone to connect to
     * @param executor     the Executor to run the callback
     * @param callback     the callback notified for the request result
     * @throws IllegalStateException if the {@code callback} was registered already
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void requestConnection(@NonNull OccupantZoneInfo receiverZone,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull ConnectionRequestCallback callback) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        Preconditions.checkState(!hasConnectionRequestCallback(callback),
                "The ConnectionRequestCallback was registered already");
        synchronized (mLock) {
            try {
                mService.requestConnection(mCurrentRequestId, receiverZone,
                        mBinderConnectionRequestCallback);
                mConnectionRequestMap.put(mCurrentRequestId, new Pair<>(callback, executor));
                mCurrentRequestId++;
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to request connection");
                handleRemoteExceptionFromCarService(e);
            }
        }
    }

    /**
     * Cancels the pending connection request to the peer client on {@code receiverZone}.
     * <p>
     * The caller endpoint may call this method when it has requested a connection, but hasn't
     * received any response for a long time, or the user wants to cancel the request explicitly.
     * In other words, this method should be called after {@link #requestConnection}, and before
     * any events in the {@link ConnectionRequestCallback} is triggered.
     *
     * @throws IllegalStateException if there was no pending connection request to {@code
     *                               receiverZone}
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void cancelConnection(@NonNull OccupantZoneInfo receiverZone) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        try {
            mService.cancelConnection(receiverZone);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to cancel connection");
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Sends the {@code payload} to the peer client on {@code receiverZone}.
     * <p>
     * Different sender endpoints in the same client app are treated as the same sender. If the
     * sender endpoints need to differentiate themselves, they can put the identity info into the
     * payload.
     *
     * @throws PayloadTransferException if the payload was not sent. For example, this method is
     *                                  called when the connection is not established or has been
     *                                  terminated, or an internal error occurred.
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void sendPayload(@NonNull OccupantZoneInfo receiverZone, @NonNull Payload payload)
            throws PayloadTransferException {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        try {
            mService.sendPayload(receiverZone, payload);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to send Payload");
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Disconnects from the peer client on {@code receiverZone}. No operation if it was not
     * connected to the peer client.
     * <p>
     * This method can be called as soon as the caller app no longer needs to send {@link Payload}
     * to {@code receiverZone}. If there are multiple sender endpoints in the client app reuse the
     * same connection, this method should be called when all sender endpoints no longer need to
     * send Payload to {@code receiverZone}.
     * <p>
     * This method must be called before the caller is destroyed. Failing to call this method might
     * cause the {@link AbstractReceiverService} in the peer client to persist.
     *
     * @throws IllegalStateException if it was not connected to {@code receiverZone}
     */
    @SuppressWarnings("[NotCloseable]")
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public void disconnect(@NonNull OccupantZoneInfo receiverZone) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        try {
            mService.disconnect(receiverZone);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to disconnect");
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * @return whether it is connected to its peer client on {@code receiverZone}.
     */
    @SuppressWarnings("[NotCloseable]")
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.UPSIDE_DOWN_CAKE_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.UPSIDE_DOWN_CAKE_0)
    @RequiresPermission(Car.PERMISSION_MANAGE_OCCUPANT_CONNECTION)
    public boolean isConnected(@NonNull OccupantZoneInfo receiverZone) {
        Objects.requireNonNull(receiverZone, "receiverZone cannot be null");
        try {
            return mService.isConnected(receiverZone);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get connection state");
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    private void onConnectionRequestCallbackInvoked(int callbackId, int requestId,
            OccupantZoneInfo receiverZone, int rejectionReason, int connectionError) {
        Pair<ConnectionRequestCallback, Executor> pair;
        synchronized (mLock) {
            pair = mConnectionRequestMap.get(requestId);
        }
        if (pair == null) {
            // This should never happen, but let's be cautious.
            Slog.e(TAG, "Failed to find the request " + requestId);
            return;
        }
        ConnectionRequestCallback callback = pair.first;
        Executor executor = pair.second;
        switch (callbackId) {
            case ICONNECTION_REQUEST_CALLBACK_ON_CONNECTED:
                executor.execute(() -> callback.onConnected(receiverZone));
                break;
            case ICONNECTION_REQUEST_CALLBACK_ON_REJECTED:
                executor.execute(() -> callback.onRejected(receiverZone, rejectionReason));
                break;
            case ICONNECTION_REQUEST_CALLBACK_ON_FAILED:
                executor.execute(() -> callback.onFailed(receiverZone, connectionError));
                break;
            case ICONNECTION_REQUEST_CALLBACK_ON_DISCONNECTED:
                executor.execute(() -> callback.onDisconnected(receiverZone));
                break;
            default:
                // Failing into this case means a bug in this class.
                throw new IllegalArgumentException("Invalid ConnectionRequestCallback ID");
        }
    }

    private boolean hasConnectionRequestCallback(@NonNull ConnectionRequestCallback callback) {
        synchronized (mLock) {
            for (int i = 0; i < mConnectionRequestMap.size(); i++) {
                ConnectionRequestCallback registeredCallback =
                        mConnectionRequestMap.valueAt(i).first;
                if (registeredCallback == callback) {
                    return true;
                }
            }
        }
        return false;
    }
}
