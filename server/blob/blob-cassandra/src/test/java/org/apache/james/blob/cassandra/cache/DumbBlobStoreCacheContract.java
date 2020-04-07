package org.apache.james.blob.cassandra.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.blob.api.BlobId;
import org.junit.Test;

import com.google.common.base.Strings;

import reactor.core.publisher.Mono;

public interface DumbBlobStoreCacheContract {

    byte[] EMPTY_BYTEARRAY = {};
    byte[] SHORT_BYTEARRAY = "Short".getBytes(StandardCharsets.UTF_8);
    byte[] EIGHT_KILOBYTES = Strings.repeat("01234567\n", 1000).getBytes(StandardCharsets.UTF_8);
    byte[] TWELVE_MEGABYTES = Strings.repeat("0123456789\r\n", 1024 * 1024).getBytes(StandardCharsets.UTF_8);

    DumbBlobStoreCache testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void shouldNotThrowWhenSaveValidByteData() {
        BlobId blobId = blobIdFactory().randomId();
        Mono<Void> actual = Mono.from(testee().cache(blobId, EIGHT_KILOBYTES));
        assertThatCode(actual::block).doesNotThrowAnyException();
    }

    @Test
    default void shouldThrowWhenSaveInValidByteData() {
        BlobId blobId = blobIdFactory().randomId();
        assertThatThrownBy(() -> Mono.from(testee().cache(blobId, TWELVE_MEGABYTES)).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldNotThrowWhenSaveValidInputStreamData() {
        BlobId blobId = blobIdFactory().randomId();
        Mono<Void> actual = Mono.from(testee().cache(blobId, new ByteArrayInputStream(EIGHT_KILOBYTES)));
        assertThatCode(actual::block).doesNotThrowAnyException();
    }

    @Test
    default void shouldThrowWhenSaveInValidInputStreamData() {
        BlobId blobId = blobIdFactory().randomId();
        assertThatThrownBy(() -> Mono.from(testee().cache(blobId, new ByteArrayInputStream(TWELVE_MEGABYTES))).block())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void shouldReturnExactlyDataWhenRead() {
        BlobId blobId = blobIdFactory().randomId();
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();

        byte[] actual = Mono.from(testee().read(blobId)).block();
        assertThat(actual).containsExactly(EIGHT_KILOBYTES);
    }

    @Test
    default void shouldReturnEmptyWhenReadWithTimeOut() {
        BlobId blobId = blobIdFactory().randomId();
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();

    }

    @Test
    default void shouldReturnNothingWhenDelete() {
        BlobId blobId = blobIdFactory().randomId();
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();
        Mono.from(testee().delete(blobId)).block();
        byte[] actual = Mono.from(testee().read(blobId)).block();

        assertThat(actual).isEmpty();
    }

    @Test
    default void shouldDeleteExactlyAndReturnNothingWhenDelete() {
        BlobId blobId = blobIdFactory().randomId();
        BlobId blobId2 = blobIdFactory().randomId();
        Mono.from(testee().cache(blobId, EIGHT_KILOBYTES)).block();
        Mono.from(testee().cache(blobId2, EIGHT_KILOBYTES)).block();
        Mono.from(testee().delete(blobId)).block();

        byte[] readBlobId2 = Mono.from(testee().read(blobId2)).block();
        assertThat(readBlobId2).containsExactly(EIGHT_KILOBYTES);
    }
}
