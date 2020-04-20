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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.blob.api.BucketName.DEFAULT;
import static org.apache.james.blob.api.DumbBlobStoreFixture.TEST_BLOB_ID;
import static org.apache.james.blob.cassandra.cache.DumbBlobStoreCacheContract.EIGHT_KILOBYTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreContract;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobStore;
import org.apache.james.blob.cassandra.CassandraBucketDAO;
import org.apache.james.blob.cassandra.CassandraDefaultBucketDAO;
import org.apache.james.blob.cassandra.CassandraDumbBlobStore;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class CachedBlobStoreTest implements BlobStoreContract {

    private static final int CHUNK_SIZE = 10240;
    private static final BucketName DEFAULT_BUCKERNAME = DEFAULT;
    private static final BucketName TEST_BUCKERNAME = BucketName.of("test");

    private static CassandraClusterExtension initCassandraClusterExtension() {
        new CassandraClusterExtension(CassandraDumbBlobCacheModule.MODULE);
        return new CassandraClusterExtension(CassandraBlobModule.MODULE);
    }

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = initCassandraClusterExtension();

    private BlobStore testee;
    private BlobStore backend;
    private DumbBlobStoreCache cached;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        backend = CassandraBlobStore.forTesting(cassandra.getConf());
        CassandraCacheConfiguration cacheConfig = new CassandraCacheConfiguration.Builder()
            .sizeThresholdInBytes(EIGHT_KILOBYTES.length +1)
            .build();
        cached = new CassandraDumbBlobStoreCache(cassandra.getConf(), cacheConfig);
        testee = new CachedBlobStore(cached, backend, cacheConfig, DEFAULT);
    }

    @Override
    public BlobStore testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }

    @Test
    public void shouldCacheWhenDefaultBucketName() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKERNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        byte[] actual = Mono.from(cached.read(blobId)).block();
        assertThat(actual).containsExactly(EIGHT_KILOBYTES);
    }

    @Test
    public void shouldNotCacheWhenNotDefaultBucketName() {
        BlobId blobId = Mono.from(testee().save(TEST_BUCKERNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(ignored -> {
            assertThat(Mono.from(cached.read(blobId)).blockOptional()).isEmpty();
            assertThat(Mono.from(backend.readBytes(TEST_BUCKERNAME, blobId)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldNotCacheWhenDefaultBucketNameAndBigByteData() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKERNAME, TWELVE_MEGABYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(ignored -> {
            assertThat(Mono.from(cached.read(blobId)).blockOptional()).isEmpty();
            assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKERNAME, blobId)).block()).containsExactly(TWELVE_MEGABYTES);
        });
    }

    @Test
    public void shouldSavedBothInCacheAndBackend() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKERNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(soflty -> {
            assertThat(Mono.from(cached.read(blobId)).block()).containsExactly(EIGHT_KILOBYTES);
            assertThat(Mono.from(backend.readBytes(DEFAULT_BUCKERNAME, blobId)).block()).containsExactly(EIGHT_KILOBYTES);
        });
    }

    @Test
    public void shouldRemoveBothInCacheAndBackendWhenDefaultBucketName() {
        BlobId blobId = Mono.from(testee().save(DEFAULT_BUCKERNAME, EIGHT_KILOBYTES, SIZE_BASED)).block();

        SoftAssertions.assertSoftly(ignored -> {
            assertThatCode(Mono.from(testee().delete(DEFAULT_BUCKERNAME, blobId))::block)
                .doesNotThrowAnyException();
            assertThat(Mono.from(cached.read(blobId)).blockOptional()).isEmpty();
            assertThatThrownBy(() -> Mono.from(backend.readBytes(DEFAULT_BUCKERNAME, blobId)).block())
                .isInstanceOf(ObjectNotFoundException.class);
        });
    }
}
