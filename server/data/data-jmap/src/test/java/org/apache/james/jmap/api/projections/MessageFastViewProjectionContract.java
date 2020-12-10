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

package org.apache.james.jmap.api.projections;

import static org.apache.james.jmap.api.projections.MessageFastViewProjection.METRIC_RETRIEVE_HIT_COUNT;
import static org.apache.james.jmap.api.projections.MessageFastViewProjection.METRIC_RETRIEVE_MISS_COUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.apache.james.jmap.api.model.Preview;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public interface MessageFastViewProjectionContract {

    Preview PREVIEW_1 = Preview.from("preview 1");
    Preview PREVIEW_2 = Preview.from("preview 2");
    MessageFastViewPrecomputedProperties MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1 = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW_1)
        .hasAttachment()
        .build();
    MessageFastViewPrecomputedProperties MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2 = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW_2)
        .noAttachments()
        .build();

    MessageFastViewProjection testee();

    MessageId newMessageId();

    RecordingMetricFactory metricFactory();

    @Test
    default void retrieveShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().retrieve((MessageId) null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void retrieveShouldReturnStoredPreview() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId)).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1);
    }

    @Test
    default void retrieveShouldReturnEmptyWhenMessageIdNotFound() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId2)).blockOptional())
            .isEmpty();
    }

    @Test
    default void retrieveShouldReturnTheRightPreviewWhenStoringMultipleMessageIds() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();
        Mono.from(testee().store(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2))
            .block();

        SoftAssertions.assertSoftly(softly -> {
           softly.assertThat(Mono.from(testee().retrieve(messageId1)).block())
               .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1);
           softly.assertThat(Mono.from(testee().retrieve(messageId2)).block())
               .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2);
        });
    }

    @Test
    default void retrieveShouldThrowWhenNullMessageIds() {
        assertThatThrownBy(() -> Mono.from(testee().retrieve((List<MessageId>) null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void retrieveShouldReturnEmptyWhenEmptyMessageIds() {
        assertThat(Mono.from(testee().retrieve(ImmutableList.of())).block())
            .isEmpty();
    }

    @Test
    default void retrieveShouldReturnAMapContainingStoredPreviews() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();
        Mono.from(testee().store(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2))
            .block();

        assertThat(Mono.from(testee().retrieve(ImmutableList.of(messageId1, messageId2))).block())
            .isEqualTo(ImmutableMap.builder()
                .put(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1)
                .put(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2)
                .build());
    }

    @Test
    default void retrieveShouldReturnOnlyPreviewsAvailableInTheStore() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        MessageId messageId3 = newMessageId();
        Mono.from(testee().store(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();
        Mono.from(testee().store(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2))
            .block();

        assertThat(Mono.from(testee().retrieve(ImmutableList.of(messageId1, messageId2, messageId3))).block())
            .isEqualTo(ImmutableMap.builder()
                .put(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1)
                .put(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2)
                .build());
    }

    @Test
    default void retrieveShouldReturnOnlyPreviewsByAskedMessageIds() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();
        Mono.from(testee().store(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2))
            .block();

        assertThat(Mono.from(testee().retrieve(ImmutableList.of(messageId1))).block())
            .isEqualTo(ImmutableMap.builder()
                .put(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1)
                .build());
    }

    @Test
    default void storeShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().store(null, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void storeShouldThrowWhenNullPreview() {
        MessageId messageId = newMessageId();
        assertThatThrownBy(() -> Mono.from(testee().store(messageId, null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void storeShouldOverrideOldRecord() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId)).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2);
    }

    @Test
    default void storeShouldBeIdempotent() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId)).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1);
    }

    @Test
    default void concurrentStoreShouldOverrideOldValueWhenSameMessageId() throws Exception {
        int threadCount = 10;
        int stepCount = 100;

        ConcurrentHashMap<Integer, MessageId> messageIds = new ConcurrentHashMap<>();
        IntStream.range(0, threadCount)
            .forEach(thread -> messageIds.put(thread, newMessageId()));

        ConcurrentTestRunner.builder()
            .reactorOperation((thread, step) -> testee()
                .store(messageIds.get(thread), MessageFastViewPrecomputedProperties.builder()
                    .preview(Preview.from(String.valueOf(step)))
                    .hasAttachment()
                    .build()))
            .threadCount(threadCount)
            .operationCount(stepCount)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        IntStream.range(0, threadCount)
            .forEach(index -> assertThat(Mono.from(testee()
                    .retrieve(messageIds.get(index)))
                    .block())
                .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                    .preview(Preview.from(String.valueOf(stepCount - 1)))
                    .hasAttachment()
                    .build()));
    }

    @Test
    default void storeShouldReturnAnyLatestPreviewOnConcurrentUpdate() throws Exception {
        MessageId messageId = newMessageId();

        ConcurrentTestRunner.builder()
            .reactorOperation((thread, step) -> testee()
                .store(messageId, MessageFastViewPrecomputedProperties.builder()
                    .preview(Preview.from(thread + "-" + step))
                    .hasAttachment()
                    .build()))
            .threadCount(10)
            .operationCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        String previewAsString = Mono.from(testee().retrieve(messageId)).block()
            .getPreview()
            .getValue();

        assertThat(previewAsString)
            .describedAs("Ensure the stored result was generated by the last operation of one of the threads")
            .isIn("0-99", "1-99", "2-99", "3-99", "4-99", "5-99", "6-99", "7-99", "8-99", "9-99");
    }

    @Test
    default void deleteShouldThrowWhenNullMessageId() {
        assertThatThrownBy(() -> Mono.from(testee().delete(null)).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldNotThrowWhenMessageIdNotFound() {
        MessageId messageId = newMessageId();
        assertThatCode(() -> Mono.from(testee().delete(messageId)).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldDeleteStoredRecord() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        Mono.from(testee().delete(messageId))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId)).blockOptional())
            .isEmpty();
    }

    @Test
    default void deleteShouldNotDeleteAnotherRecord() {
        MessageId messageId1 = newMessageId();
        MessageId messageId2 = newMessageId();
        Mono.from(testee().store(messageId1, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();
        Mono.from(testee().store(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2))
            .block();

        Mono.from(testee().delete(messageId1))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId2)).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2);
    }

    @Test
    default void deleteShouldBeIdempotent() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        Mono.from(testee().delete(messageId))
            .block();
        Mono.from(testee().delete(messageId))
            .block();

        assertThat(Mono.from(testee().retrieve(messageId)).blockOptional())
            .isEmpty();
    }

    @Test
    default void retrieveShouldIncrementMetricHitCountWhenPreviewIsFound() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        Mono.from(testee().retrieve(messageId))
            .block();

        assertThat(metricFactory().countFor(METRIC_RETRIEVE_HIT_COUNT))
            .isEqualTo(1);
    }

    @Test
    default void retrieveShouldNotIncrementMetricMissCountWhenPreviewIsFound() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        Mono.from(testee().retrieve(messageId))
            .block();

        assertThat(metricFactory().countFor(METRIC_RETRIEVE_MISS_COUNT))
            .isEqualTo(0);
    }

    @Test
    default void retrieveShouldIncrementMetricMissCountWhenPreviewIsNotFound() {
        MessageId messageId = newMessageId();
        Mono.from(testee().retrieve(messageId))
            .block();

        assertThat(metricFactory().countFor(METRIC_RETRIEVE_MISS_COUNT))
            .isEqualTo(1);
    }

    @Test
    default void retrieveShouldNotIncrementMetricHitCountWhenPreviewIsNotFound() {
        MessageId messageId = newMessageId();
        Mono.from(testee().retrieve(messageId))
            .block();

        assertThat(metricFactory().countFor(METRIC_RETRIEVE_HIT_COUNT))
            .isEqualTo(0);
    }

    @Test
    default void clearShouldNotThrowWhenNoData() {
        assertThatCode(() -> Mono.from(testee().clear()).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void clearShouldRemoveStoredData() {
        MessageId messageId = newMessageId();
        Mono.from(testee().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1))
            .block();

        Mono.from(testee().clear()).block();

        assertThat(Mono.from(testee().retrieve(messageId)).blockOptional())
            .isEmpty();
    }
}