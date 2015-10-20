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

import com.google.common.collect.Lists;
import com.netflix.config.ConfigurationManager;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.discovery.InstanceDiscovery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CompositeInstanceDiscoveryTest {

    @Mock
    InstanceDiscovery discovery1;

    @Mock
    InstanceDiscovery discovery2;

    @Mock
    InstanceDiscovery exceptionThrowingDiscovery;

    @Before
    public void setup() throws Exception {
        when(discovery1.getInstanceList()).thenReturn(Lists.newArrayList(
                new Instance("host1", "discovery1", true),
                new Instance("host2", "discovery1", true),
                new Instance("host3", "discovery1", true)
                ));

        when(discovery2.getInstanceList()).thenReturn(Lists.newArrayList(
                new Instance("host1", "discovery2", true),
                new Instance("host2", "discovery2", true),
                new Instance("host3", "discovery2", true)
        ));

        when(exceptionThrowingDiscovery.getInstanceList()).thenThrow(new RuntimeException("Error getting instances"));
    }

    @Test
    public void testGetInstanceList_oneDelegate() throws Exception {
        CompositeInstanceDiscovery discovery = new CompositeInstanceDiscovery(Lists.newArrayList(discovery1));
        Collection<Instance> instanceList = discovery.getInstanceList();

        assertEquals(3, instanceList.size());
    }

    @Test
    public void testGetInstanceList_multipleDelegate() throws Exception {
        CompositeInstanceDiscovery discovery = new CompositeInstanceDiscovery(Lists.newArrayList(discovery1, discovery2));
        Collection<Instance> instanceList = discovery.getInstanceList();

        assertEquals(6, instanceList.size());
        assertEquals(2, instanceList.stream().map(Instance::getCluster).collect(Collectors.toSet()).size());
    }

    @Test
    public void testGetInstanceList_multipleDelegateAndException() throws Exception {
        CompositeInstanceDiscovery discovery = new CompositeInstanceDiscovery(Lists.newArrayList(discovery1, discovery2,
                exceptionThrowingDiscovery));
        Collection<Instance> instanceList = discovery.getInstanceList();

        assertEquals(6, instanceList.size());
        assertEquals(2, instanceList.stream().map(Instance::getCluster).collect(Collectors.toSet()).size());
    }

    @Test(expected = IllegalStateException.class)
    public void testGetInstanceList_noDelegates() throws Exception {
        CompositeInstanceDiscovery discovery = new CompositeInstanceDiscovery(Lists.newArrayList());
    }

    @Test
    public void testLoadClass() throws Exception {
        ConfigurationManager.getConfigInstance().addProperty("CompositeInstanceDiscovery.delegates", NoOpInstanceDiscovery.class.getName());
        Collection<Instance> instanceList = new CompositeInstanceDiscovery().getInstanceList();
        assertNotNull(instanceList);
        assertEquals(1, NoOpInstanceDiscovery.instances.get());
    }

    public static class NoOpInstanceDiscovery implements InstanceDiscovery {
        static AtomicInteger instances = new AtomicInteger();

        public NoOpInstanceDiscovery() {
            instances.incrementAndGet();
        }

        @Override
        public Collection<Instance> getInstanceList() throws Exception {
            return Collections.emptyList();
        }
    }
}