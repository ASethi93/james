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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.TestId;
import org.apache.mailbox.tools.indexer.UserReindexingTask;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.google.common.collect.ImmutableList;

class WebAdminUserReprocessingDTOTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");

    private final String serializedAdditionalInformation = "{" +
        "  \"type\":\"user-reindexing\",\"username\":\"bob\"," +
        "  \"runningOptions\":{\"messagesPerSecond\":50,\"mode\":\"REBUILD_ALL\"}," +
        "  \"successfullyReprocessedMailCount\":42," +
        "  \"failedReprocessedMailCount\":2," +
        "  \"messageFailures\":{\"1\":[{\"uid\":10}],\"2\":[{\"uid\":20}]}," +
        "  \"mailboxFailures\":[\"3\", \"4\"]," +
        "  \"timestamp\":\"2018-11-13T12:00:55Z\"}";

    private final TestId mailboxId = TestId.of(1L);
    private final MessageUid messageUid = MessageUid.of(10L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId, messageUid);
    private final TestId mailboxId2 = TestId.of(2L);
    private final MessageUid messageUid2 = MessageUid.of(20L);
    private final ReIndexingExecutionFailures.ReIndexingFailure indexingFailure2 = new ReIndexingExecutionFailures.ReIndexingFailure(mailboxId2, messageUid2);
    private final List<ReIndexingExecutionFailures.ReIndexingFailure> messageFailures = ImmutableList.of(indexingFailure, indexingFailure2);
    private final ImmutableList<MailboxId> mailboxFailures = ImmutableList.of(TestId.of(3), TestId.of(4));
    private final ReIndexingExecutionFailures executionFailures = new ReIndexingExecutionFailures(messageFailures, mailboxFailures);

    @Test
    void shouldSerializeAdditionalInformation() throws Exception {
        UserReindexingTask.AdditionalInformation domainObject =
            new UserReindexingTask.AdditionalInformation(
                Username.of("bob"),
                42,
                2,
                executionFailures,
                TIMESTAMP,
                ReIndexer.RunningOptions.DEFAULT);

        String json =
            JsonGenericSerializer.forModules(WebAdminUserReindexingTaskAdditionalInformationDTO
                .serializationModule(new TestId.Factory()))
                .withoutNestedType()
                .serialize(domainObject);

        assertThatJson(json)
            .isEqualTo(serializedAdditionalInformation);
    }

    @Test
    void deserializeShouldNotBeSupported() {
        assertThatThrownBy(() -> JsonGenericSerializer.forModules(WebAdminUserReindexingTaskAdditionalInformationDTO
            .serializationModule(new TestId.Factory()))
            .withoutNestedType()
            .deserialize(serializedAdditionalInformation))
            .isInstanceOf(InvalidDefinitionException.class);
    }
}
