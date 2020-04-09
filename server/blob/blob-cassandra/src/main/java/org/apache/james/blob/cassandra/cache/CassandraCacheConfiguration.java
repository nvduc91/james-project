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

import java.time.Duration;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class CassandraCacheConfiguration {

    private final Duration timeOut;
    private final int byteThresholdSize;
    private final Duration ttl;

    public static class Builder {
        private final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(50);
        private final Duration DEFAULT_TTL = Duration.ofSeconds(50);
        private final int DEFAULT_BYTE_THRESHOLD_SIZE = 8 * 1000;

        private Optional<Duration> timeout = Optional.empty();
        private Optional<Integer> byteThresholdSize = Optional.empty();
        private Optional<Duration> ttl = Optional.empty();

        public Builder timeOut(Duration timeout) {
            Preconditions.checkNotNull(timeout, "'Threshold size' must not to be null");
            Preconditions.checkArgument(timeout.toMillis() > 0, "'Threshold size' needs to be positive");

            this.timeout = Optional.of(timeout);
            return this;
        }

        public Builder sizeThreshold(int sizeThreshold) {
            Preconditions.checkArgument(sizeThreshold >= 0, "'Threshold size' needs to be positive");

            this.byteThresholdSize = Optional.of(sizeThreshold);
            return this;
        }

        public Builder ttl(Duration ttl) {
            Preconditions.checkNotNull(ttl, "'TTL' must not to be null");
            Preconditions.checkArgument(ttl.getSeconds() > 0, "'TTL' needs to be positive");

            this.ttl = Optional.of(ttl);
            return this;
        }

        public CassandraCacheConfiguration build() {
            return new CassandraCacheConfiguration(
                timeout.orElse(DEFAULT_TIMEOUT),
                byteThresholdSize.orElse(DEFAULT_BYTE_THRESHOLD_SIZE),
                ttl.orElse(DEFAULT_TTL));
        }
    }

    private CassandraCacheConfiguration(Duration timeout, int byteThresholdSize, Duration ttl) {
        this.timeOut = timeout;
        this.byteThresholdSize = byteThresholdSize;
        this.ttl = ttl;
    }

    public Duration getTimeOut() {
        return timeOut;
    }

    public Duration getTtl() {
        return ttl;
    }

    public int getByteThresholdSize() {
        return byteThresholdSize;
    }
}
