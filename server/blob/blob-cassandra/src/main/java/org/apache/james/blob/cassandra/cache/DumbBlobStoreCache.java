package org.apache.james.blob.cassandra.cache;

import java.io.InputStream;

import org.apache.james.blob.api.BlobId;
import org.reactivestreams.Publisher;

public interface DumbBlobStoreCache {
    Publisher<Void> cache(BlobId blobId, byte[] data);
    Publisher<Void> cache(BlobId blobId, InputStream data);

    Publisher<byte[]> read(BlobId blobId);
    Publisher<Void> delete(BlobId blobId);
}
