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

import javax.servlet.ServletContext;
import java.util.Collection;
import java.util.Optional;

/**
 * Utility class to find servlet mappings by servlet name
 */
public class ServletMappingUtil {

    private ServletMappingUtil() {
    }

    /**
     * Returns collection of mappings for the given servlet name (case insensitive)
     * @param sc ServletContext for getting list of mappings
     * @param name servlet name to look for
     * @return Collection of mappings if found
     */
    public static Optional<Collection<String>> findServletMapping(ServletContext sc, String name) {
        return findServletMapping(sc, name, false);
    }

    /**
     * Returns collection of mappings for the given servlet name (case insensitive)
     * @param sc ServletContext for getting list of mappings
     * @param name servlet name to look for
     * @param caseSensitive should the servlet name be matched with case-sensitivity
     * @return Collection of mappings if found
     */
    public static Optional<Collection<String>> findServletMapping(ServletContext sc, String name, boolean caseSensitive) {
        return sc.getServletRegistrations().entrySet()
                .stream()
                .filter(e -> caseSensitive ? e.getKey().equals(name) : e.getKey().equalsIgnoreCase(name))
                .map(e -> e.getValue().getMappings())
                .findFirst();
    }
}
