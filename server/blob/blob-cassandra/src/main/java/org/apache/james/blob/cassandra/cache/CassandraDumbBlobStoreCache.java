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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.ttl;
import static org.apache.james.blob.cassandra.BlobTables.BucketBlobTable.ID;
import static org.apache.james.blob.cassandra.BlobTables.DumbBlobCache.DATA;
import static org.apache.james.blob.cassandra.BlobTables.DumbBlobCache.TTL;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.cassandra.BlobTables;
import org.reactivestreams.Publisher;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CassandraDumbBlobStoreCache implements DumbBlobStoreCache {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement deleteStatement;
    private final CassandraCacheConfiguration cacheConfiguration;

    @Inject
    public CassandraDumbBlobStoreCache(Session session, CassandraCacheConfiguration cacheConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.insertStatement = prepareInsert(session);
        this.selectStatement = prepareSelect(session);
        this.deleteStatement = prepareDelete(session);
        this.cacheConfiguration = cacheConfiguration;
    }

    @Override
    public Publisher<Void> cache(BlobId blobId, byte[] bytes) {
        Preconditions.checkNotNull(bytes, "Data must not be null");
        Preconditions.checkArgument(bytes.length > 0, "Data must not be empty");

        return Mono.just(bytes)
            .flatMap(data -> {
                if (data.length > cacheConfiguration.getSizeThreshold()) {
                    return Mono.empty();
                } else {
                    return Mono.from(save(blobId, ByteBuffer.wrap(data, 0, data.length)));
                }
            });
    }

    private Publisher<Void> save(BlobId blobId, ByteBuffer data) {
        return cassandraAsyncExecutor.executeVoid(
            insertStatement.bind()
                .setString(ID, blobId.asString())
                .setBytes(DATA, data)
                .setInt(TTL, cacheConfiguration.getTtl())
                .setConsistencyLevel(ConsistencyLevel.ONE));
    }

    @Override
    public Publisher<Void> cache(BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(inputStream);

        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream, cacheConfiguration.getSizeThreshold() + 1);
        return Mono.fromCallable(() -> isGreaterThanThreshold(bufferedInputStream))
            .filter(bigStream -> !bigStream)
            .flatMap(ignored ->
                Mono.fromCallable(() -> IOUtils.toByteArray(bufferedInputStream))
                    .flatMap(bytes -> Mono.from(save(blobId, ByteBuffer.wrap(bytes, 0, bytes.length)))))
            .switchIfEmpty(Mono.empty())
            .doFinally(any -> closeInputStreamQuite(inputStream));

    }

    @Override
    public Publisher<byte[]> read(BlobId blobId) {
        return cassandraAsyncExecutor
            .executeSingleRow(
                selectStatement.bind()
                    .setString(ID, blobId.asString())
                    .setConsistencyLevel(ConsistencyLevel.ONE))
            .map(row -> row.getBytes(DATA).array())
            .timeout(cacheConfiguration.getTimeOut(), Mono.empty());
    }

    @Override
    public Publisher<Void> remove(BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            deleteStatement.bind().setString(ID, blobId.asString()));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(
            delete().
                from(BlobTables.DumbBlobCache.TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(
            select()
                .from(BlobTables.DumbBlobCache.TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(
            insertInto(BlobTables.DumbBlobCache.TABLE_NAME)
                .value(ID, bindMarker(ID))
                .value(DATA, bindMarker(DATA))
                .using(ttl(bindMarker(TTL)))
        );
    }

    private boolean isGreaterThanThreshold(BufferedInputStream bufferedData) throws IOException {
        bufferedData.mark(0);
        bufferedData.skip(cacheConfiguration.getSizeThreshold());
        boolean readable = bufferedData.read() != -1;
        bufferedData.reset();
        return readable;
    }

    private void closeInputStreamQuite(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
        }
    }
}
