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
package org.apache.james.modules.server;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.apache.james.webadmin.dto.WebAdminReprocessingContextInformationDTO;
import org.apache.james.webadmin.dto.WebAdminSingleMailboxReindexingTaskAdditionalInformationDTO;
import org.apache.james.webadmin.dto.WebAdminUserReindexingTaskAdditionalInformationDTO;
import org.apache.mailbox.tools.indexer.MessageIdReindexingTaskAdditionalInformationDTO;
import org.apache.mailbox.tools.indexer.SingleMessageReindexingTaskAdditionalInformationDTO;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class WebAdminReIndexingTaskSerializationModule extends AbstractModule {
    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> errorRecoveryAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return WebAdminReprocessingContextInformationDTO.WebAdminErrorRecoveryIndexationDTO.serializationModule(mailboxIdFactory);
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> fullReindexAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return WebAdminReprocessingContextInformationDTO.WebAdminFullIndexationDTO.serializationModule(mailboxIdFactory);
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> messageIdReindexingAdditionalInformation(MessageId.Factory messageIdFactory) {
        return MessageIdReindexingTaskAdditionalInformationDTO.module(messageIdFactory);
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> singleMailboxReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return WebAdminSingleMailboxReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> singleMessageReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return SingleMessageReindexingTaskAdditionalInformationDTO.module(mailboxIdFactory);
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends  AdditionalInformationDTO> userReindexingAdditionalInformation(MailboxId.Factory mailboxIdFactory) {
        return WebAdminUserReindexingTaskAdditionalInformationDTO.serializationModule(mailboxIdFactory);
    }
}
