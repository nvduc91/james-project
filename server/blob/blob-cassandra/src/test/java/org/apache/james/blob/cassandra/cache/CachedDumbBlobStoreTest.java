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

import static org.apache.james.blob.api.BucketName.DEFAULT;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TWELVE_MEGABYTES;
import static org.apache.james.blob.cassandra.cache.DumbBlobStoreCacheContract.EIGHT_KILOBYTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.DumbBlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBucketDAO;
import org.apache.james.blob.cassandra.CassandraDefaultBucketDAO;
import org.apache.james.blob.cassandra.CassandraDumbBlobStore;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

public class CachedDumbBlobStoreTest implements DumbBlobStoreContract {

    private static final int CHUNK_SIZE = 10240;
    private static final BucketName DEFAULT_BUCKERNAME = DEFAULT;
    private static final BucketName TEST_BUCKERNAME = BucketName.of("test");

    private static CassandraClusterExtension initCassandraClusterExtension() {
        new CassandraClusterExtension(CassandraDumbBlobCacheModule.MODULE);
        return new CassandraClusterExtension(CassandraBlobModule.MODULE);
    }

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = initCassandraClusterExtension();

    private DumbBlobStore testee;
    private CassandraDefaultBucketDAO defaultBucketDAO;
    private DumbBlobStore backend;
    private DumbBlobStoreCache cached;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        HashBlobId.Factory blobIdFactory = new HashBlobId.Factory();
        CassandraBucketDAO bucketDAO = new CassandraBucketDAO(blobIdFactory, cassandra.getConf());
        defaultBucketDAO = new CassandraDefaultBucketDAO(cassandra.getConf());
        backend = new CassandraDumbBlobStore(
            defaultBucketDAO,
            bucketDAO,
            CassandraConfiguration.builder()
                .blobPartSize(CHUNK_SIZE)
                .build(),
            DEFAULT);
        CassandraCacheConfiguration cacheConfig = new CassandraCacheConfiguration.Builder().build();
        cached = new CassandraDumbBlobStoreCache(cassandra.getConf(), cacheConfig);
        testee = new CachedDumbBlobStore(cached, backend, cacheConfig, bucketDAO, DEFAULT);
    }

    @Override
    public DumbBlobStore testee() {
        return testee;
    }

    @Test
    public void shouldCacheWhenDefaultBucketName() {
        assertThatCode(Mono.from(testee().save(DEFAULT_BUCKERNAME, TEST_BLOB_ID, EIGHT_KILOBYTES))::block)
            .doesNotThrowAnyException();

        byte[] actual = Mono.from(cached.read(TEST_BLOB_ID)).block();
        assertThat(actual).containsExactly(EIGHT_KILOBYTES);
    }

    @Test
    public void shouldNotCacheWhenNotDefaultBucketName() {
        assertThatCode(Mono.from(testee().save(TEST_BUCKERNAME, TEST_BLOB_ID, EIGHT_KILOBYTES))::block)
            .doesNotThrowAnyException();

        SoftAssertions.assertSoftly(ignored -> {
            assertThat(Mono.from(cached.read(TEST_BLOB_ID)).blockOptional()).isEmpty();
            assertThat(Mono.from(backend.readBytes(TEST_BUCKERNAME, TEST_BLOB_ID)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldNotCacheWhenDefaultBucketNameAndBigByteData() {
        assertThatCode(Mono.from(testee().save(DEFAULT_BUCKERNAME, TEST_BLOB_ID, TWELVE_MEGABYTES))::block)
            .doesNotThrowAnyException();

        SoftAssertions.assertSoftly(ignored -> {
            assertThat(Mono.from(cached.read(TEST_BLOB_ID)).blockOptional()).isEmpty();
            assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKERNAME, TEST_BLOB_ID)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldSavedBothInCacheAndCassandra() {
        assertThatCode(Mono.from(testee().save(DEFAULT_BUCKERNAME, TEST_BLOB_ID, EIGHT_KILOBYTES))::block)
            .doesNotThrowAnyException();

        SoftAssertions.assertSoftly(soflty -> {
            assertThat(Mono.from(cached.read(TEST_BLOB_ID)).block()).containsExactly(EIGHT_KILOBYTES);
            assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKERNAME, TEST_BLOB_ID)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldRemoveBothInCacheAndCassandraWhenDefaultBucketName() {
        SoftAssertions.assertSoftly(ignored -> {
            assertThatCode(Mono.from(testee().save(DEFAULT_BUCKERNAME, TEST_BLOB_ID, EIGHT_KILOBYTES))::block)
                .doesNotThrowAnyException();
            assertThatCode(Mono.from(testee().delete(DEFAULT_BUCKERNAME, TEST_BLOB_ID))::block)
                .doesNotThrowAnyException();
            assertThat(Mono.from(cached.read(TEST_BLOB_ID)).blockOptional()).isEmpty();
            assertThatThrownBy(() -> Mono.from(backend.readBytes(DEFAULT_BUCKERNAME, TEST_BLOB_ID)).block())
                .isInstanceOf(ObjectNotFoundException.class);
        });
    }
}
