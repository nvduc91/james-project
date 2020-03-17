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

package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class VersionTest {
    @Test
    void ofShouldReturnCorrectValue() {
        Optional<String> version = Optional.of("rfc-8621");

        assertThat(Version.of(version)).isEqualTo(Version.RFC8621);
    }

    @Test
    void ofShouldThrowWhenVersionNotKnown() {
        Optional<String> version = Optional.of("unknown");

        assertThatThrownBy(() -> Version.of(version))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofShouldReturnDefaultWhenNoVersion() {
        Optional<String> version = Optional.empty();

        assertThat(Version.of(version)).isEqualTo(Version.DRAFT);
    }
}
