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

package org.apache.james.jmap.core

import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import org.apache.james.jmap.core.UTCDate.UTC_ZONE_ID

object UTCDate {
  private val UTC_ZONE_ID: ZoneId = ZoneId.of("UTC")

  def from(date: Date, zoneId: ZoneId): UTCDate = UTCDate(ZonedDateTime.ofInstant(date.toInstant, zoneId))
}

case class UTCDate(date: ZonedDateTime) {
  def asUTC: ZonedDateTime = {
    date.withZoneSameInstant(UTC_ZONE_ID)
  }
}
