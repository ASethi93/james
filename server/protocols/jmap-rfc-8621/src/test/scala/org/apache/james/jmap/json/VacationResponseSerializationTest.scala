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

import java.time.ZonedDateTime

import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.json.VacationResponseSerializationTest.VACATION_RESPONSE
import org.apache.james.jmap.mail.Subject
import org.apache.james.jmap.vacation
import org.apache.james.jmap.vacation.{FromDate, HtmlBody, IsEnabled, TextBody, ToDate, VacationResponse, VacationResponseId}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

object VacationResponseSerializationTest {
  private val UTC_DATE_TIME_2016 = UTCDate(ZonedDateTime.parse("2016-10-09T01:07:06Z"))
  private val UTC_DATE_TIME_2017 = UTCDate(ZonedDateTime.parse("2017-10-09T01:07:06Z"))

  private val VACATION_RESPONSE_ID: VacationResponseId = VacationResponseId()
  private val IS_ENABLED: IsEnabled = IsEnabled(true)
  private val FROM_DATE: Option[FromDate] = Some(FromDate(UTC_DATE_TIME_2016))
  private val TO_DATE: Option[ToDate] = Some(ToDate(UTC_DATE_TIME_2017))
  private val SUBJECT: Option[Subject] = Some(Subject("Hello world"))
  private val TEXT_BODY: Option[TextBody] = Some(TextBody("text is required when enabled"))
  private val HTML_BODY: Option[HtmlBody] = Some(HtmlBody("<b>HTML body</b>"))

  val VACATION_RESPONSE: VacationResponse = vacation.VacationResponse(
    id = VACATION_RESPONSE_ID,
    isEnabled = IS_ENABLED,
    fromDate = FROM_DATE,
    toDate = TO_DATE,
    subject = SUBJECT,
    textBody = TEXT_BODY,
    htmlBody = HTML_BODY
  )
}

class VacationResponseSerializationTest extends AnyWordSpec with Matchers {
  "Serialize VacationResponse" should {
    "succeed" in {
      val expectedJson: String =
        """{
          | "id":"singleton",
          | "isEnabled":true,
          | "fromDate":"2016-10-09T01:07:06Z",
          | "toDate":"2017-10-09T01:07:06Z",
          | "subject":"Hello world",
          | "textBody":"text is required when enabled",
          | "htmlBody":"<b>HTML body</b>"
          |}""".stripMargin

      assertThatJson(Json.stringify(VacationSerializer.serialize(VACATION_RESPONSE))).isEqualTo(expectedJson)
    }
  }
}
