/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.acl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleGroupMembershipResolverTest {
    private static final Username USER = Username.of("user");

    private SimpleGroupMembershipResolver simpleGroupMembershipResolver;

    @BeforeEach
    void setUp() {
       simpleGroupMembershipResolver = new SimpleGroupMembershipResolver();
    }

    @Test
    void isMemberShouldReturnFalseWhenEmptyResolver() {
        //When
        boolean actual = simpleGroupMembershipResolver.isMember(USER, "group");
        //Then
        assertThat(actual).isFalse();
    }

    @Test
    void isMemberShouldReturnTrueWhenTheSearchedMembershipIsPresent() {
        //Given
        simpleGroupMembershipResolver.addMembership("group", USER);
        //When
        boolean actual = simpleGroupMembershipResolver.isMember(USER, "group");
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    void addMembershipShouldAddAMembershipWhenNonNullUser() {
        //When
        simpleGroupMembershipResolver.addMembership("group", USER);
        boolean actual = simpleGroupMembershipResolver.isMember(USER, "group");
        //Then
        assertThat(actual).isTrue();
    }

    @Test
    void addMembershipShouldAddAMembershipWithANullUser() {
        //Given
        Username userAdded = null;
        //When
        simpleGroupMembershipResolver.addMembership("group", userAdded);
        boolean actual = simpleGroupMembershipResolver.isMember(userAdded, "group");
        //Then
        assertThat(actual).isTrue();
    }

}
