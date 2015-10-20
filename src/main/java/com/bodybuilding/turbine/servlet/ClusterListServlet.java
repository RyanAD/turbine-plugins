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

package com.bodybuilding.turbine.servlet;

import com.bodybuilding.turbine.discovery.ClusterListUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.turbine.data.TurbineData;
import com.netflix.turbine.monitor.cluster.ClusterMonitor;
import com.netflix.turbine.monitor.cluster.ClusterMonitorFactory;
import com.netflix.turbine.plugins.PluginsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Returns the list of currently tracked cluster names as a json list
 */
public class ClusterListServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(ClusterListServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DynamicStringProperty DASHBOARD_URL = DynamicPropertyFactory.getInstance()
            .getStringProperty("hystrix.dashboard.url", null);

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");

            String servletPathRegex = Pattern.quote(request.getServletPath());
            Set<String> clusterNames = ClusterListUtil.getClusterNames();
            Optional<String> dashboardUrl = getDashboardUrl(getServletContext(), request);
            String turbinePath = getTurbineMapping(getServletContext());
            String turbineBaseUrl = request.getRequestURL().toString().replaceFirst(servletPathRegex, turbinePath + "?cluster=");
            log.debug("Using turbine URL: {}", turbineBaseUrl);
            log.debug("Using dashboard URL: {}", dashboardUrl);
            ClusterMonitorFactory<?> clusterMonitorFactory = PluginsFactory.getClusterMonitorFactory();
            List<ClusterInfo> clusters = clusterNames.stream()
                    .filter(c -> {
                        ClusterMonitor<? extends TurbineData> m = clusterMonitorFactory.getClusterMonitor(c);
                        if(m == null) {
                            log.debug("Cluster {} does not have a ClusterMonitor", c);
                        }
                        return m != null;
                    })
                    .map(c -> {
                        String turbineUrl = turbineBaseUrl + encodeUrl(c);
                        if (dashboardUrl.isPresent()) {
                            String link = dashboardUrl.get() + encodeUrl(turbineBaseUrl + c) + "&title=" + encodeUrl(c);
                            return new ClusterInfo(c, turbineUrl, link);
                        } else {
                            return new ClusterInfo(c, turbineUrl);
                        }
                    }).collect(Collectors.toList());

            response.setHeader("Content-Type", "application/json;charset=UTF-8");
            OBJECT_MAPPER.writeValue(response.getOutputStream(), clusters);
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("Error returning list of clusters", e);
        }
    }

    private String encodeUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the Turbine servlet mapping.
     * @param sc ServletContext
     * @return
     */
    private String getTurbineMapping(ServletContext sc) {
        Optional<String> mapping = ServletMappingUtil.findServletMapping(sc, "turbinestreamservlet").filter(s -> !s.isEmpty())
                .map(s -> s.stream().findFirst().get());

        if(!mapping.isPresent()) {
            throw new RuntimeException("Could not find servlet registered with name turbinestreamservlet");
        }

        return mapping.get();
    }

    /**
     * Returns Hystrix Dashboard URL
     * @param sc ServletContext
     * @param request HttpServletRequest for building full URL
     * @return
     */
    private Optional<String> getDashboardUrl(ServletContext sc, HttpServletRequest request) {
        String dashboardUrl = DASHBOARD_URL.get();
        if(dashboardUrl == null) {
            dashboardUrl = sc.getInitParameter("hystrix.dashboard.url");
        }

        if(dashboardUrl == null) {
            dashboardUrl = "/monitor/monitor.html?stream=";
        }

        if(!dashboardUrl.startsWith("http://") && !dashboardUrl.startsWith("https://")) {
            if(!dashboardUrl.startsWith("/")) {
                dashboardUrl = "/" + dashboardUrl;
            }
            dashboardUrl = request.getRequestURL().toString().replaceFirst(Pattern.quote(request.getServletPath()),
                    dashboardUrl);
        }
        return Optional.ofNullable(dashboardUrl);
    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class ClusterInfo {
        @JsonProperty(required = true)
        private String name;
        @JsonProperty
        private String link;
        @JsonProperty
        private String turbineStream;

        public ClusterInfo(String name, String turbineStream) {
            this.name = name;
            this.turbineStream = turbineStream;
        }

        public ClusterInfo(String name, String turbineStream, String link) {
            this.name = name;
            this.link = link;
            this.turbineStream = turbineStream;
        }
    }
}
