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

package com.android.car.telemetry.databroker;

import static com.google.common.truth.Truth.assertThat;

import com.android.car.telemetry.TelemetryProto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DataBrokerUnitTest {
    private final DataBrokerImpl mDataBroker = new DataBrokerImpl();
    private static final TelemetryProto.VehiclePropertyPublisher
            VEHICLE_PROPERTY_PUBLISHER_CONFIGURATION =
            TelemetryProto.VehiclePropertyPublisher.newBuilder().setReadRate(
                    1).setVehiclePropertyId(1000).build();
    private static final TelemetryProto.Publisher PUBLISHER_CONFIGURATION =
            TelemetryProto.Publisher.newBuilder().setVehicleProperty(
                    VEHICLE_PROPERTY_PUBLISHER_CONFIGURATION).build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_FOO =
            TelemetryProto.Subscriber.newBuilder().setHandler("function_name_foo").setPublisher(
                    PUBLISHER_CONFIGURATION).build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_FOO =
            TelemetryProto.MetricsConfig.newBuilder().setName("Foo").setVersion(
                    1).addSubscribers(SUBSCRIBER_FOO).build();
    private static final TelemetryProto.Subscriber SUBSCRIBER_BAR =
            TelemetryProto.Subscriber.newBuilder().setHandler("function_name_bar").setPublisher(
                    PUBLISHER_CONFIGURATION).build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_BAR =
            TelemetryProto.MetricsConfig.newBuilder().setName("Bar").setVersion(
                    1).addSubscribers(SUBSCRIBER_BAR).build();

    @Test
    public void testAddMetricsConfiguration_newMetricsConfig() {
        mDataBroker.addMetricsConfiguration(METRICS_CONFIG_FOO);

        assertThat(mDataBroker.getPublisherMap().containsKey(
                TelemetryProto.Publisher.PublisherCase.VEHICLE_PROPERTY)).isTrue();
        assertThat(mDataBroker.getSubscriptionMap()).containsKey(METRICS_CONFIG_FOO.getName());
        // there should be one data subscriber in the subscription list of METRICS_CONFIG_FOO
        assertThat(mDataBroker.getSubscriptionMap().get(METRICS_CONFIG_FOO.getName())).hasSize(1);
    }

    @Test
    public void testAddMetricsConfiguration_multipleMetricsConfigsSamePublisher() {
        mDataBroker.addMetricsConfiguration(METRICS_CONFIG_FOO);
        mDataBroker.addMetricsConfiguration(METRICS_CONFIG_BAR);

        assertThat(mDataBroker.getPublisherMap()).hasSize(1);
        assertThat(mDataBroker.getSubscriptionMap()).containsKey(METRICS_CONFIG_FOO.getName());
        assertThat(mDataBroker.getSubscriptionMap()).containsKey(METRICS_CONFIG_BAR.getName());
    }

    @Test
    public void testAddMetricsConfiguration_addSameMetricsConfigs() {
        mDataBroker.addMetricsConfiguration(METRICS_CONFIG_FOO);

        boolean status = mDataBroker.addMetricsConfiguration(METRICS_CONFIG_FOO);

        assertThat(status).isFalse();
    }

    @Test
    public void testRemoveMetricsConfiguration_publisherShouldExist() {
        mDataBroker.addMetricsConfiguration(METRICS_CONFIG_FOO);

        mDataBroker.removeMetricsConfiguration(METRICS_CONFIG_FOO);

        assertThat(mDataBroker.getPublisherMap()).containsKey(
                TelemetryProto.Publisher.PublisherCase.VEHICLE_PROPERTY);
        assertThat(mDataBroker.getSubscriptionMap()).doesNotContainKey(
                METRICS_CONFIG_FOO.getName());
    }

    @Test
    public void testRemoveMetricsConfiguration_removeNonexistentMetricsConfig() {
        boolean status = mDataBroker.removeMetricsConfiguration(METRICS_CONFIG_FOO);

        assertThat(status).isFalse();
    }
}
