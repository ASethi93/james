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

import java.time.Instant;
import java.util.List;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.indexer.ReIndexingExecutionFailures;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.mailbox.tools.indexer.RunningOptionsDTO;
import org.apache.mailbox.tools.indexer.UserReindexingTask;

import com.fasterxml.jackson.annotation.JsonCreator;

public class WebAdminUserReindexingTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<UserReindexingTask.AdditionalInformation, WebAdminUserReindexingTaskAdditionalInformationDTO> serializationModule(MailboxId.Factory factory) {
        return DTOModule.forDomainObject(UserReindexingTask.AdditionalInformation.class)
            .convertToDTO(WebAdminUserReindexingTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> {
                throw new NotImplementedException("Deserialization not implemented for this DTO");
            })
            .toDTOConverter((details, type) -> new WebAdminUserReindexingTaskAdditionalInformationDTO(
                type,
                details.getUsername(),
                RunningOptionsDTO.toDTO(details.getRunningOptions()),
                details.getSuccessfullyReprocessedMailCount(),
                details.getFailedReprocessedMailCount(),
                details.failures(),
                details.timestamp()))
            .typeName(UserReindexingTask.USER_RE_INDEXING.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final WebAdminReprocessingContextInformationDTO reprocessingContextInformationDTO;
    private final String username;

    @JsonCreator
    private WebAdminUserReindexingTaskAdditionalInformationDTO(String type,
                                                               String username,
                                                               RunningOptionsDTO runningOptions,
                                                               int successfullyReprocessedMailCount,
                                                               int failedReprocessedMailCount,
                                                               ReIndexingExecutionFailures failures,
                                                               Instant timestamp) {
        this.username = username;
        this.reprocessingContextInformationDTO = new WebAdminReprocessingContextInformationDTO(
            type,
            runningOptions,
            successfullyReprocessedMailCount,
            failedReprocessedMailCount, failures, timestamp);
    }

    @Override
    public String getType() {
        return reprocessingContextInformationDTO.getType();
    }

    public Instant getTimestamp() {
        return reprocessingContextInformationDTO.getTimestamp();
    }

    public String getUsername() {
        return username;
    }

    public RunningOptionsDTO getRunningOptions() {
        return reprocessingContextInformationDTO.getRunningOptions();
    }

    public int getSuccessfullyReprocessedMailCount() {
        return reprocessingContextInformationDTO.getSuccessfullyReprocessedMailCount();
    }

    public int getFailedReprocessedMailCount() {
        return reprocessingContextInformationDTO.getFailedReprocessedMailCount();
    }

    public SerializableReIndexingExecutionFailures getMessageFailures() {
        return reprocessingContextInformationDTO.getMessageFailures();
    }

    public List<String> getMailboxFailures() {
        return reprocessingContextInformationDTO.getMailboxFailures();
    }
}
