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

package org.apache.james.jmap.json

import eu.timepit.refined.refineV
import javax.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.mail.EmailSet.{UnparsedMessageId, UnparsedMessageIdConstraint}
import org.apache.james.jmap.mail.EmailSubmissionSet.EmailSubmissionCreationId
import org.apache.james.jmap.mail.{DestroyIds, EmailSubmissionAddress, EmailSubmissionCreationRequest, EmailSubmissionCreationResponse, EmailSubmissionId, EmailSubmissionSetRequest, EmailSubmissionSetResponse, Envelope}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.{JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

import scala.util.Try

class EmailSubmissionSetSerializer @Inject()(messageIdFactory: MessageId.Factory) {
  private implicit val mapCreationRequestByEmailSubmissionCreationId: Reads[Map[EmailSubmissionCreationId, JsObject]] =
    Reads.mapReads[EmailSubmissionCreationId, JsObject] {string => refineV[IdConstraint](string).fold(JsError(_), id => JsSuccess(id)) }

  private implicit val messageIdReads: Reads[MessageId] = {
    case JsString(serializedMessageId) => Try(JsSuccess(messageIdFactory.fromString(serializedMessageId)))
      .fold(_ => JsError("Invalid messageId"), messageId => messageId)
    case _ => JsError("Expecting messageId to be represented by a JsString")
  }
  private implicit val notCreatedWrites: Writes[Map[EmailSubmissionCreationId, SetError]] = mapWrites[EmailSubmissionCreationId, SetError](_.value, setErrorWrites)

  private implicit val mailAddressReads: Reads[MailAddress] = {
    case JsString(value) => Try(JsSuccess(new MailAddress(value)))
      .fold(e => JsError(s"Invalid mailAddress: ${e.getMessage}"), mailAddress => mailAddress)
    case _ => JsError("Expecting mailAddress to be represented by a JsString")
  }

  private implicit val emailUpdatesMapReads: Reads[Map[UnparsedMessageId, JsObject]] =
    Reads.mapReads[UnparsedMessageId, JsObject] {string => refineV[UnparsedMessageIdConstraint](string).fold(JsError(_), id => JsSuccess(id)) }
  private implicit val destroyIdsReads: Reads[DestroyIds] = Json.valueFormat[DestroyIds]

  private implicit val emailSubmissionSetRequestReads: Reads[EmailSubmissionSetRequest] = Json.reads[EmailSubmissionSetRequest]

  private implicit val emailSubmissionIdWrites: Writes[EmailSubmissionId] = Json.valueWrites[EmailSubmissionId]

  private implicit val emailSubmissionAddresReads: Reads[EmailSubmissionAddress] = Json.reads[EmailSubmissionAddress]
  private implicit val envelopeReads: Reads[Envelope] = Json.reads[Envelope]

  implicit val emailSubmissionCreationRequestReads: Reads[EmailSubmissionCreationRequest] = Json.reads[EmailSubmissionCreationRequest]

  private implicit val emailSubmissionCreationResponseWrites: Writes[EmailSubmissionCreationResponse] = Json.valueWrites[EmailSubmissionCreationResponse]

  private implicit def emailSubmissionSetResponseWrites(implicit emailSubmissionCreationResponseWrites: Writes[EmailSubmissionCreationResponse]): Writes[EmailSubmissionSetResponse] = Json.writes[EmailSubmissionSetResponse]

  private implicit def emailSubmissionMapCreationResponseWrites(implicit emailSubmissionSetCreationResponseWrites: Writes[EmailSubmissionCreationResponse]): Writes[Map[EmailSubmissionCreationId, EmailSubmissionCreationResponse]] =
    mapWrites[EmailSubmissionCreationId, EmailSubmissionCreationResponse](_.value, emailSubmissionSetCreationResponseWrites)

  def deserializeEmailSubmissionSetRequest(input: JsValue): JsResult[EmailSubmissionSetRequest] = Json.fromJson[EmailSubmissionSetRequest](input)

  def serializeEmailSubmissionSetResponse(response: EmailSubmissionSetResponse): JsValue = Json.toJson(response)
}
