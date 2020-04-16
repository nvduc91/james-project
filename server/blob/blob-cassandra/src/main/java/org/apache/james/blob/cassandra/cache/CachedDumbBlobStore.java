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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PushbackInputStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.apache.james.blob.cassandra.CassandraBucketDAO;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

public class CachedDumbBlobStore implements DumbBlobStore {

    public static final String DEFAULT_BUCKET = "cassandraDefault";

    private final DumbBlobStoreCache cache;
    private final DumbBlobStore backend;
    private final Integer sizeThresholdInBytes;
    private final BucketName defaultBucket;
    private final CassandraBucketDAO bucketDAO;

    @Inject
    public CachedDumbBlobStore(DumbBlobStoreCache cache, DumbBlobStore backend,
                               CassandraCacheConfiguration cacheConfiguration,
                               CassandraBucketDAO bucketDAO,
                               @Named(DEFAULT_BUCKET) BucketName defaultBucket) {
        this.cache = cache;
        this.backend = backend;
        this.sizeThresholdInBytes = cacheConfiguration.getSizeThresholdInBytes();
        this.defaultBucket = defaultBucket;
        this.bucketDAO = bucketDAO;
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        Preconditions.checkNotNull(bucketName, "bucketName should not be null");

        return Mono.just(bucketName)
            .filter(defaultBucket::equals)
            .flatMap(bucker ->
                Mono.from(cache.read(blobId))
                    .<InputStream>flatMap(bytes -> Mono.fromCallable(() -> new ByteArrayInputStream(bytes)))
                    .switchIfEmpty(Mono.fromCallable(() -> backend.read(bucketName, blobId)))
            )
            .switchIfEmpty(Mono.fromCallable(() -> backend.read(bucketName, blobId)))
            .blockOptional()
            .orElseThrow(() -> new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId)));
    }

    @Override
    public Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(cache.read(blobId))
            .switchIfEmpty(Mono.from(backend.readBytes(bucketName, blobId)));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        return Mono.from(backend.save(bucketName, blobId, data))
            .then(Mono.just(bucketName)
                .filter(bucket -> bucket.equals(defaultBucket) && data.length <= sizeThresholdInBytes)
                .flatMap(ignored -> Mono.from(cache.cache(blobId, data))));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.from(backend.save(bucketName, blobId, inputStream))
            .then(Mono.just(inputStream)
                .flatMap(stream -> handleCache(bucketName, blobId, stream)));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.from(backend.save(bucketName, blobId, content))
            .then(Mono.fromCallable(content::openBufferedStream)
                .flatMap(stream -> handleCache(bucketName, blobId, stream)));
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.from(backend.delete(bucketName, blobId))
            .then(Mono.from(cache.remove(blobId)));
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return Mono.from(backend.deleteBucket(bucketName))
            .then(Mono.just(bucketName)
                .filter(defaultBucket::equals)
                .map(ignored ->
                    bucketDAO.listAll()
                        .filter(bucketNameBlobIdPair -> bucketNameBlobIdPair.getKey().equals(bucketName))
                        .map(Pair::getValue)
                        .flatMap(cache::remove)
                ).then());
    }

    private Mono<Void> handleCache(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        return Mono.fromCallable(() -> {
            byte[] bytes = new byte[sizeThresholdInBytes + 1];
            PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
            int read = pushbackInputStream.read(bytes);
            pushbackInputStream.unread(read);
            return bytes;
        })
            .filter(bytes -> (bucketName.equals(defaultBucket) && bytes.length <= sizeThresholdInBytes))
            .flatMap(bytes -> Mono.from(cache.cache(blobId, bytes)));
    }
}
