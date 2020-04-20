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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CachedBlobStore implements BlobStore {

    private static final String DEFAULT_BUCKET = "cassandraDefault";
    private static final byte[] EMPTY_BYTES = new byte[0];

    private final DumbBlobStoreCache cache;
    private final BlobStore backend;
    private final Integer sizeThresholdInBytes;
    private final BucketName defaultBucket;

    @Inject
    public CachedBlobStore(DumbBlobStoreCache cache, BlobStore backend,
                           CassandraCacheConfiguration cacheConfiguration,
                           @Named(DEFAULT_BUCKET) BucketName defaultBucket) {
        this.cache = cache;
        this.backend = backend;
        this.sizeThresholdInBytes = cacheConfiguration.getSizeThresholdInBytes();
        this.defaultBucket = defaultBucket;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        Preconditions.checkNotNull(bucketName, "bucketName should not be null");

        return isDefaultBucketName(bucketName)
            .flatMap(ignored ->
                Mono.from(cache.read(blobId))
                    .<InputStream>flatMap(bytes -> Mono.fromCallable(() -> new ByteArrayInputStream(bytes)))
            )
            .switchIfEmpty(Mono.fromCallable(() -> backend.read(bucketName, blobId)))
            .blockOptional()
            .orElseThrow(() -> new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId)));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return isDefaultBucketName(bucketName)
            .flatMap(ignored -> Mono.from(cache.read(blobId)))
            .switchIfEmpty(Mono.from(backend.readBytes(bucketName, blobId)));
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] bytes, StoragePolicy storagePolicy) {
        return Mono.from(backend.save(bucketName, bytes, storagePolicy))
            .flatMap(blobId -> {
                if (isCacheAble(bucketName, bytes, storagePolicy)) {
                    return Mono.from(cache.cache(blobId, bytes)).thenReturn(blobId);
                }
                return Mono.just(blobId);
            });
    }

    @Override
    public Publisher<BlobId> save(BucketName bucketName, InputStream inputStream, StoragePolicy storagePolicy) {
        Preconditions.checkNotNull(inputStream, "InputStream must not be null");

        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, sizeThresholdInBytes + 1);
        return Mono.from(backend.save(bucketName, pushbackInputStream, storagePolicy))
            .flatMap(blobId ->
                Mono.fromCallable(() -> isALargeStream(pushbackInputStream))
                    .flatMap(largeStream -> {
                        if (!largeStream && isCacheAble(bucketName, storagePolicy)) {
                            return Mono.from(handleCache(bucketName, blobId, pushbackInputStream, storagePolicy))
                                .thenReturn(blobId);
                        }
                        return Mono.just(blobId);
                    })
            );
    }

    @Override
    public BucketName getDefaultBucketName() {
        return defaultBucket;
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.from(backend.delete(bucketName, blobId))
            .then(isDefaultBucketName(bucketName)
                .flatMap(ignored -> Mono.from(cache.remove(blobId)))
                .then());
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return Mono.from(backend.deleteBucket(bucketName));
    }

    private Mono<BucketName> isDefaultBucketName(BucketName bucketName) {
        return Mono.just(bucketName)
            .filter(defaultBucket::equals);
    }

    private Mono<Void> handleCache(BucketName bucketName, BlobId blobId, PushbackInputStream pushbackInputStream, StoragePolicy storagePolicy) {
        return Mono.fromCallable(() -> copyBytesFromStream(pushbackInputStream))
            .filter(bytes -> isCacheAble(bucketName, bytes, storagePolicy))
            .flatMap(bytes -> Mono.from(cache.cache(blobId, bytes)));
    }

    private byte[] copyBytesFromStream(PushbackInputStream pushbackInputStream) throws IOException {
        byte[] bytes = new byte[sizeThresholdInBytes];
        int read = IOUtils.read(pushbackInputStream, bytes);
        pushbackInputStream.unread(read);
        return bytes;
    }

    private boolean isALargeStream(PushbackInputStream pushbackInputStream) throws IOException {
        pushbackInputStream.mark(0);
        long skip = pushbackInputStream.skip(sizeThresholdInBytes + 1);
        pushbackInputStream.unread(Math.toIntExact(skip));
        return skip >= sizeThresholdInBytes;
    }

    private boolean isCacheAble(BucketName bucketName, byte[] bytes, StoragePolicy storagePolicy) {
        return isCacheAble(bucketName, storagePolicy) && bytes.length <= sizeThresholdInBytes;
    }

    private boolean isCacheAble(BucketName bucketName, StoragePolicy storagePolicy) {
        return defaultBucket.equals(bucketName) && !storagePolicy.equals(LOW_COST);
    }
}
