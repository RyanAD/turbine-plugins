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

import com.google.common.collect.ImmutableSortedSet;
import com.netflix.turbine.data.TurbineData;
import com.netflix.turbine.discovery.Instance;
import com.netflix.turbine.discovery.InstanceObservable;
import com.netflix.turbine.monitor.cluster.ClusterMonitor;
import com.netflix.turbine.plugins.PluginsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Returns a sorted list of all currently tracked clusters
 */
public class ClusterListUtil {
    private static final Logger log = LoggerFactory.getLogger(ClusterListUtil.class);
    private ClusterListUtil() {
    }
    public static SortedSet<String> getClusterNames() {
        return ImmutableSortedSet.copyOf(InstanceObservable.getInstance().getCurrentHostsUp()
                .stream()
                .map(Instance::getCluster)
                .filter(c -> {
                    ClusterMonitor<? extends TurbineData> cm =
                            PluginsFactory.getClusterMonitorFactory().getClusterMonitor(c);

                    if (cm == null) {
                        log.info("ClusterMonitor does not know about cluster with name: {}", c);
                    }

                    return cm != null;
                })
                .collect(Collectors.toSet()));
    }
}
