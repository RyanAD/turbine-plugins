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

import com.google.common.base.Strings;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.turbine.discovery.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps an EC2 Instance object to a Turbine Instance object
 */
class Ec2InstanceMapper {
    private static final Logger log = LoggerFactory.getLogger(Ec2InstanceMapper.class);
    private static final Ec2InstanceMapper INSTANCE = new Ec2InstanceMapper();
    public static final String HOST_FIELD_PROPERTY_NAME = "ec2.hostField";
    private static final DynamicStringProperty DEFAULT_HOST_FIELD = DynamicPropertyFactory.getInstance().getStringProperty(HOST_FIELD_PROPERTY_NAME, "private_ip");

    private Ec2InstanceMapper() {
    }

    public static Ec2InstanceMapper getInstance() {
        return INSTANCE;
    }

    /**
     * Maps a EC2 Instance to a Turbine instance.
     * @param clusterName name of the cluster for this instance
     * @param ec2Instance EC2 instance model
     * @return Turbine instance
     */
    public Instance createTurbineInstance(String clusterName, com.amazonaws.services.ec2.model.Instance ec2Instance) {
        String hostField = DynamicPropertyFactory.getInstance().getStringProperty("ec2.hostField." + clusterName, null).get();
        if(Strings.isNullOrEmpty(hostField)) {
            hostField = DEFAULT_HOST_FIELD.get();
        }

        hostField = Strings.nullToEmpty(hostField);

        String host;
        if(hostField.equalsIgnoreCase("private_dns")) {
            host = ec2Instance.getPrivateDnsName();
        } else if(hostField.equalsIgnoreCase("private_ip")) {
            host = ec2Instance.getPrivateIpAddress();
        } else if(hostField.equalsIgnoreCase("public_dns")) {
            host = ec2Instance.getPublicDnsName();
        } else if(hostField.equalsIgnoreCase("public_ip")) {
            host = ec2Instance.getPublicIpAddress();
        } else {
            log.warn("{} is not a valid value for property {} it should be one of " +
                    "[private_dns, private_ip, public_dns, public_ip]. Falling back to private_ip", hostField, "ec2.hostField");
            host = ec2Instance.getPrivateIpAddress();
        }

        return new Instance(host, clusterName, ec2Instance.getState().getName().equals("running"));
    }
}
