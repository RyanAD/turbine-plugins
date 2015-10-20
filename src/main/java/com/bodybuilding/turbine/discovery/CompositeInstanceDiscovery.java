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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.discovery.InstanceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Instance discovery that is composed of other InstanceDiscovery implementations
 */
public class CompositeInstanceDiscovery implements InstanceDiscovery {
    private static final Logger log = LoggerFactory.getLogger(CompositeInstanceDiscovery.class);
    private static final Splitter SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();
    private static final DynamicStringProperty DELEGATES_PROP = DynamicPropertyFactory.getInstance()
            .getStringProperty("CompositeInstanceDiscovery.delegates", "com.netflix.turbine.discovery.ConfigPropertyBasedDiscovery");

    private final Collection<InstanceDiscovery> delegates;

    public CompositeInstanceDiscovery(Collection<InstanceDiscovery> delegates) {
        Preconditions.checkNotNull(delegates);
        Preconditions.checkState(!delegates.isEmpty(), "No delegates could be loaded");
        this.delegates = delegates;
    }

    public CompositeInstanceDiscovery() {
        delegates = new ArrayList<>();
        Iterable<String> classes = SPLITTER.split(DELEGATES_PROP.get());
        for (String c : classes) {
            loadClass(c).ifPresent(delegates::add);
        }
        Preconditions.checkState(!delegates.isEmpty(), "No delegates could be loaded");
    }

    private Optional<InstanceDiscovery> loadClass(String className) {
        InstanceDiscovery instance = null;
        try {
            Class clazz = Class.forName(className);
            instance = (InstanceDiscovery) clazz.newInstance();
        } catch (Exception e) {
            log.error("Could not load InstanceDiscovery impl class {}", className, e);
        }

        return Optional.ofNullable(instance);
    }

    @Override
    public Collection<Instance> getInstanceList() throws Exception {
        return delegates.stream().flatMap(d -> {
            try {
                return d.getInstanceList().stream();
            } catch (Exception e) {
                log.error("Exception loading instances from {}", d.getClass(), e);
                return Stream.empty();
            }
        }).collect(Collectors.<Instance>toList());
    }
}
