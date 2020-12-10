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

package org.apache.james.webadmin.vault.routes;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.apache.james.vault.search.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class DeletedMessagesVaultExportTask implements Task {

    static final TaskType TYPE = TaskType.of("deleted-messages-export");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private final Username userExportFrom;
        private final MailAddress exportTo;
        private final long totalExportedMessages;
        private final Instant timestamp;

        public AdditionalInformation(Username userExportFrom, MailAddress exportTo, long totalExportedMessages, Instant timestamp) {
            this.userExportFrom = userExportFrom;
            this.exportTo = exportTo;
            this.totalExportedMessages = totalExportedMessages;
            this.timestamp = timestamp;
        }

        public String getUserExportFrom() {
            return userExportFrom.asString();
        }

        public String getExportTo() {
            return exportTo.asString();
        }

        public long getTotalExportedMessages() {
            return totalExportedMessages;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DeletedMessagesVaultExportTask.class);

    private final ExportService exportService;
    private final Username userExportFrom;
    @VisibleForTesting
    final Query exportQuery;
    private final MailAddress exportTo;
    private final AtomicLong totalExportedMessages;

    DeletedMessagesVaultExportTask(ExportService exportService, Username userExportFrom, Query exportQuery, MailAddress exportTo) {
        this.exportService = exportService;
        this.userExportFrom = userExportFrom;
        this.exportQuery = exportQuery;
        this.exportTo = exportTo;
        this.totalExportedMessages = new AtomicLong();
    }

    @Override
    public Result run() {
        try {
            Runnable messageToShareCallback = totalExportedMessages::incrementAndGet;
            exportService.export(userExportFrom, exportQuery, exportTo, messageToShareCallback);
            return Result.COMPLETED;
        } catch (IOException e) {
            LOGGER.error("Error happens when exporting deleted messages from {} to {}", userExportFrom.asString(), exportTo.asString());
            return Result.PARTIAL;
        }
    }

    @Override
    public TaskType type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(new AdditionalInformation(userExportFrom, exportTo, totalExportedMessages.get(), Clock.systemUTC().instant()));
    }

    Username getUserExportFrom() {
        return userExportFrom;
    }

    MailAddress getExportTo() {
        return exportTo;
    }
}
