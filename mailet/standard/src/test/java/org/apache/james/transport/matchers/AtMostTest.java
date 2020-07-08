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

package org.apache.james.transport.matchers;

import static org.apache.james.transport.matchers.AtMost.AT_MOST_EXECUTIONS;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class AtMostTest {
    private static final String CONDITION = "2";

    private AtMost matcher;

    private Mail createMail() throws MessagingException {
        return FakeMail.builder()
            .name("test-message")
            .recipient(RECIPIENT1)
            .build();
    }

    @BeforeEach
    void setup() throws MessagingException {
        this.matcher = new AtMost();
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("AtMost")
            .condition(CONDITION)
            .build();
        matcher.init(matcherConfig);
    }

    @Nested
    class WrongConditionConfigurationTest {
        private static final String NO_VALUE_MATCHER = "NoValueMatcher";
        private static final String RETRY_WITHOUT_CONDITION_NAME = ":3";
        private static final String RETRY_WITHOUT_CONDITION_VALUE = "randomName:";
        private static final String RETRY_WITH_SPACE_IN_CONDITION = "  :  ";

        @Test
        void shouldThrowWhenMatchersConfigWithOutConditionValue() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                .matcherName(NO_VALUE_MATCHER)
                .condition(RETRY_WITHOUT_CONDITION_VALUE)
                .build()))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigWithConditionValueAsWord() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                .matcherName(NO_VALUE_MATCHER)
                .condition("value")
                .build()))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigWithNegativeConditionValue() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                .matcherName(NO_VALUE_MATCHER)
                .condition("-87")
                .build()))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigWithoutConditionValue() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                .matcherName(NO_VALUE_MATCHER)
                .condition(RETRY_WITHOUT_CONDITION_VALUE)
                .build()))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigWithoutConditionName() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                .matcherName(NO_VALUE_MATCHER)
                .condition(RETRY_WITHOUT_CONDITION_NAME)
                .build()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigNameAsSpace() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                .matcherName(NO_VALUE_MATCHER)
                .condition(RETRY_WITH_SPACE_IN_CONDITION)
                .build()))
                .isInstanceOf(MessagingException.class);
        }
    }

    @Nested
    class MultiplesMatcherConfigurationTest {
        private static final String MULTIPLE_MATCHERS = "MultipleMatchers";
        private static final String RETRY_3_TIMES = "randomName:3";
        private static final String RETRY_5_TIMES = "randomName:5";

        private AtMost multipleMatchers;

        @BeforeEach
        void setup() throws MessagingException {
            this.multipleMatchers = new AtMost();
            multipleMatchers.init(
                FakeMatcherConfig.builder()
                    .matcherName("MultipleMatchers")
                    .condition(RETRY_3_TIMES)
                    .condition(RETRY_5_TIMES)
                    .build());
        }

        @Test
        void matchersShouldMatchWhenNoRetries() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AttributeName.of(MULTIPLE_MATCHERS), AttributeValue.of(0)));

            Collection<MailAddress> actual = multipleMatchers.match(mail);

            assertThat(actual).containsOnly(RECIPIENT1);
        }

        @Test
        void matchersShouldStopWhenAMatcherReachedLimit() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AttributeName.of(MULTIPLE_MATCHERS), AttributeValue.of(3)));

            Collection<MailAddress> actual = multipleMatchers.match(mail);

            assertThat(actual).containsOnly(RECIPIENT1);
        }

        @Test
        void matchersShouldMatchWhenLimitNotReached() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AttributeName.of("randomName"), AttributeValue.of(2)));

            Collection<MailAddress> actual = multipleMatchers.match(mail);

            assertThat(actual).containsOnly(RECIPIENT1);
        }
    }

    @Nested
    class SingleMatcherConfigurationTest {
        @Test
        void shouldMatchWhenAttributeNotSet() throws MessagingException {
            Mail mail = createMail();

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).containsOnly(RECIPIENT1);
        }

        @Test
        void shouldMatchWhenNoRetries() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AT_MOST_TRIES, AttributeValue.of(0)));

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).containsOnly(RECIPIENT1);
        }

        @Test
        void shouldNotMatchWhenOverAtMost() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AT_MOST_TRIES, AttributeValue.of(3)));

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).isEmpty();
        }

        @Test
        void shouldNotMatchWhenEqualToAtMost() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AT_MOST_TRIES, AttributeValue.of(2)));

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).isEmpty();
        }

        @Test
        void shouldThrowWithEmptyCondition() {
            FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AtMostFailureRetries")
                .build();

            assertThatThrownBy(() -> matcher.init(matcherConfig))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowWithInvalidCondition() {
            FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AtMostFailureRetries")
                .condition("invalid")
                .build();

            assertThatThrownBy(() -> matcher.init(matcherConfig))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWithNegativeCondition() {
            FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AtMostFailureRetries")
                .condition("-1")
                .build();

            assertThatThrownBy(() -> matcher.init(matcherConfig))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWithConditionToZero() {
            FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AtMostFailureRetries")
                .condition("0")
                .build();

            assertThatThrownBy(() -> matcher.init(matcherConfig))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldMatchUntilOverAtMost() throws MessagingException {
            Mail mail = createMail();

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(matcher.match(mail)).describedAs("First execution").contains(RECIPIENT1);
                softly.assertThat(matcher.match(mail)).describedAs("Second execution").contains(RECIPIENT1);
                softly.assertThat(matcher.match(mail)).describedAs("Third execution").isEmpty();
            }));
        }
    }
}
