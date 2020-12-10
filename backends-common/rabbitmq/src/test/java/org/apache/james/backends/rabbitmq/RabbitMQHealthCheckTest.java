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

package org.apache.james.backends.rabbitmq;

import static org.apache.james.backends.rabbitmq.RabbitMQExtension.IsolationPolicy.WEAK;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.healthcheck.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RabbitMQHealthCheckTest {

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(WEAK);

    private RabbitMQHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        healthCheck = new RabbitMQHealthCheck(rabbitMQExtension.getConnectionPool(), rabbitMQExtension.getRabbitChannelPool());
    }

    @AfterEach
    void tearDown(DockerRabbitMQ rabbitMQ) throws Exception {
        rabbitMQ.reset();
    }

    @Test
    void checkShouldReturnHealthyWhenRabbitMQIsRunning() {
        Result check = healthCheck.check().block();

        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnHealthyWhenCalledSeveralTime() {
        healthCheck.check().block();
        healthCheck.check().block();
        healthCheck.check().block();
        healthCheck.check().block();
        healthCheck.check().block();
        healthCheck.check().block();
        healthCheck.check().block();
        Result check = healthCheck.check().block();

        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldReturnUnhealthyWhenRabbitMQIsNotRunning(DockerRabbitMQ rabbitMQ) throws Exception {
        rabbitMQ.stopApp();

        Result check = healthCheck.check().block();

        assertThat(check.isHealthy()).isFalse();
    }

    @Test
    void checkShouldDetectWhenRabbitMQRecovered(DockerRabbitMQ rabbitMQ) throws Exception {
        rabbitMQ.stopApp();
        healthCheck.check().block();

        rabbitMQ.startApp();

        Result check = healthCheck.check().block();
        assertThat(check.isHealthy()).isTrue();
    }

    @Test
    void checkShouldDetectWhenRabbitMQFail(DockerRabbitMQ rabbitMQ) throws Exception {
        healthCheck.check().block();

        rabbitMQ.stopApp();

        Result check = healthCheck.check().block();
        assertThat(check.isHealthy()).isFalse();
    }
}