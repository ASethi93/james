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

package org.apache.james.mailbox.cassandra;

import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.mailbox.MailboxManagerStressContract;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraMailboxManagerStressTest implements MailboxManagerStressContract<CassandraMailboxManager> {

    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MailboxAggregateModule.MODULE_WITH_QUOTA);

    private CassandraMailboxManager mailboxManager;

    @Override
    public CassandraMailboxManager getManager() {
        return mailboxManager;
    }

    @Override
    public EventBus retrieveEventBus() {
        return mailboxManager.getEventBus();
    }

    @BeforeEach
    void setUp() {
        this.mailboxManager = CassandraMailboxManagerProvider.provideMailboxManager(
            cassandra.getCassandraCluster(),
            PreDeletionHooks.NO_PRE_DELETION_HOOK);
    }
}
