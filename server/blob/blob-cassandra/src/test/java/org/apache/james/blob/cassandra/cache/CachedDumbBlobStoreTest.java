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

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DumbBlobStore;
import org.apache.james.blob.api.DumbBlobStoreContract;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBucketDAO;
import org.apache.james.blob.cassandra.CassandraDefaultBucketDAO;
import org.apache.james.blob.cassandra.CassandraDumbBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CachedDumbBlobStoreTest implements DumbBlobStoreContract {

    private static final int CHUNK_SIZE = 10240;

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
            BucketName.DEFAULT);
        cached = new CassandraDumbBlobStoreCache(cassandra.getConf(), new CassandraCacheConfiguration.Builder().build());
        testee = new CachedDumbBlobStore(cached, backend);
    }

    @Override
    public DumbBlobStore testee() {
        return testee;
    }
}
