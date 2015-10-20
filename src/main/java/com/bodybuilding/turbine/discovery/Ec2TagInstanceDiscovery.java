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
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
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

/**
 * Class that encapsulates an {@link InstanceDiscovery} implementation that locates Amazon EC2 Instances by Tag name.
 * The tag value is used as the turbine cluster name
 */
public class Ec2TagInstanceDiscovery implements InstanceDiscovery{
    private static final Logger log = LoggerFactory.getLogger(Ec2TagInstanceDiscovery.class);
    public static final String PROPERTY_NAME = "ec2discovery.tag";
    private static final DynamicStringProperty CLUSTER_TAG_KEY = DynamicPropertyFactory.getInstance()
            .getStringProperty(PROPERTY_NAME, null);

    private final AmazonEC2Client ec2Client;

    public Ec2TagInstanceDiscovery() {
        this(new AmazonEC2Client());
    }

    protected Ec2TagInstanceDiscovery(AmazonEC2Client ec2Client) {
        Preconditions.checkNotNull(ec2Client);
        this.ec2Client = ec2Client;

        Preconditions.checkState(!Strings.isNullOrEmpty(CLUSTER_TAG_KEY.get()), PROPERTY_NAME + " must be supplied!");
        String regionName = DynamicPropertyFactory.getInstance().getStringProperty("turbine.region", "us-east-1").get();
        ec2Client.setRegion(Region.getRegion(Regions.fromName(regionName)));
        log.debug("Set the ec2 region to [{}]", regionName);
    }

    @Override
    public Collection<Instance> getInstanceList() throws Exception {
        try {
            Collection<Instance> instances = getInstancesInternal();
            log.debug("Returning instances {}", instances);
            return instances;
        } catch (Exception e) {
            log.error("Failed to fetch ec2 instances with tag {}", CLUSTER_TAG_KEY.get(), e);
        }
        return Collections.emptyList();
    }

    private Collection<Instance> getInstancesInternal() {
        List<Filter> filterList = new ArrayList<>(1);
        filterList.add(new Filter("tag-key", Lists.newArrayList(CLUSTER_TAG_KEY.get())));


        String nextToken = null;
        Collection<Instance> instances = new ArrayList<>();
        do {
            DescribeInstancesRequest request = new DescribeInstancesRequest();
            request.setFilters(filterList);
            request.setNextToken(nextToken);
            DescribeInstancesResult results = ec2Client.describeInstances(request);
            nextToken = results.getNextToken();
            instances.addAll(processReservations(results.getReservations()));
        } while (!Strings.isNullOrEmpty(nextToken));

        return instances;
    }

    /**
     * Converts EC2 reservations to Turbine instance
     *
     * @param reservations
     * @return
     */
    private List<Instance> processReservations(List<Reservation> reservations) {
        List<Instance> instances = new ArrayList<>();

        // add all instances from each of the reservations - after converting to Turbine instance
        reservations.stream()
                .flatMap(r -> r.getInstances().stream())
                .forEach(ec2Instance -> {
                    String clusterName = ec2Instance.getTags().stream()
                            .filter(t -> t.getKey().equals(CLUSTER_TAG_KEY.get()))
                            .map(Tag::getValue)
                            .filter(s -> !Strings.isNullOrEmpty(s))
                            .findAny()
                            .orElse(null);

                    if (clusterName != null) {
                        instances.add(createTurbineInstance(clusterName, ec2Instance));
                    }
                });
        return instances;
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
}
