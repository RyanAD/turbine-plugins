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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class ServletMappingUtilTest {

    @Mock
    ServletContext sc;

    @Mock
    ServletRegistration registration;

    @Before
    public void setup() {
        Map<String, ServletRegistration> registrations = Maps.newHashMap();
        List<String> mappingList = Lists.newArrayList("/testing1", "/testing2");
        registrations.put("turbineStreamServlet", registration);

        when(registration.getMappings()).thenReturn(mappingList);
        when(sc.getServletRegistrations()).thenAnswer(i -> registrations);
    }

    @Test
    public void testFindServletMapping() {
        Optional<Collection<String>> mappings = ServletMappingUtil.findServletMapping(sc, "turbinestreamservlet");
        assertNotNull(mappings);
        assertTrue(mappings.isPresent());
        assertEquals(Lists.newArrayList("/testing1", "/testing2"), mappings.get());
    }

    @Test
    public void testFindServletMapping_caseSensitive() {
        Optional<Collection<String>> mappings = ServletMappingUtil.findServletMapping(sc, "turbinestreamservlet", true);
        assertNotNull(mappings);
        assertFalse(mappings.isPresent());

        mappings = ServletMappingUtil.findServletMapping(sc, "turbineStreamServlet", true);
        assertNotNull(mappings);
        assertTrue(mappings.isPresent());
        assertEquals(Lists.newArrayList("/testing1", "/testing2"), mappings.get());
    }
}