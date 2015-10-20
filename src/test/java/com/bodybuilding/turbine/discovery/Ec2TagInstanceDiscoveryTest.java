/*
 * Copyright (C) 2015 Bodybuilding.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bodybuilding.turbine.discovery;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.collect.Lists;
import com.netflix.config.ConfigurationManager;
import com.netflix.turbine.discovery.Instance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.bodybuilding.turbine.discovery.Ec2TagInstanceDiscovery.PROPERTY_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class Ec2TagInstanceDiscoveryTest {
    @Mock
    private AmazonEC2Client ec2Client;

    @Before
    public void setup() {
        ConfigurationManager.getConfigInstance().addProperty(PROPERTY_NAME, "Tag");
    }

    @Test(expected = IllegalStateException.class)
    public void requiresTagProperty() {
        ConfigurationManager.getConfigInstance().addProperty(PROPERTY_NAME, "");
        new Ec2TagInstanceDiscovery();
    }

    @Test
    public void testGetInstances_emptyList() throws Exception {
        Ec2TagInstanceDiscovery discovery = new Ec2TagInstanceDiscovery(ec2Client);
        when(ec2Client.describeInstances(anyObject())).thenReturn(new DescribeInstancesResult());
        Collection<Instance> instanceList = discovery.getInstanceList();
        assertNotNull(instanceList);
        assertEquals(0, instanceList.size());
    }

    @Test
    public void testGetInstances() throws Exception {
        Ec2TagInstanceDiscovery discovery = new Ec2TagInstanceDiscovery(ec2Client);
        DescribeInstancesResult result = Mockito.mock(DescribeInstancesResult.class);

        List<Reservation> reservations = Lists.newArrayList(createReservationMock(), createReservationMock());
        when(result.getReservations()).thenReturn(reservations);
        when(ec2Client.describeInstances(anyObject())).thenReturn(result);

        Collection<Instance> instanceList = discovery.getInstanceList();
        assertNotNull(instanceList);
        assertEquals(4, instanceList.size());
        assertEquals(2, instanceList.stream().map(Instance::getCluster).distinct().count());
    }

    @Test
    public void testGetInstances_awsException() throws Exception {
        Ec2TagInstanceDiscovery discovery = new Ec2TagInstanceDiscovery(ec2Client);
        when(ec2Client.describeInstances(anyObject())).thenThrow(new AmazonClientException("Unit Test Intentional Exception"));
        Collection<Instance> instanceList = discovery.getInstanceList();
        assertNotNull(instanceList);
        assertEquals(0, instanceList.size());
    }

    private static Reservation createReservationMock() {
        ArrayList<com.amazonaws.services.ec2.model.Instance> instances =
                Lists.newArrayList(createInstanceMock("cluster1"), createInstanceMock("cluster2"));
        Reservation reservation = mock(Reservation.class);
        when(reservation.getInstances()).thenReturn(instances);
        return reservation;
    }

    private static com.amazonaws.services.ec2.model.Instance createInstanceMock(String tagVal) {
        return new com.amazonaws.services.ec2.model.Instance()
                .withPublicDnsName("www.public.com")
                .withState(new InstanceState().withName("running"))
                .withTags(new Tag("Tag", tagVal));
    }
}