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
package org.apache.james.blob.cassandra.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public class CassandraCacheConfigurationTest {

    byte[] EIGHT_KILOBYTES = Strings.repeat("01234567\n", 1000).getBytes(StandardCharsets.UTF_8);
    private final Duration DEFAULT_TIME_OUT = Duration.of(50, ChronoUnit.MILLIS);
    private final int DEFAULT_THRESHOLD_SIZE_IN_BYTES = EIGHT_KILOBYTES.length;
    private final Duration _1_SEC_TTL = Duration.ofSeconds(1);
    private final Duration TOO_BIG_TTL = Duration.ofSeconds(Integer.MAX_VALUE + 1L);

    private final Duration NEGATIVE_TIME_OUT = Duration.of(-50, ChronoUnit.MILLIS);
    private final int NEGATIVE_THRESHOLD_SIZE_IN_BYTES = -1 * EIGHT_KILOBYTES.length;
    private final Duration NEGATIVE_TTL = Duration.ofSeconds(-1);

    @Test
    void shouldReturnTheCorrectConfigured() {
        CassandraCacheConfiguration cacheConfiguration = new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(DEFAULT_THRESHOLD_SIZE_IN_BYTES)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(_1_SEC_TTL)
            .build();

        SoftAssertions.assertSoftly(soflty -> {
            assertThat(cacheConfiguration.getReadTimeOut()).isEqualTo(DEFAULT_TIME_OUT);
            assertThat(cacheConfiguration.getSizeThresholdInBytes()).isEqualTo(DEFAULT_THRESHOLD_SIZE_IN_BYTES);
            assertThat(cacheConfiguration.getTtl()).isEqualTo(_1_SEC_TTL);
        });
    }

    @Test
    void shouldThrowWhenConfiguredNegativeTimeout() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(DEFAULT_THRESHOLD_SIZE_IN_BYTES)
            .timeOut(NEGATIVE_TIME_OUT)
            .ttl(_1_SEC_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredNullTimeout() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(DEFAULT_THRESHOLD_SIZE_IN_BYTES)
            .timeOut(null)
            .ttl(_1_SEC_TTL)
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenConfiguredTooBigTTL() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(DEFAULT_THRESHOLD_SIZE_IN_BYTES)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(TOO_BIG_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredNegativeTTL() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(DEFAULT_THRESHOLD_SIZE_IN_BYTES)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(NEGATIVE_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredZeroTTL() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(DEFAULT_THRESHOLD_SIZE_IN_BYTES)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(Duration.ofSeconds(0))
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredNegativeThreshold() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(NEGATIVE_THRESHOLD_SIZE_IN_BYTES)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(_1_SEC_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }
}