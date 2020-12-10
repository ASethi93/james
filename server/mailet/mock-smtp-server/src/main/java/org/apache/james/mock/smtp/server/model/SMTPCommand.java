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

package org.apache.james.mock.smtp.server.model;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SMTPCommand {
    RCPT_TO("RCPT TO"),
    EHLO("EHLO"),
    MAIL_FROM("MAIL FROM"),
    DATA("DATA"),
    RSET("RSET"),
    VRFY("VRFY"),
    NOOP("NOOP"),
    QUIT("QUIT");

    @JsonCreator
    public static SMTPCommand parse(String value) {
        return Arrays.stream(SMTPCommand.values())
            .filter(command -> command.commandName.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No command name associated with supplied value: " + value));
    }

    private final String commandName;

    SMTPCommand(String commandName) {
        this.commandName = commandName;
    }

    @JsonValue
    public String getCommandName() {
        return commandName;
    }
}
