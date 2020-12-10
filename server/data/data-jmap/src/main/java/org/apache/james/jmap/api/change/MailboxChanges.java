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

package org.apache.james.jmap.api.change;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.model.MailboxId;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

public class MailboxChanges {

    public static class MailboxChangesBuilder {

        public static class MailboxChangeCollector implements Collector<MailboxChange, MailboxChangesBuilder, MailboxChanges> {
            private final MailboxChange.Limit limit;

            public MailboxChangeCollector(MailboxChange.Limit limit) {
                this.limit = limit;
            }

            @Override
            public Supplier<MailboxChangesBuilder> supplier() {
                return () -> new MailboxChangesBuilder(limit);
            }

            public BiConsumer<MailboxChangesBuilder, MailboxChange> accumulator() {
                return MailboxChangesBuilder::add;
            }

            @Override
            public BinaryOperator<MailboxChangesBuilder> combiner() {
                throw new NotImplementedException("Not supported");
            }

            @Override
            public Function<MailboxChangesBuilder, MailboxChanges> finisher() {
                return MailboxChangesBuilder::build;
            }

            @Override
            public Set<Characteristics> characteristics() {
                return Sets.immutableEnumSet(Characteristics.UNORDERED);
            }
        }

        private MailboxChange.State state;
        private boolean hasMoreChanges;
        private boolean canAddMoreItem;
        private MailboxChange.Limit limit;
        private Set<MailboxId> created;
        private Set<MailboxId> updated;
        private Set<MailboxId> destroyed;

        public MailboxChangesBuilder(MailboxChange.Limit limit) {
            this.limit = limit;
            this.hasMoreChanges = false;
            this.canAddMoreItem = true;
            this.created = new HashSet<>();
            this.updated = new HashSet<>();
            this.destroyed = new HashSet<>();
        }

        public MailboxChanges.MailboxChangesBuilder add(MailboxChange change) {
            if (!canAddMoreItem) {
                return this;
            }

            Set<MailboxId> createdTemp = new HashSet<>(created);
            Set<MailboxId> updatedTemp = new HashSet<>(updated);
            Set<MailboxId> destroyedTemp = new HashSet<>(destroyed);
            createdTemp.addAll(change.getCreated());
            updatedTemp.addAll(change.getUpdated());
            destroyedTemp.addAll(change.getDestroyed());

            if (createdTemp.size() + updatedTemp.size() + destroyedTemp.size() > limit.getValue()) {
                hasMoreChanges = true;
                canAddMoreItem = false;
                return this;
            }

            state = change.getState();
            this.created.addAll(change.getCreated());
            this.updated.addAll(change.getUpdated());
            this.destroyed.addAll(change.getDestroyed());

            return this;
        }

        public MailboxChanges build() {
            return new MailboxChanges(state, hasMoreChanges, created, updated, destroyed);
        }
    }

    private MailboxChange.State newState;
    private final boolean hasMoreChanges;
    private final Set<MailboxId> created;
    private final Set<MailboxId> updated;
    private final Set<MailboxId> destroyed;

    private MailboxChanges(MailboxChange.State newState, boolean hasMoreChanges, Set<MailboxId> created, Set<MailboxId> updated, Set<MailboxId> destroyed) {
        this.newState = newState;
        this.hasMoreChanges = hasMoreChanges;
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
    }

    public MailboxChangesBuilder build(MailboxChange.Limit limit) {
        return new MailboxChangesBuilder(limit);
    }

    public MailboxChange.State getNewState() {
        return newState;
    }

    public boolean hasMoreChanges() {
        return hasMoreChanges;
    }

    public Set<MailboxId> getCreated() {
        return created;
    }

    public Set<MailboxId> getUpdated() {
        return updated;
    }

    public Set<MailboxId> getDestroyed() {
        return destroyed;
    }

    public List<MailboxId> getAllChanges() {
        return ImmutableList.<MailboxId>builder()
            .addAll(created)
            .addAll(updated)
            .addAll(destroyed)
            .build();
    }
}
