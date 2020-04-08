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

import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public class CassandraCacheConfigurationTest {

    byte[] EIGHT_KILOBYTES = Strings.repeat("01234567\n", 1000).getBytes(StandardCharsets.UTF_8);
    private final Duration DEFAULT_TIME_OUT = Duration.of(50, ChronoUnit.MILLIS);
    private final int DEFAULT_THRESHOLD = EIGHT_KILOBYTES.length;
    private final int _10_SEC_TTL = 10;

    private final Duration NEGATIVE_TIME_OUT = Duration.of(-50, ChronoUnit.MILLIS);
    private final int NEGATIVE_THRESHOLD = -1 * EIGHT_KILOBYTES.length;
    private final int NEGATIVE_TTL = -10;

    private final CassandraCacheConfiguration DEFAULT_CONFIG = new CassandraCacheConfiguration(DEFAULT_TIME_OUT, DEFAULT_THRESHOLD, _10_SEC_TTL);

    @Test
    void shouldReturnTheSameAsConfigured() {
        CassandraCacheConfiguration cacheConfiguration = new CassandraCacheConfiguration.Builder()
            .sizeThreshold(DEFAULT_THRESHOLD)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(_10_SEC_TTL)
            .build();

        assertThat(cacheConfiguration).hasFieldOrPropertyWithValue("timeOut", DEFAULT_TIME_OUT);
        assertThat(cacheConfiguration).hasFieldOrPropertyWithValue("sizeThreshold", DEFAULT_THRESHOLD);
        assertThat(cacheConfiguration).hasFieldOrPropertyWithValue("ttl", _10_SEC_TTL);
    }

    @Test
    void shouldThrowWhenConfiguredNegativeTimeout() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThreshold(DEFAULT_THRESHOLD)
            .timeOut(NEGATIVE_TIME_OUT)
            .ttl(_10_SEC_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredNullTimeout() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThreshold(DEFAULT_THRESHOLD)
            .timeOut(null)
            .ttl(_10_SEC_TTL)
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowWhenConfiguredNegativeTTL() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThreshold(DEFAULT_THRESHOLD)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(NEGATIVE_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredZeroTTL() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThreshold(DEFAULT_THRESHOLD)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(0)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenConfiguredNegativeThreshold() {
        assertThatThrownBy(() -> new CassandraCacheConfiguration.Builder()
            .sizeThreshold(NEGATIVE_THRESHOLD)
            .timeOut(DEFAULT_TIME_OUT)
            .ttl(_10_SEC_TTL)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
    }
}
