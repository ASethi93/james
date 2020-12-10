/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.core

import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Id.Id

final case class ClientId(value: Id) {
  def referencesPreviousCreationId: Boolean = value.value.startsWith("#")
  def retrieveOriginalClientId: Option[Either[IllegalArgumentException, ClientId]] =
    if (referencesPreviousCreationId) {
      Some(Id.validate(value.value.substring(1, value.value.length))
        .map(ClientId))
    } else {
      None
    }
}

final case class ServerId(value: Id)

case class CreatedIds(value: Map[ClientId, ServerId])

case class RequestObject(using: Seq[CapabilityIdentifier], methodCalls: Seq[Invocation], createdIds: Option[CreatedIds] = None)
