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

import static org.apache.james.util.FunctionalUtils.negate;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Optional;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class DefaultMailboxesProvisioner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMailboxesProvisioner.class);
    private final MailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    public DefaultMailboxesProvisioner(MailboxManager mailboxManager,
                                SubscriptionManager subscriptionManager,
                                MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
        this.metricFactory = metricFactory;
    }

    public Mono<Void> createMailboxesIfNeeded(MailboxSession session) {
        return metricFactory.decorateSupplierWithTimerMetric("JMAP-mailboxes-provisioning",
            () -> {
                Username username = session.getUser();
                return createDefaultMailboxes(username);
            });
    }

    private Mono<Void> createDefaultMailboxes(Username username) {
        MailboxSession session = mailboxManager.createSystemSession(username);

        return Flux.fromIterable(DefaultMailboxes.DEFAULT_MAILBOXES)
            .map(toMailboxPath(session))
            .filterWhen(mailboxPath -> mailboxDoesntExist(mailboxPath, session), DEFAULT_CONCURRENCY)
            .concatMap(mailboxPath -> Mono.fromRunnable(() -> createMailbox(mailboxPath, session))
                .subscribeOn(Schedulers.elastic()))
            .then();
    }

    private Mono<Boolean> mailboxDoesntExist(MailboxPath mailboxPath, MailboxSession session) {
        try {
            return Mono.from(mailboxManager.mailboxExists(mailboxPath, session))
                .map(negate());
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Function<String, MailboxPath> toMailboxPath(MailboxSession session) {
        return mailbox -> MailboxPath.forUser(session.getUser(), mailbox);
    }
    
    private void createMailbox(MailboxPath mailboxPath, MailboxSession session) {
        try {
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
            if (mailboxId.isPresent()) {
                subscriptionManager.subscribe(session, mailboxPath.getName());
            }
            LOGGER.info("Provisioning {}. {} created.", mailboxPath, mailboxId);
        } catch (MailboxExistsException e) {
            LOGGER.info("Mailbox {} have been created concurrently", mailboxPath);
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }
}
