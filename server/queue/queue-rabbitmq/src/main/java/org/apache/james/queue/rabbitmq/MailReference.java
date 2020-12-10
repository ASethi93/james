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

package org.apache.james.queue.rabbitmq;

import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.mailet.Mail;

public class MailReference {

    private final EnqueueId enqueueId;
    private final Mail mail;
    private final MimeMessagePartsId partsId;

    public MailReference(EnqueueId enqueueId, Mail mail, MimeMessagePartsId partsId) {
        this.enqueueId = enqueueId;
        this.mail = mail;
        this.partsId = partsId;
    }

    public EnqueueId getEnqueueId() {
        return enqueueId;
    }

    public Mail getMail() {
        return mail;
    }

    public MimeMessagePartsId getPartsId() {
        return partsId;
    }
}
