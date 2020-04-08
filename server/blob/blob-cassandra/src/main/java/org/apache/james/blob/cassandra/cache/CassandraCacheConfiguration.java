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

import com.google.common.base.Preconditions;

public class CassandraCacheConfiguration {

    private final Duration timeOut;
    private final int sizeThreshold;
    private final int ttl;

    public CassandraCacheConfiguration(Duration timeout, int sizeThreshold, int ttl) {
        this.timeOut = timeout;
        this.sizeThreshold = sizeThreshold;
        this.ttl = ttl;
    }

    public Duration getTimeOut() {
        return timeOut;
    }

    public int getTtl() {
        return ttl;
    }

    public int getSizeThreshold() {
        return sizeThreshold;
    }

    public static class Builder {
        private Duration timeOut;
        private int sizeThreshold;
        private int ttl;

        public Builder timeOut(Duration timeOut) {
            Preconditions.checkNotNull(timeOut, "'Threshold size' must not to be null");
            Preconditions.checkArgument(timeOut.toMillis() > 0, "'Threshold size' needs to be positive");
            this.timeOut = timeOut;
            return this;
        }

        public Builder sizeThreshold(int sizeThreshold) {
            Preconditions.checkArgument(sizeThreshold >= 0, "'Threshold size' needs to be positive");
            this.sizeThreshold = sizeThreshold;
            return this;
        }

        public Builder ttl(int ttl) {
            Preconditions.checkArgument(ttl > 0, "'TTL' needs to be positive");
            this.ttl = ttl;
            return this;
        }

        public CassandraCacheConfiguration build() {
            return new CassandraCacheConfiguration(this.timeOut, this.sizeThreshold, this.ttl);
        }
    }
}
