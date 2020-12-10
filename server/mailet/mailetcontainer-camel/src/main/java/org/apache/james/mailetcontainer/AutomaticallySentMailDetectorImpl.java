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

package org.apache.james.mailetcontainer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.mdn.MDNReportParser;
import org.apache.james.mdn.sending.mode.DispositionSendingMode;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.mailet.Mail;
import org.apache.mailet.base.AutomaticallySentMailDetector;

public class AutomaticallySentMailDetectorImpl implements AutomaticallySentMailDetector {

    private static final String[] MAILING_LIST_HEADERS = new String[] {
            "List-Help",
            "List-Subscribe",
            "List-Unsubscribe",
            "List-Owner",
            "List-Post",
            "List-Id",
            "List-Archive" };

    @Override
    public boolean isAutomaticallySent(Mail mail) throws MessagingException {
        return !mail.hasSender() ||
            isMailingList(mail) ||
            isAutoSubmitted(mail) ||
            isMdnSentAutomatically(mail);
    }

    @Override
    public boolean isMailingList(Mail mail) throws MessagingException {
        return senderIsMailingList(mail)
            || headerIsMailingList(mail);
    }

    private boolean senderIsMailingList(Mail mail) {
        return mail.getMaybeSender()
            .asOptional()
            .map(MailAddress::getLocalPart)
            .map(localPart ->  localPart.startsWith("owner-")
                || localPart.endsWith("-request")
                || localPart.equalsIgnoreCase("MAILER-DAEMON")
                || localPart.equalsIgnoreCase("LISTSERV")
                || localPart.equalsIgnoreCase("majordomo"))
            .orElse(false);
    }

    private boolean headerIsMailingList(Mail mail) throws MessagingException {
        return mail.getMessage()
            .getMatchingHeaders(MAILING_LIST_HEADERS)
            .hasMoreElements();
    }

    @Override
    public boolean isAutoSubmitted(Mail mail) throws MessagingException {
        String[] headers = mail.getMessage().getHeader(AUTO_SUBMITTED_HEADER);
        if (headers != null) {
            return Arrays.stream(headers)
                .anyMatch(this::isAutoSubmitted);
        }
        return false;
    }

    private boolean isAutoSubmitted(String header) {
        return header.equalsIgnoreCase(AUTO_REPLIED_VALUE)
            || header.equalsIgnoreCase(AUTO_GENERATED_VALUE)
            || header.equalsIgnoreCase(AUTO_NOTIFIED_VALUE);
    }

    @Override
    public boolean isMdnSentAutomatically(Mail mail) throws MessagingException {
        ResultCollector resultCollector = new ResultCollector(false);
        MimeStreamParser parser = new MimeStreamParser(MimeConfig.custom()
            .setMaxContentLen(100 * 1024 * 1024)
            .setMaxHeaderCount(-1)
            .setMaxHeaderLen(-1)
            .setMaxLineLen(-1)
            .setHeadlessParsing(mail.getMessage().getContentType())
            .build());
        parser.setContentHandler(createMdnContentHandler(resultCollector));
        try {
            parser.parse(new MimeMessageInputStream(mail.getMessage()));
        } catch (MimeException e) {
            throw new MessagingException("Can not parse Mime", e);
        } catch (IOException e) {
            throw new MessagingException("Can not read content", e);
        }
        return resultCollector.getResult();
    }

    private AbstractContentHandler createMdnContentHandler(final ResultCollector resultCollector) {
        return new AbstractContentHandler() {
            @Override
            public void body(BodyDescriptor bodyDescriptor, InputStream inputStream) throws MimeException, IOException {
                if (bodyDescriptor.getMimeType().equalsIgnoreCase("message/disposition-notification")) {
                    resultCollector.setResult(MDNReportParser.parse(inputStream, bodyDescriptor.getCharset())
                        .map(report -> report.getDispositionField().getSendingMode() == DispositionSendingMode.Automatic)
                        .getOrElse(() -> false));
                }
            }
        };
    }

    private static class ResultCollector {
        private boolean result;

        public ResultCollector(boolean result) {
            this.result = result;
        }

        public boolean getResult() {
            return result;
        }

        public void setResult(boolean result) {
            this.result = result;
        }
    }

}
