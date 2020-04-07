package org.apache.james.blob.cassandra.cache;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import javax.inject.Inject;

import org.apache.commons.configuration2.Configuration;

import com.google.common.annotations.VisibleForTesting;

public class CassandraCacheConfiguration {

    private final String TIME_OUT_PROPERTY = "";
    private final String MAX_SIZE_PROPERTY = "";

    private final long DEFAULT_TIME_OUT = 50;
    private final int DEFAULT_MAX_SIZE = 8;

    final Duration TIME_OUT;
    final int MAX_SIZE;

    @Inject
    public CassandraCacheConfiguration(Configuration propertiesConfiguration) {
        this.TIME_OUT = Duration.of(propertiesConfiguration.getLong(TIME_OUT_PROPERTY, DEFAULT_TIME_OUT), ChronoUnit.MILLIS);
        this.MAX_SIZE = propertiesConfiguration.getInt(MAX_SIZE_PROPERTY, DEFAULT_MAX_SIZE);
    }

    @VisibleForTesting
    CassandraCacheConfiguration(long timeout, int maxSize) {
        this.TIME_OUT = Duration.of(timeout, ChronoUnit.MILLIS);
        this.MAX_SIZE = maxSize;
    }
}
