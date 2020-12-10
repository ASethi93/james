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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.draft.model.CreationMessageId;
import org.apache.james.mailbox.AttachmentContentLoader;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.Cid;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil.Usage;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.stream.Field;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MIMEMessageConverterTest {
    MailboxSession session;
    AttachmentContentLoader attachmentContentLoader;

    @BeforeEach
    void setUp() {
        session = MailboxSessionUtil.create(Username.of("bob"));
        attachmentContentLoader = mock(AttachmentContentLoader.class);
    }

    @Test
    void convertToMimeShouldAddInReplyToHeaderWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        String matchingMessageId = "unique-message-id";
        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .inReplyToMessageId(matchingMessageId)
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("In-Reply-To")).extracting(Field::getBody)
                .containsOnly(matchingMessageId);
    }

    @Test
    void convertToMimeShouldGenerateMessageId() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage message = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), message), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("Message-ID")).extracting(Field::getBody)
                .isNotNull();
    }

    @Test
    void convertToMimeShouldGenerateMessageIdWhenSenderWithoutDomain() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage message = CreationMessage.builder()
                .from(DraftEmailer.builder().email("sender").build())
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), message), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("Message-ID")).extracting(Field::getBody)
                .isNotNull();
    }

    @Test
    void convertToMimeShouldGenerateMessageIdContainingSenderDomain() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage message = CreationMessage.builder()
                .from(DraftEmailer.builder().email("email@domain.com").build())
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), message), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("Message-ID")).hasSize(1);
        assertThat(result.getHeader().getFields("Message-ID").get(0).getBody())
            .contains("@domain.com");
    }

    @Test
    void convertToMimeShouldAddHeaderWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .headers(ImmutableMap.of("FIRST", "first value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("FIRST")).extracting(Field::getBody)
                .containsOnly("first value");
    }

    @Test
    void convertToMimeShouldAddHeadersWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .headers(ImmutableMap.of("FIRST", "first value", "SECOND", "second value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("FIRST")).extracting(Field::getBody)
                .containsOnly("first value");
        assertThat(result.getHeader().getFields("SECOND")).extracting(Field::getBody)
            .containsOnly("second value");
    }

    @Test
    void convertToMimeShouldFilterGeneratedHeadersWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        String joesEmail = "joe@example.com";
        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().email(joesEmail).name("joe").build())
                .headers(ImmutableMap.of("From", "hacker@example.com", "VALID", "valid header value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getFrom()).extracting(Mailbox::getAddress)
            .allMatch(f -> f.equals(joesEmail));
        assertThat(result.getHeader().getFields("VALID")).extracting(Field::getBody)
            .containsOnly("valid header value");
        assertThat(result.getHeader().getFields("From")).extracting(Field::getBody)
            .containsOnly("joe <joe@example.com>");
    }

    @Test
    void convertToMimeShouldFilterGeneratedHeadersRegardlessOfCaseWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        String joesEmail = "joe@example.com";
        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().email(joesEmail).name("joe").build())
                .headers(ImmutableMap.of("frOM", "hacker@example.com", "VALID", "valid header value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getFrom()).extracting(Mailbox::getAddress)
            .allMatch(f -> f.equals(joesEmail));
        assertThat(result.getHeader().getFields("VALID")).extracting(Field::getBody)
            .containsOnly("valid header value");
        assertThat(result.getHeader().getFields("From")).extracting(Field::getBody)
            .containsOnly("joe <joe@example.com>");
    }

    @Test
    void convertToMimeShouldAddMultivaluedHeadersWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .headers(ImmutableMap.of("FIRST", "first value\nsecond value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("FIRST")).extracting(Field::getBody)
            .containsOnly("first value", "second value");
    }

    @Test
    void convertToMimeShouldFilterEmptyHeaderNames() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("joe").build())
                .headers(ImmutableMap.of("", "empty header name value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("")).isEmpty();
    }

    @Test
    void convertToMimeShouldFilterWhiteSpacesOnlyHeaderNames() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("joe").build())
                .headers(ImmutableMap.of("   ", "only spaces header name values"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader().getFields("   ")).isEmpty();
        assertThat(result.getHeader().getFields("")).isEmpty();
    }

    @Test
    void convertToMimeShouldThrowWhenMessageIsNull() {
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        assertThatThrownBy(() -> sut.convertToMime(
                new ValueWithId.CreationMessageEntry(CreationMessageId.of("any"), null),
                ImmutableList.of(), session))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertToMimeShouldSetBothFromAndSenderHeaders() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        String joesEmail = "joe@example.com";
        CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("deadbeef-dead-beef-dead-beef"))
                .subject("subject")
                .from(DraftEmailer.builder().email(joesEmail).name("joe").build())
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getFrom()).extracting(Mailbox::getAddress).allMatch(f -> f.equals(joesEmail));
        assertThat(result.getSender().getAddress()).isEqualTo(joesEmail);
    }

    @Test
    void convertToMimeShouldSetCorrectLocalDate() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        Instant now = Instant.now();
        ZonedDateTime messageDate = ZonedDateTime.ofInstant(now, ZoneId.systemDefault());

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .date(messageDate)
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getDate()).isEqualToIgnoringMillis(Date.from(now));
    }

    @Test
    void convertToMimeShouldSetQuotedPrintableContentTransferEncodingWhenText() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getHeader()
                .getField("Content-Transfer-Encoding")
                .getBody())
            .isEqualTo("quoted-printable");
    }

    @Test
    void convertToMimeShouldSetTextBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);
        TextBody expected = new BasicBodyFactory().textBody("Hello all!", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    void convertToMimeShouldSetEmptyBodyWhenNoBodyProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);
        TextBody expected = new BasicBodyFactory().textBody("", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    void convertToMimeShouldSetHtmlBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);
        TextBody expected = new BasicBodyFactory().textBody("Hello <b>all</b>!", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    void convertToMimeShouldGenerateMultipartWhenHtmlBodyAndTextBodyProvided() throws Exception {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getBody()).isInstanceOf(Multipart.class);
        assertThat(result.isMultipart()).isTrue();
        assertThat(result.getMimeType()).isEqualTo("multipart/alternative");
        Multipart typedResult = (Multipart)result.getBody();
        assertThat(typedResult.getBodyParts()).hasSize(2);
    }

    @Test
    void convertShouldGenerateExpectedMultipartWhenHtmlAndTextBodyProvided() throws Exception {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all</b>!")
                .build();

        String expectedHeaders = "MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/alternative;\r\n" +
                " boundary=\"-=Part.";
        String expectedPart1 = "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "\r\n" +
                "Hello all!\r\n";
        String expectedPart2 = "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "\r\n" +
                "Hello <b>all</b>!\r\n";

        // When
        byte[] convert = sut.convert(new MessageWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        String actual = new String(convert, StandardCharsets.UTF_8);
        assertThat(actual).startsWith(expectedHeaders);
        assertThat(actual).contains(expectedPart1);
        assertThat(actual).contains(expectedPart2);
    }

    @Test
    void convertToMimeShouldSetMimeTypeWhenTextBody() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    void convertToMimeShouldSetMimeTypeWhenHtmlBody() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getMimeType()).isEqualTo("text/html");
    }

    @Test
    void convertToMimeShouldSetEmptyHtmlBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);
        TextBody expected = new BasicBodyFactory().textBody("", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
        assertThat(result.getMimeType()).isEqualTo("text/html");
    }

    @Test
    void convertToMimeShouldSetEmptyTextBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);
        TextBody expected = new BasicBodyFactory().textBody("", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(), session);

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
        assertThat(result.getMimeType()).isEqualTo("text/plain");
    }

    @Nested
    class WithAttachments {

        @Test
        void convertToMimeShouldAddAttachment() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String expectedCID = "cid";
            String expectedMimeType = "image/png";
            String text = "123456";
            TextBody expectedBody = new BasicBodyFactory().textBody(text.getBytes(), StandardCharsets.UTF_8);
            AttachmentId blodId = AttachmentId.from("blodId");
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(blodId)
                    .size(text.getBytes().length)
                    .type(expectedMimeType)
                    .build())
                .cid(Cid.from(expectedCID))
                .isInline(true)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .anySatisfy(part -> {
                    assertThat(part.getBody()).isEqualToComparingOnlyGivenFields(expectedBody, "content");
                    assertThat(part.getDispositionType()).isEqualTo("inline");
                    assertThat(part.getMimeType()).isEqualTo(expectedMimeType);
                    assertThat(part.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
                    assertThat(part.getContentTransferEncoding()).isEqualTo("base64");
                });
        }

        @Test
        void convertToMimeShouldPreservePartCharset() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String expectedCID = "cid";
            String expectedMimeType = "text/calendar; charset=\"iso-8859-1\"";
            String text = "123456";
            TextBody expectedBody = new BasicBodyFactory().textBody(text.getBytes(), StandardCharsets.UTF_8);
            AttachmentId blodId = AttachmentId.from("blodId");
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(blodId)
                    .size(text.getBytes().length)
                    .type(expectedMimeType)
                    .build())
                .cid(Cid.from(expectedCID))
                .isInline(true)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .anySatisfy(part -> {
                    assertThat(part.getBody()).isEqualToComparingOnlyGivenFields(expectedBody, "content");
                    assertThat(part.getDispositionType()).isEqualTo("inline");
                    assertThat(part.getMimeType()).isEqualTo("text/calendar");
                    assertThat(part.getCharset()).isEqualTo("iso-8859-1");
                    assertThat(part.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
                    assertThat(part.getContentTransferEncoding()).isEqualTo("base64");
                });
        }

        @Test
        void convertToMimeShouldAddAttachmentAndMultipartAlternativeWhenOneAttachementAndTextAndHtmlBody() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all<b>!")
                .build();
            TextBody expectedTextBody = new BasicBodyFactory().textBody("Hello all!".getBytes(), StandardCharsets.UTF_8);
            TextBody expectedHtmlBody = new BasicBodyFactory().textBody("Hello <b>all<b>!".getBytes(), StandardCharsets.UTF_8);

            String expectedCID = "cid";
            String expectedMimeType = "image/png";
            String text = "123456";
            TextBody expectedAttachmentBody = new BasicBodyFactory().textBody(text.getBytes(), StandardCharsets.UTF_8);
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type(expectedMimeType)
                    .build())
                .cid(Cid.from(expectedCID))
                .isInline(true)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .satisfies(part -> {
                    assertThat(part.getBody()).isInstanceOf(Multipart.class);
                    assertThat(part.isMultipart()).isTrue();
                    assertThat(part.getMimeType()).isEqualTo("multipart/alternative");
                    assertThat(((Multipart)part.getBody()).getBodyParts()).hasSize(2);
                    Entity textPart = ((Multipart)part.getBody()).getBodyParts().get(0);
                    Entity htmlPart = ((Multipart)part.getBody()).getBodyParts().get(1);
                    assertThat(textPart.getBody()).isEqualToComparingOnlyGivenFields(expectedTextBody, "content");
                    assertThat(htmlPart.getBody()).isEqualToComparingOnlyGivenFields(expectedHtmlBody, "content");
                }, Index.atIndex(0))
                .satisfies(part -> {
                    assertThat(part.getBody()).isEqualToComparingOnlyGivenFields(expectedAttachmentBody, "content");
                    assertThat(part.getDispositionType()).isEqualTo("inline");
                    assertThat(part.getMimeType()).isEqualTo(expectedMimeType);
                    assertThat(part.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
                }, Index.atIndex(1));
        }

        @Test
        void convertShouldEncodeWhenNonASCIICharacters() {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                    .mailboxId("dead-bada55")
                    .subject("subject")
                    .from(DraftEmailer.builder().name("sender").build())
                    .htmlBody("Some non-ASCII characters: áÄÎßÿ")
                    .build();

            // When
            ImmutableList<MessageAttachmentMetadata> attachments = ImmutableList.of();
            byte[] convert = sut.convert(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), attachments, session);

            String expectedEncodedContent = "Some non-ASCII characters: =C3=A1=C3=84=C3=8E=C3=9F=C3=BF";

            // Then
            String actual = new String(convert, StandardCharsets.US_ASCII);
            assertThat(actual).contains(expectedEncodedContent);
        }

        @Test
        void convertToMimeShouldAddAttachmentAndContainsIndicationAboutTheWayToEncodeFilenamesAttachmentInTheInputStreamWhenSending() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String expectedCID = "cid";
            String expectedMimeType = "image/png";
            String text = "123456";
            String name = "ديناصور.png";
            String expectedName = EncoderUtil.encodeEncodedWord(name, Usage.TEXT_TOKEN);
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name(name)
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type(expectedMimeType)
                    .build())
                .cid(Cid.from(expectedCID))
                .isInline(true)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));


            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .anySatisfy(part -> assertThat(getNameParameterValue(part)).isEqualTo(expectedName));
        }


        @Test
        void convertToMimeShouldHaveMixedMultipart() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name("ديناصور.png")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);

            assertThat(result.getBody()).isInstanceOf(Multipart.class);
            Multipart typedResult = (Multipart)result.getBody();
            assertThat(typedResult.getSubType()).isEqualTo("mixed");
        }

        @Test
        void convertToMimeShouldNotHaveInnerMultipartWhenNoInline() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name("ديناصور.png")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .noneMatch(Entity::isMultipart);
        }

        @Test
        void convertToMimeShouldHaveChildrenAttachmentParts() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name("ديناصور.png")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .extracting(Entity::getDispositionType)
                .anySatisfy(contentDisposition -> assertThat(contentDisposition).isEqualTo("attachment"));
        }

        @Test
        void convertToMimeShouldNotThrowWhenNameInContentTypeFieldAndAttachmentMetadata() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name("fgh.png")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png; name=abc.png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            assertThatCode(() -> sut.convertToMime(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session))
                .doesNotThrowAnyException();
        }

        @Test
        void attachmentNameShouldBeOverriddenWhenSpecified() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name("fgh.png")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png; name=abc.png; charset=\"iso-8859-1\"")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo("image/png; charset=iso-8859-1; name=\"=?US-ASCII?Q?fgh.png?=\"");
        }

        @Test
        void nameShouldBeAddedToContentTypeWhenSpecified() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name("fgh.png")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo("image/png; name=\"=?US-ASCII?Q?fgh.png?=\"");
        }

        @Test
        void attachmentNameShouldBePreservedWhenNameNotSpecified() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png; name=abc.png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo("image/png; name=abc.png");
        }

        @Test
        void attachmentNameShouldBeUnspecifiedWhenNone() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo("image/png");
        }

        @Test
        void convertToMimeShouldHaveChildMultipartWhenOnlyInline() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String name = "ديناصور.png";
            String text = "123456";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name(name)
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(true)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .allMatch(Entity::isMultipart)
                .extracting(entity -> (Multipart) entity.getBody())
                .extracting(Multipart::getSubType)
                .allSatisfy(subType -> assertThat(subType).isEqualTo("related"));
        }

        @Test
        void convertToMimeShouldHaveChildMultipartWhenBothInlinesAndAttachments() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter(attachmentContentLoader);

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "inline data";
            MessageAttachmentMetadata inline = MessageAttachmentMetadata.builder()
                .name("ديناصور.png")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId"))
                    .size(text.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid"))
                .isInline(true)
                .build();
            when(attachmentContentLoader.load(inline.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text.getBytes()));


            String text2 = "attachment data";
            MessageAttachmentMetadata attachment = MessageAttachmentMetadata.builder()
                .name("att.pdf")
                .attachment(AttachmentMetadata.builder()
                    .attachmentId(AttachmentId.from("blodId2"))
                    .size(text2.getBytes().length)
                    .type("image/png")
                    .build())
                .cid(Cid.from("cid2"))
                .isInline(false)
                .build();
            when(attachmentContentLoader.load(attachment.getAttachment(), session))
                .thenReturn(new ByteArrayInputStream(text2.getBytes()));


            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(inline, attachment), session);
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(2)
                .satisfies(part -> {
                    Multipart multipartRelated = (Multipart) part.getBody();
                    assertThat(multipartRelated.getSubType()).isEqualTo("related");
                    assertThat(multipartRelated.getBodyParts())
                        .extracting(Entity::getDispositionType)
                        .contains("inline");
                }, Index.atIndex(0))
                .satisfies(part -> {
                    assertThat(part.getDispositionType()).isEqualTo("attachment");
                }, Index.atIndex(1));
        }

        private String getNameParameterValue(Entity attachmentPart) {
            return ((ContentTypeField) attachmentPart.getHeader().getField("Content-Type")).getParameter("name");
        }
    }
}
