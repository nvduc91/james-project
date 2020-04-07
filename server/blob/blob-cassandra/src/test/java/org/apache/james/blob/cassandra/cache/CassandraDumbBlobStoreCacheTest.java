package org.apache.james.blob.cassandra.cache;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CassandraDumbBlobStoreCacheTest implements DumbBlobStoreCacheContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraBlobModule.MODULE);

    private final long DEFAULT_TIME_OUT = 50;
    private final int DEFAULT_MAX_SIZE = 8;

    private DumbBlobStoreCache testee;
    private HashBlobId.Factory blobIdFactory;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        blobIdFactory = new HashBlobId.Factory();
        CassandraCacheConfiguration cacheConfiguration = new CassandraCacheConfiguration(DEFAULT_TIME_OUT, DEFAULT_MAX_SIZE);
        testee = new CassandraDumbBlobStoreCache(cassandra.getConf(), cacheConfiguration);
    }

    @Override
    public DumbBlobStoreCache testee() {
        return testee;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return blobIdFactory;
    }
}
