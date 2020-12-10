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
package org.apache.james.jmap.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.draft.api.SimpleTokenManager;
import org.apache.james.mailbox.MailboxManager;
import org.junit.Before;
import org.junit.Test;

import reactor.netty.http.server.HttpServerRequest;

public class QueryParameterAccessTokenAuthenticationStrategyTest {

    private QueryParameterAccessTokenAuthenticationStrategy testee;
    private HttpServerRequest mockedRequest;

    @Before
    public void setup() {
        SimpleTokenManager mockedSimpleTokenManager = mock(SimpleTokenManager.class);
        MailboxManager mockedMailboxManager = mock(MailboxManager.class);
        mockedRequest = mock(HttpServerRequest.class);

        testee = new QueryParameterAccessTokenAuthenticationStrategy(mockedSimpleTokenManager, mockedMailboxManager);
    }

    @Test
    public void createMailboxSessionShouldThrowWhenNoAccessTokenProvided() {
        when(mockedRequest.param("access_token"))
            .thenReturn(null);

        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .isEmpty();
    }

    @Test
    public void createMailboxSessionShouldThrowWhenAccessTokenIsNotValid() {
        when(mockedRequest.param("access_token"))
            .thenReturn("bad");

        assertThat(testee.createMailboxSession(mockedRequest).blockOptional())
            .isEmpty();
    }
}