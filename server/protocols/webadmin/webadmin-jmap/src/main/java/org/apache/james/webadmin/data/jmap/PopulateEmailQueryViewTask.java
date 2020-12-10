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

package org.apache.james.webadmin.data.jmap;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.scheduler.Schedulers;

public class PopulateEmailQueryViewTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("PopulateEmailQueryViewTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private static AdditionalInformation from(EmailQueryViewPopulator.Progress progress,
                                                  RunningOptions runningOptions) {
            return new AdditionalInformation(runningOptions,
                progress.getProcessedUserCount(),
                progress.getProcessedMessageCount(),
                progress.getFailedUserCount(),
                progress.getFailedMessageCount(),
                Clock.systemUTC().instant());
        }

        private final RunningOptions runningOptions;
        private final long processedUserCount;
        private final long processedMessageCount;
        private final long failedUserCount;
        private final long failedMessageCount;
        private final Instant timestamp;

        public AdditionalInformation(RunningOptions runningOptions, long processedUserCount, long processedMessageCount, long failedUserCount, long failedMessageCount, Instant timestamp) {
            this.runningOptions = runningOptions;
            this.processedUserCount = processedUserCount;
            this.processedMessageCount = processedMessageCount;
            this.failedUserCount = failedUserCount;
            this.failedMessageCount = failedMessageCount;
            this.timestamp = timestamp;
        }

        public long getProcessedUserCount() {
            return processedUserCount;
        }

        public long getProcessedMessageCount() {
            return processedMessageCount;
        }

        public long getFailedUserCount() {
            return failedUserCount;
        }

        public long getFailedMessageCount() {
            return failedMessageCount;
        }

        public RunningOptions getRunningOptions() {
            return runningOptions;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }
    }

    public static class PopulateEmailQueryViewTaskDTO implements TaskDTO {
        private final String type;
        private final Optional<RunningOptionsDTO> runningOptions;

        public PopulateEmailQueryViewTaskDTO(@JsonProperty("type") String type,
                                             @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) {
            this.type = type;
            this.runningOptions = runningOptions;
        }

        @Override
        public String getType() {
            return type;
        }

        public Optional<RunningOptionsDTO> getRunningOptions() {
            return runningOptions;
        }
    }

    public static TaskDTOModule<PopulateEmailQueryViewTask, PopulateEmailQueryViewTaskDTO> module(EmailQueryViewPopulator populator) {
        return DTOModule
            .forDomainObject(PopulateEmailQueryViewTask.class)
            .convertToDTO(PopulateEmailQueryViewTaskDTO.class)
            .toDomainObjectConverter(dto -> asTask(populator, dto))
            .toDTOConverter(PopulateEmailQueryViewTask::asDTO)
            .typeName(TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private static PopulateEmailQueryViewTaskDTO asDTO(PopulateEmailQueryViewTask task, String type) {
        return new PopulateEmailQueryViewTaskDTO(type, Optional.of(RunningOptionsDTO.asDTO(task.runningOptions)));
    }

    private static PopulateEmailQueryViewTask asTask(EmailQueryViewPopulator populator, PopulateEmailQueryViewTaskDTO dto) {
        return new PopulateEmailQueryViewTask(populator,
            dto.getRunningOptions()
                .map(RunningOptionsDTO::asDomainObject)
                .orElse(RunningOptions.DEFAULT));
    }

    private final EmailQueryViewPopulator populator;
    private final EmailQueryViewPopulator.Progress progress;
    private final RunningOptions runningOptions;

    PopulateEmailQueryViewTask(EmailQueryViewPopulator populator, RunningOptions runningOptions) {
        this.populator = populator;
        this.runningOptions = runningOptions;
        this.progress = new EmailQueryViewPopulator.Progress();
    }

    @Override
    public Result run() {
        return populator.populateView(progress, runningOptions)
            .subscribeOn(Schedulers.elastic())
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(progress, runningOptions));
    }
}