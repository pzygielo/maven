/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.cling.logging.impl;

import java.net.URL;
import java.util.Map;
import java.util.Set;

import org.apache.maven.cling.logging.BaseSlf4jConfiguration;

/**
 * Pseudo-configuration for unsupported SLF4J binding.
 *
 * @since 3.2.4
 */
public class UnsupportedSlf4jBindingConfiguration extends BaseSlf4jConfiguration {

    /**
     * @deprecated the arguments are ignored. Use the no-args constructor.
     */
    @Deprecated
    public UnsupportedSlf4jBindingConfiguration(String slf4jBinding, Map<URL, Set<Object>> supported) {}

    public UnsupportedSlf4jBindingConfiguration() {}

    @Override
    public void activate() {}
}
