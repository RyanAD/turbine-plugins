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

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.netflix.config.ConfigurationManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static com.bodybuilding.turbine.discovery.Ec2InstanceMapper.HOST_FIELD_PROPERTY_NAME;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class Ec2InstanceMapperTest {
    private Ec2InstanceMapper mapper = Ec2InstanceMapper.getInstance();


    @Before
    public void setup() {
        ConfigurationManager.getConfigInstance().addProperty(HOST_FIELD_PROPERTY_NAME, "");
    }

    @Test
    public void createTurbineInstance_default() {
        Instance instance = createEc2Instance();
        com.netflix.turbine.discovery.Instance turbineInstance = mapper.createTurbineInstance("test_cluster1", instance);
        assertNotNull(turbineInstance);
        assertEquals("test_cluster1", turbineInstance.getCluster());
        assertEquals("private_ip", turbineInstance.getHostname());
        assertTrue(turbineInstance.isUp());
    }

    @Test
    public void createTurbineInstance_privateDns() {
        ConfigurationManager.getConfigInstance().addProperty(HOST_FIELD_PROPERTY_NAME, "private_dns");
        Instance instance = createEc2Instance();
        com.netflix.turbine.discovery.Instance turbineInstance = mapper.createTurbineInstance("test_cluster1", instance);
        assertNotNull(turbineInstance);
        assertEquals("test_cluster1", turbineInstance.getCluster());
        assertEquals("private_dns", turbineInstance.getHostname());
        assertTrue(turbineInstance.isUp());
    }

    @Test
    public void createTurbineInstance_publicDns() {
        ConfigurationManager.getConfigInstance().addProperty(HOST_FIELD_PROPERTY_NAME, "public_dns");
        Instance instance = createEc2Instance();
        com.netflix.turbine.discovery.Instance turbineInstance = mapper.createTurbineInstance("test_cluster1", instance);
        assertNotNull(turbineInstance);
        assertEquals("test_cluster1", turbineInstance.getCluster());
        assertEquals("public_dns", turbineInstance.getHostname());
        assertTrue(turbineInstance.isUp());
    }

    @Test
    public void createTurbineInstance_publicIp() {
        ConfigurationManager.getConfigInstance().addProperty(HOST_FIELD_PROPERTY_NAME, "public_ip");
        Instance instance = createEc2Instance();
        com.netflix.turbine.discovery.Instance turbineInstance = mapper.createTurbineInstance("test_cluster1", instance);
        assertNotNull(turbineInstance);
        assertEquals("test_cluster1", turbineInstance.getCluster());
        assertEquals("public_ip", turbineInstance.getHostname());
        assertTrue(turbineInstance.isUp());
    }

    @Test
    public void createTurbineInstance_down() {
        ConfigurationManager.getConfigInstance().addProperty(HOST_FIELD_PROPERTY_NAME, "public_ip");
        Instance instance = createEc2Instance();
        instance.setState(new InstanceState().withName("stopped"));
        com.netflix.turbine.discovery.Instance turbineInstance = mapper.createTurbineInstance("test_cluster1", instance);
        assertNotNull(turbineInstance);
        assertEquals("test_cluster1", turbineInstance.getCluster());
        assertEquals("public_ip", turbineInstance.getHostname());
        assertFalse(turbineInstance.isUp());
    }

    private Instance createEc2Instance() {
        return new Instance().withPrivateDnsName("private_dns")
                .withPrivateIpAddress("private_ip")
                .withPublicDnsName("public_dns")
                .withPublicIpAddress("public_ip")
                .withState(new InstanceState().withName("running"));
    }
}