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

package org.apache.james.mailbox.cassandra.table;

public interface CassandraMessageV2Table {

    String TABLE_NAME = "messageV2";
    String INTERNAL_DATE = "internalDate";
    String BODY_START_OCTET = "bodyStartOctet";
    String FULL_CONTENT_OCTETS = "fullContentOctets";
    String BODY_OCTECTS = "bodyOctets";
    String TEXTUAL_LINE_COUNT = "textualLineCount";
    String BODY_CONTENT = "bodyContent";
    String HEADER_CONTENT = "headerContent";
    String PROPERTIES = "properties";
    String ATTACHMENTS = "attachments";

    interface Properties {
        String NAMESPACE = "namespace";
        String NAME = "name";
        String VALUE = "value";
    }

    interface Attachments {
        String ID = "id";
        String NAME = "name";
        String CID = "cid";
        String IS_INLINE = "isInline";
    }

}
