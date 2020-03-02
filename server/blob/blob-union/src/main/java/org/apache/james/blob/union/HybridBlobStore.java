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

package org.apache.james.blob.union;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class HybridBlobStore implements BlobStore {
    @FunctionalInterface
    public interface RequireLowCost {
        RequireHighPerformance lowCost(BlobStore blobStore);
    }

    @FunctionalInterface
    public interface RequireHighPerformance {
        RequireConfiguration highPerformance(BlobStore blobStore);
    }

    @FunctionalInterface
    public interface RequireConfiguration {
        Builder configuration(Configuration configuration);
    }

    public static class Builder {
        private final BlobStore lowCostBlobStore;
        private final BlobStore highPerformanceBlobStore;
        private final Configuration configuration;

        Builder(BlobStore lowCostBlobStore, BlobStore highPerformanceBlobStore, Configuration configuration) {
            this.lowCostBlobStore = lowCostBlobStore;
            this.highPerformanceBlobStore = highPerformanceBlobStore;
            this.configuration = configuration;
        }

        public HybridBlobStore build() {
            return new HybridBlobStore(
                lowCostBlobStore,
                highPerformanceBlobStore,
                configuration);
        }
    }

    public static class Configuration {
        public static final int DEFAULT_SIZE_THRESHOLD = 32 * 1024;
        public static final boolean DEFAULT_DUPLICATE_WRITE = false;
        public static final Configuration DEFAULT = new Configuration(DEFAULT_SIZE_THRESHOLD, DEFAULT_DUPLICATE_WRITE);
        private static final String PROPERTY_NAME = "hybrid.size.threshold";
        private static final String DUPLICATE_WRITE_PROPERTY = "hybrid.duplicate.writes";

        public static Configuration from(org.apache.commons.configuration2.Configuration propertiesConfiguration) {
            return new Configuration(propertiesConfiguration.getInteger(PROPERTY_NAME, DEFAULT_SIZE_THRESHOLD),
                propertiesConfiguration.getBoolean(DUPLICATE_WRITE_PROPERTY, DEFAULT_DUPLICATE_WRITE));
        }

        private final int sizeThreshold;
        private final boolean duplicateWrite;

        public Configuration(int sizeThreshold, boolean duplicateWrite) {
            Preconditions.checkArgument(sizeThreshold >= 0, "'" + PROPERTY_NAME + "' needs to be positive");

            this.sizeThreshold = sizeThreshold;
            this.duplicateWrite = duplicateWrite;
        }

        public int getSizeThreshold() {
            return sizeThreshold;
        }

        public boolean isDuplicateWrite() {
            return duplicateWrite;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Configuration) {
                Configuration that = (Configuration) o;

                return Objects.equals(this.sizeThreshold, that.sizeThreshold)
                    && Objects.equals(this.duplicateWrite, that.duplicateWrite);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(sizeThreshold, duplicateWrite);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(HybridBlobStore.class);

    public static RequireLowCost builder() {
        return lowCost -> highPerformance -> configuration -> new Builder(lowCost, highPerformance, configuration);
    }

    private final BlobStore lowCostBlobStore;
    private final BlobStore highPerformanceBlobStore;
    private final Configuration configuration;

    private HybridBlobStore(BlobStore lowCostBlobStore, BlobStore highPerformanceBlobStore, Configuration configuration) {
        this.lowCostBlobStore = lowCostBlobStore;
        this.highPerformanceBlobStore = highPerformanceBlobStore;
        this.configuration = configuration;
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy) {
        return Flux.from(selectBlobStores(storagePolicy, Mono.just(data.length > configuration.getSizeThreshold())))
            .flatMap(blobStore -> blobStore.save(bucketName, data, storagePolicy))
            .distinct()
            .single();
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy) {
        Preconditions.checkNotNull(data);

        Supplier<byte[]> byteSupplier = () -> {
            try {
                return readAllBytes(new BufferedInputStream(data, configuration.getSizeThreshold() + 1));
            } catch (IOException e) {
                LOGGER.error("Error when reading bytes from InputStream, cause: {}", e.getMessage());
            }
            return new byte[0];
        };

        return save(bucketName, byteSupplier.get(), storagePolicy);
    }

    private Publisher<BlobStore> selectBlobStores(StoragePolicy storagePolicy, Mono<Boolean> largeData) {
        switch (storagePolicy) {
            case LOW_COST:
                return Mono.just(lowCostBlobStore);

            case SIZE_BASED:
                return largeData.flux()
                    .filter(Boolean::booleanValue)
                    .map(ignored -> lowCostBlobStore)
                    .switchIfEmpty(
                        Flux.just(configuration.isDuplicateWrite())
                            .filter(duplicateWrite -> !duplicateWrite)
                            .map(ignored -> highPerformanceBlobStore)
                            .switchIfEmpty(Flux.just(highPerformanceBlobStore, lowCostBlobStore)));

            case HIGH_PERFORMANCE:
                return Flux.just(this.configuration.isDuplicateWrite())
                    .filter(duplicateWrite -> !duplicateWrite)
                    .map(ignored -> highPerformanceBlobStore)
                    .switchIfEmpty(Flux.just(highPerformanceBlobStore, lowCostBlobStore));

            default:
                throw new RuntimeException("Unknown storage policy: " + storagePolicy);
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[inputStream.available()];
        inputStream.read(bytes);
        return bytes;
    }

    @Override
    public BucketName getDefaultBucketName() {
        Preconditions.checkState(
            lowCostBlobStore.getDefaultBucketName()
                .equals(highPerformanceBlobStore.getDefaultBucketName()),
            "lowCostBlobStore and highPerformanceBlobStore doen't have same defaultBucketName which could lead to " +
                "unexpected result when interact with other APIs");

        return lowCostBlobStore.getDefaultBucketName();
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.defer(() -> Mono.from(highPerformanceBlobStore.readBytes(bucketName, blobId)))
            .onErrorResume(this::logAndReturnEmpty)
            .switchIfEmpty(Mono.defer(() -> Mono.from(lowCostBlobStore.readBytes(bucketName, blobId))));
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) {
        try {
            return highPerformanceBlobStore.read(bucketName, blobId);
        } catch (ObjectNotFoundException e) {
            return lowCostBlobStore.read(bucketName, blobId);
        } catch (Exception e) {
            LOGGER.error("Error reading {} {} in {}, falling back to {}", bucketName, blobId, highPerformanceBlobStore, lowCostBlobStore);
            return lowCostBlobStore.read(bucketName, blobId);
        }
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        return Mono.defer(() -> Mono.from(lowCostBlobStore.deleteBucket(bucketName)))
            .and(highPerformanceBlobStore.deleteBucket(bucketName))
            .onErrorResume(this::logDeleteFailureAndReturnEmpty);
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        return Mono.defer(() -> Mono.from(lowCostBlobStore.delete(bucketName, blobId)))
            .and(highPerformanceBlobStore.delete(bucketName, blobId))
            .onErrorResume(this::logDeleteFailureAndReturnEmpty);
    }

    private <T> Mono<T> logAndReturnEmpty(Throwable throwable) {
        LOGGER.error("error happens from current blob store, fall back to lowCost blob store", throwable);
        return Mono.empty();
    }

    private <T> Mono<T> logDeleteFailureAndReturnEmpty(Throwable throwable) {
        LOGGER.error("Cannot delete from either lowCost or highPerformance blob store", throwable);
        return Mono.empty();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("lowCostBlobStore", lowCostBlobStore)
            .add("highPerformanceBlobStore", highPerformanceBlobStore)
            .toString();
    }
}
