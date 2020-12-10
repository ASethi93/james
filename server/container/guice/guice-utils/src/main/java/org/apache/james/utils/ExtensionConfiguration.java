/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class ExtensionConfiguration {
    public static final ExtensionConfiguration DEFAULT = new ExtensionConfiguration(ImmutableList.of());

    public static ExtensionConfiguration from(Configuration configuration) {
        List<String> list = Optional.ofNullable(configuration.getStringArray("guice.extension.module"))
            .map(Arrays::asList)
            .orElse(ImmutableList.of());

        return new ExtensionConfiguration(list.stream()
            .map(ClassName::new)
            .collect(Guavate.toImmutableList()));
    }

    private final List<ClassName> additionalGuiceModulesForExtensions;

    public ExtensionConfiguration(List<ClassName> additionalGuiceModulesForExtensions) {
        this.additionalGuiceModulesForExtensions = additionalGuiceModulesForExtensions;
    }

    public List<ClassName> getAdditionalGuiceModulesForExtensions() {
        return additionalGuiceModulesForExtensions;
    }
}
