package org.apache.james.blob.cassandra.cache;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static org.apache.james.blob.cassandra.BlobTables.BucketBlobTable.ID;
import static org.apache.james.blob.cassandra.BlobTables.DumbBlobCache.DATA;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.cassandra.BlobTables;
import org.apache.james.blob.cassandra.utils.DataChunker;
import org.reactivestreams.Publisher;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class CassandraDumbBlobStoreCache implements DumbBlobStoreCache {

    private final DataChunker dataChunker;
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement delete;
    private final CassandraCacheConfiguration cacheConfiguration;

    @Inject
    public CassandraDumbBlobStoreCache(Session session, CassandraCacheConfiguration cacheConfiguration) {
        this.dataChunker = new DataChunker();
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.insert = prepareInsert(session);
        this.select = prepareSelect(session);
        this.delete = prepareDelete(session);
        this.cacheConfiguration = cacheConfiguration;
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(
            QueryBuilder.delete().
                from(BlobTables.BucketBlobTable.TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(
            QueryBuilder.select()
                .from(BlobTables.BucketBlobTable.TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(
            QueryBuilder.insertInto(BlobTables.DumbBlobCache.TABLE_NAME)
                .value(ID, bindMarker(ID))
                .value(DATA, bindMarker(DATA)));
    }

    private boolean isAValidStream(InputStream bufferedData) throws IOException {
        bufferedData.mark(0);
        bufferedData.skip(cacheConfiguration.MAX_SIZE);
        boolean isItABigStream = bufferedData.read() != -1;
        bufferedData.reset();
        return isItABigStream;
    }

    @Override
    public Publisher<Void> cache(BlobId blobId, byte[] data) {
        Preconditions.checkNotNull(data, "Data must not be null");
        Preconditions.checkArgument(data.length <= 0, "Data must not be empty");
        Preconditions.checkArgument(data.length > cacheConfiguration.MAX_SIZE, "Data must not biger than %s kb", cacheConfiguration.MAX_SIZE);

        return dataChunker.chunk(data, cacheConfiguration.MAX_SIZE)
            .flatMap(chunk -> save(blobId, chunk));
    }

    private Publisher<Void> save(BlobId blobId, ByteBuffer data) {
        return cassandraAsyncExecutor.executeVoid(
            insert.bind()
                .setString(ID, blobId.asString())
                .setBytes(DATA, data));
    }

    @Override
    public Publisher<Void> cache(BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(inputStream);

        return Mono.fromCallable(() -> isAValidStream(inputStream))
            .flatMap(validStream -> {
                if (validStream) {
                    return dataChunker.chunkStream(inputStream, cacheConfiguration.MAX_SIZE)
                        .flatMap(chunk -> save(blobId, chunk)).single();
                }
                return Mono.error(new IllegalArgumentException(
                    String.format("Data must not biger than %s kb", cacheConfiguration.MAX_SIZE)));
            });
    }

    @Override
    public Publisher<byte[]> read(BlobId blobId) {
        return cassandraAsyncExecutor.executeSingleRow(select.bind().setString(ID, blobId.asString()))
            .map(row -> row.getBytes(DATA).array())
            .timeout(cacheConfiguration.TIME_OUT, Mono.empty());
    }

    @Override
    public Publisher<Void> delete(BlobId blobId) {
        return cassandraAsyncExecutor.executeVoid(
            delete.bind().setString(ID, blobId.asString()));
    }
}
