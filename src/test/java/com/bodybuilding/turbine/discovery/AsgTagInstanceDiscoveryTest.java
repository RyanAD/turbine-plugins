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
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.netflix.config.ConfigurationManager;
import com.netflix.turbine.discovery.Instance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bodybuilding.turbine.discovery.AsgTagInstanceDiscovery.TAG_PROPERTY_NAME;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AsgTagInstanceDiscoveryTest {
    private static final String TAG_KEY = "Tag";
    @Mock
    private AmazonEC2Client ec2Client;
    @Mock
    private AmazonAutoScalingClient asgClient;

    @Before
    public void setup() {
        ConfigurationManager.getConfigInstance().addProperty(TAG_PROPERTY_NAME, TAG_KEY);
    }

    @Test(expected = IllegalStateException.class)
    public void requiresTagProperty() {
        ConfigurationManager.getConfigInstance().addProperty(TAG_PROPERTY_NAME, "");
        new AsgTagInstanceDiscovery();
    }

    @Test
    public void getInstances_emptyList() throws Exception {
        when(asgClient.describeAutoScalingGroups(anyObject())).thenReturn(new DescribeAutoScalingGroupsResult());

        AsgTagInstanceDiscovery discovery = new AsgTagInstanceDiscovery(asgClient, ec2Client);
        Collection<Instance> instanceList = discovery.getInstanceList();
        assertNotNull(instanceList);
        assertTrue(instanceList.isEmpty());
    }

    @Test
    public void getInstances() throws Exception {
        AutoScalingGroup groupWithTag1 = new AutoScalingGroup()
                .withTags(new TagDescription().withKey(TAG_KEY).withValue("Cluster1"))
                .withInstances(createMockInstance("id1"), createMockInstance("id2"));

        AutoScalingGroup groupWithTag2 = new AutoScalingGroup()
                .withTags(new TagDescription().withKey(TAG_KEY).withValue("Cluster2"))
                .withInstances(createMockInstance("id3"), createMockInstance("id4"));

        AutoScalingGroup groupWithoutTag = new AutoScalingGroup()
                .withTags(new TagDescription().withKey("WrongTag").withValue("Cluster3"))
                .withInstances(createMockInstance("id5"), createMockInstance("id6"));

        Set<String> badIds = Sets.newHashSet("id5", "id6");

        DescribeAutoScalingGroupsResult result = new DescribeAutoScalingGroupsResult();
        result.setAutoScalingGroups(Lists.newArrayList(groupWithTag1, groupWithTag2, groupWithoutTag));
        when(asgClient.describeAutoScalingGroups(anyObject())).thenReturn(result);

        // mock the ec2 client request to get instance details
        when(ec2Client.describeInstances(any(DescribeInstancesRequest.class))).thenAnswer(m -> {
            DescribeInstancesRequest req = m.getArgumentAt(0, DescribeInstancesRequest.class);
            // make sure id5 and id6 didnt get requested. They dont have the right tag
            assertTrue(Sets.intersection(Sets.newHashSet(req.getInstanceIds()), badIds).isEmpty());
            List<com.amazonaws.services.ec2.model.Instance> ec2Instances = req.getInstanceIds().stream()
                    .map(id -> new com.amazonaws.services.ec2.model.Instance()
                            .withInstanceId(id)
                            .withTags(new Tag(TAG_KEY, "Unused"))
                            .withState(new InstanceState().withName("running"))
                            .withPublicDnsName("www.public.com"))
                    .collect(Collectors.toList());

            return new DescribeInstancesResult().withReservations(new Reservation().withInstances(ec2Instances));
        });

        Collection<Instance> instanceList = new AsgTagInstanceDiscovery(asgClient, ec2Client).getInstanceList();
        assertNotNull(instanceList);
        assertEquals(4, instanceList.size());
        Set<String> validClusters = Sets.newHashSet("Cluster1", "Cluster2");
        for (Instance i : instanceList) {
            assertTrue(validClusters.contains(i.getCluster()));
        }
    }

    @Test
    public void testGetInstances_awsException() throws Exception {
        when(asgClient.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class))).thenThrow(new AmazonClientException("Unit Test Intentional Exception"));
        Collection<Instance> instanceList = new AsgTagInstanceDiscovery(asgClient, ec2Client).getInstanceList();
        assertNotNull(instanceList);
        assertTrue(instanceList.isEmpty());
    }



    private static com.amazonaws.services.autoscaling.model.Instance createMockInstance(String id) {
        return new com.amazonaws.services.autoscaling.model.Instance().withInstanceId(id);
    }
}