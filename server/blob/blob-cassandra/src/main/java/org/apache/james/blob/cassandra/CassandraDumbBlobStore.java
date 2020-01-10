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

package org.apache.james.blob.cassandra;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.NoSuchElementException;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.IOObjectStoreException;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.cassandra.utils.DataChunker;
import org.apache.james.util.ReactorUtils;

import com.datastax.driver.core.Session;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

public class CassandraDumbBlobStore implements DumbBlobStore {

    private static final int PREFETCH = 16;
    private static final int MAX_CONCURRENCY = 1;
    private final CassandraDefaultBucketDAO defaultBucketDAO;
    private final CassandraBucketDAO bucketDAO;
    private final DataChunker dataChunker;
    private final CassandraConfiguration configuration;
    private final HashBlobId.Factory blobIdFactory;
    private final BucketName defaultBucket;

    @Inject
    CassandraDumbBlobStore(CassandraDefaultBucketDAO defaultBucketDAO,
                           CassandraBucketDAO bucketDAO,
                           CassandraConfiguration cassandraConfiguration,
                           HashBlobId.Factory blobIdFactory,
                           BucketName defaultBucket) {
        this.defaultBucketDAO = defaultBucketDAO;
        this.bucketDAO = bucketDAO;
        this.configuration = cassandraConfiguration;
        this.blobIdFactory = blobIdFactory;
        this.defaultBucket = defaultBucket;
        this.dataChunker = new DataChunker();
    }

    @VisibleForTesting
    public CassandraDumbBlobStore(Session session, BucketName defaultBucket) {
        this(new CassandraDefaultBucketDAO(session),
            new CassandraBucketDAO(new HashBlobId.Factory(), session),
            CassandraConfiguration.DEFAULT_CONFIGURATION,
            new HashBlobId.Factory(),
            defaultBucket);
    }


    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws IOObjectStoreException, ObjectNotFoundException {
        return ReactorUtils.toInputStream(readBlobParts(bucketName, blobId));
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return readBlobParts(bucketName, blobId)
            .collectList()
            .map(this::byteBuffersToBytesArray);
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        Preconditions.checkNotNull(data);

        return Mono.fromCallable(() -> dataChunker.chunk(data, configuration.getBlobPartSize()))
            .flatMap(chunks -> save(bucketName, blobId, chunks));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(inputStream);

        return Mono.fromCallable(() -> dataChunker.chunkStream(inputStream, configuration.getBlobPartSize()))
            .flatMap(chunks -> save(bucketName, blobId, chunks))
            .onErrorMap(e -> new IOObjectStoreException("Exception occurred while saving input stream", e));
    }

    @Override
    public Mono<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        return Mono.using(content::openBufferedStream,
            stream -> save(bucketName, blobId, stream),
            Throwing.consumer(InputStream::close).sneakyThrow());
    }

    private Mono<Void> save(BucketName bucketName, BlobId blobId, Flux<ByteBuffer> chunksAsFlux) {
        return saveBlobParts(bucketName, blobId, chunksAsFlux)
            .flatMap(numberOfChunk -> saveBlobPartReference(bucketName, blobId, numberOfChunk));
    }

    private Mono<Integer> saveBlobParts(BucketName bucketName, BlobId blobId, Flux<ByteBuffer> chunksAsFlux) {
        return chunksAsFlux
            .publishOn(Schedulers.elastic(), PREFETCH)
            .index()
            .flatMap(pair -> writePart(bucketName, blobId, pair.getT1().intValue(), pair.getT2()).thenReturn(getChunkNum(pair)))
            .count()
            .map(Long::intValue);
    }

    private Mono<Void> writePart(BucketName bucketName, BlobId blobId, int position, ByteBuffer data) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.writePart(data, blobId, position);
        } else {
            return bucketDAO.writePart(data, bucketName, blobId, position);
        }
    }

    private Integer getChunkNum(Tuple2<Long, ByteBuffer> pair) {
        return pair.getT1().intValue();
    }

    private Mono<Void> saveBlobPartReference(BucketName bucketName, BlobId blobId, Integer numberOfChunk) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.saveBlobPartsReferences(blobId, numberOfChunk);
        } else {
            return bucketDAO.saveBlobPartsReferences(bucketName, blobId, numberOfChunk);
        }
    }

    private boolean isDefaultBucket(BucketName bucketName) {
        return bucketName.equals(defaultBucket);
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.deletePosition(blobId)
                .then(defaultBucketDAO.deleteParts(blobId));
        } else {
            return bucketDAO.deletePosition(bucketName, blobId)
                .then(bucketDAO.deleteParts(bucketName, blobId));
        }
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkArgument(!isDefaultBucket(bucketName), "Deleting the default bucket is forbidden");

        return bucketDAO.listAll()
            .filter(bucketNameBlobIdPair -> bucketNameBlobIdPair.getKey().equals(bucketName))
            .map(Pair::getValue)
            .flatMap(blobId -> delete(bucketName, blobId))
            .then();
    }

    private Mono<ByteBuffer> readPart(BucketName bucketName, BlobId blobId, Integer partIndex) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.readPart(blobId, partIndex);
        } else {
            return bucketDAO.readPart(bucketName, blobId, partIndex);
        }
    }

    private Mono<Integer> selectRowCount(BucketName bucketName, BlobId blobId) {
        if (isDefaultBucket(bucketName)) {
            return defaultBucketDAO.selectRowCount(blobId);
        } else {
            return bucketDAO.selectRowCount(bucketName, blobId);
        }
    }

    private Flux<ByteBuffer> readBlobParts(BucketName bucketName, BlobId blobId) {
        Integer rowCount = selectRowCount(bucketName, blobId)
            .publishOn(Schedulers.elastic())
            .single()
            .onErrorResume(NoSuchElementException.class, e -> Mono.error(
                new ObjectNotFoundException(String.format("Could not retrieve blob metadata for %s", blobId))))
            .block();
        return Flux.range(0, rowCount)
            .publishOn(Schedulers.elastic(), PREFETCH)
            .flatMapSequential(partIndex -> readPart(bucketName, blobId, partIndex)
                    .single()
                    .onErrorResume(NoSuchElementException.class, e -> Mono.error(
                        new ObjectNotFoundException(String.format("Missing blob part for blobId %s and position %d", blobId, partIndex)))),
                MAX_CONCURRENCY, PREFETCH);
    }

    private byte[] byteBuffersToBytesArray(List<ByteBuffer> byteBuffers) {
        int targetSize = byteBuffers
            .stream()
            .mapToInt(ByteBuffer::remaining)
            .sum();

        return byteBuffers
            .stream()
            .reduce(ByteBuffer.allocate(targetSize), (accumulator, element) -> accumulator.put(element))
            .array();
    }
}
