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
package org.apache.james.webadmin.dto;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.module.SimpleModule;

class QuotaLimitValueDeserializerTest {

    @Test
    void objectDeserializeShouldContainGivenValues() throws JsonExtractException {
        String payload = "{\"count\":52,\"size\":42}";
        ValidatedQuotaDTO actual = new JsonExtractor<>(ValidatedQuotaDTO.class,
            new SimpleModule()
                .addDeserializer(QuotaCountLimit.class, new QuotaValueDeserializer<>(QuotaCountLimit.unlimited(), QuotaCountLimit::count))
                .addDeserializer(QuotaSizeLimit.class, new QuotaValueDeserializer<>(QuotaSizeLimit.unlimited(), QuotaSizeLimit::size))
        ).parse(payload);
        Assertions.assertThat(actual)
            .isEqualTo(
                ValidatedQuotaDTO
                    .builder()
                    .count(java.util.Optional.of(QuotaCountLimit.count(52)))
                    .size(java.util.Optional.of(QuotaSizeLimit.size(42)))
                    .build());
    }

}