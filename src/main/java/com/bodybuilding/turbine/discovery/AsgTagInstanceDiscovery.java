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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.TagDescription;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.discovery.InstanceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class that encapsulates an {@link InstanceDiscovery} implementation that locates Amazon Auto Scaling Groups by Tag name.
 * The tag value is used as the turbine cluster name
 */
public class AsgTagInstanceDiscovery implements InstanceDiscovery {
    private static final Logger log = LoggerFactory.getLogger(AsgTagInstanceDiscovery.class);
    public static final String TAG_PROPERTY_NAME = "asgdiscovery.tag";

    private static final DynamicStringProperty CLUSTER_TAG_KEY = DynamicPropertyFactory.getInstance()
            .getStringProperty(TAG_PROPERTY_NAME, null);

    private final AmazonAutoScalingClient asgClient;
    private final AmazonEC2Client ec2Client;

    public AsgTagInstanceDiscovery() {
        this(new AmazonAutoScalingClient(), new AmazonEC2Client());
    }

    protected AsgTagInstanceDiscovery(AmazonAutoScalingClient asgClient, AmazonEC2Client ec2Client) {
        Preconditions.checkNotNull(asgClient);
        Preconditions.checkNotNull(ec2Client);
        Preconditions.checkState(!Strings.isNullOrEmpty(CLUSTER_TAG_KEY.get()), TAG_PROPERTY_NAME + " must be supplied!");
        this.asgClient = asgClient;
        this.ec2Client = ec2Client;

        String regionName = DynamicPropertyFactory.getInstance().getStringProperty("turbine.region", "us-east-1").get();
        Region region = Region.getRegion(Regions.fromName(regionName));
        ec2Client.setRegion(region);
        asgClient.setRegion(region);
        log.debug("Set the region to [{}]", region);
    }

    @Override
    public Collection<Instance> getInstanceList() throws Exception {
        try {
            Collection<Instance> instances = getInstanceListInternal();
            log.debug("Returning instances {}", instances);
            return instances;
        } catch (Exception e) {
            log.error("Error getting instances for Auto Scaling Groups with tag {}", CLUSTER_TAG_KEY.get(), e);
        }
        return Collections.emptyList();
    }

    private Collection<Instance> getInstanceListInternal() throws Exception {
        List<Instance> instanceList = new ArrayList<>();
        findAutoscalingGroups().stream()
                .map(this::getTurbineInstances)
                .forEach(instanceList::addAll);

        return instanceList;
    }

    /**
     * Convert from AWS ASG Instances to Turbine Instances
     *
     * @param asg
     * @return list of Turbine Instances (not AWS Instances)
     */
    private List<Instance> getTurbineInstances(AutoScalingGroup asg) {
        String clusterName = asg.getTags()
                .stream()
                .filter(t -> t.getKey().equals(CLUSTER_TAG_KEY.get()))
                .findAny()
                .get().getValue();

        List<com.amazonaws.services.autoscaling.model.Instance> awsInstances = asg.getInstances();

        Collection<String> instanceIds = awsInstances.stream()
                .map(com.amazonaws.services.autoscaling.model.Instance::getInstanceId)
                .collect(Collectors.toSet());

        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.withInstanceIds(instanceIds);

        DescribeInstancesResult describeInstancesResult = ec2Client.describeInstances(request);
        List<Reservation> reservations = describeInstancesResult.getReservations();
        List<Instance> turbineInstances = new ArrayList<>();

        // add all instances from each of the reservations - after converting to Turbine instance
        reservations.stream()
                .flatMap(r -> r.getInstances().stream())
                .filter(i -> !Strings.isNullOrEmpty(i.getPublicDnsName()))
                .map(i -> createTurbineInstance(clusterName, i))
                .forEach(turbineInstances::add);


        return turbineInstances;
    }

    /**
     * Maps a EC2 Instance to a Turbine instance.
     * @param clusterName name of the cluster for this instance
     * @param ec2Instance EC2 instance model
     * @return Turbine instance
     */
    protected Instance createTurbineInstance(String clusterName, com.amazonaws.services.ec2.model.Instance ec2Instance) {
        return Ec2InstanceMapper.getInstance().createTurbineInstance(clusterName, ec2Instance);
    }

    /**
     * Returns auto scaling groups that have the CLUSTER_TAG_KEY tag
     * @return collection of AutoScalingGroup that contain the CLUSTER_TAG_KEY
     */
    private Collection<AutoScalingGroup> findAutoscalingGroups() {
        String token = null;
        List<AutoScalingGroup> groupList = new ArrayList<>();
        do {
            DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
            request.setNextToken(token);
            DescribeAutoScalingGroupsResult result = asgClient.describeAutoScalingGroups(request);
            result.getAutoScalingGroups().stream()
                    .filter(a -> containsTag(a.getTags()))
                    .forEach(groupList::add);

            token = result.getNextToken();
        } while(!Strings.isNullOrEmpty(token));

        return groupList;
    }

    private boolean containsTag(Collection<TagDescription> tags) {
        return tags.stream().anyMatch(t -> t.getKey().equals(CLUSTER_TAG_KEY.get()));
    }
}
