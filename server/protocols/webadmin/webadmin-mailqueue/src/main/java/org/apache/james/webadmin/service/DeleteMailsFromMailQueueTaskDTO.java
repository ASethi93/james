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

package org.apache.james.webadmin.service;

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.json.DTOModule;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;

public class DeleteMailsFromMailQueueTaskDTO implements TaskDTO {

    public static TaskDTOModule<DeleteMailsFromMailQueueTask, DeleteMailsFromMailQueueTaskDTO> module(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return DTOModule
            .forDomainObject(DeleteMailsFromMailQueueTask.class)
            .convertToDTO(DeleteMailsFromMailQueueTaskDTO.class)
            .toDomainObjectConverter(dto -> dto.fromDTO(mailQueueFactory))
            .toDTOConverter(DeleteMailsFromMailQueueTaskDTO::toDTO)
            .typeName(DeleteMailsFromMailQueueTask.TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    public static DeleteMailsFromMailQueueTaskDTO toDTO(DeleteMailsFromMailQueueTask domainObject, String typeName) {
        return new DeleteMailsFromMailQueueTaskDTO(
            typeName,
            domainObject.getQueueName().asString(),
            domainObject.getMaybeSender().map(MailAddress::asString),
            domainObject.getMaybeName(),
            domainObject.getMaybeRecipient().map(MailAddress::asString)
        );
    }

    private final String type;
    private final String queueName;
    private final Optional<String> sender;
    private final Optional<String> name;
    private final Optional<String> recipient;

    public DeleteMailsFromMailQueueTaskDTO(@JsonProperty("type") String type,
                                           @JsonProperty("queue") String queueName,
                                           @JsonProperty("sender") Optional<String> sender,
                                           @JsonProperty("name") Optional<String> name,
                                           @JsonProperty("recipient") Optional<String> recipient) {
        this.type = type;
        this.queueName = queueName;
        this.sender = sender;
        this.name = name;
        this.recipient = recipient;
    }

    public DeleteMailsFromMailQueueTask fromDTO(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory) {
        return new DeleteMailsFromMailQueueTask(
            MailQueueName.of(queueName), name -> mailQueueFactory.getQueue(name).orElseThrow(() -> new DeleteMailsFromMailQueueTask.UnknownSerializedQueue(queueName)),
            sender.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow()),
            name,
            recipient.map(Throwing.<String, MailAddress>function(MailAddress::new).sneakyThrow())
        );
    }

    @Override
    public String getType() {
        return type;
    }

    public String getQueue() {
        return queueName;
    }

    public Optional<String> getSender() {
        return sender;
    }

    public Optional<String> getName() {
        return name;
    }

    public Optional<String> getRecipient() {
        return recipient;
    }
}