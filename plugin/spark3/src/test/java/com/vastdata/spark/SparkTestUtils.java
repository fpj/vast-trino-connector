/*
 *  Copyright (C) Vast Data Ltd.
 */

package com.vastdata.spark;

import com.vastdata.client.VastConfig;
import org.apache.spark.sql.SparkSession;

import java.net.URI;

import static java.lang.String.format;

public final class SparkTestUtils
{
    private SparkTestUtils() {}

    public static SparkSession getSession()
    {
        return getSession(80);
    }

    public static SparkSession getSession(int testPort)
    {
        return SparkSession.builder()
                .config("spark.sql.catalog.ndb", "spark.sql.catalog.ndb.VastCatalog")
                .config("spark.sql.defaultCatalog", "ndb")
                .config("spark.sql.extensions", "ndb.NDBSparkSessionExtension")
                .config("spark.sql.optimizer.runtimeFilter.semiJoinReduction.enabled", "true")
                .config("spark.driver.extraClassPath", "src/tabular/trino/plugin/spark3/resources/*")
                .config("spark.driver.userClassPathFirst", true)
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.ndb.endpoint", getEndpointString(testPort))
                .config("spark.ndb.access_key_id", "pIX3SzyuQVmdrIVZnyy0")
                .config("spark.ndb.secret_access_key", "5c5HqW3cDQsUNg68OlhJmq72TM2nZxcP5lR6D1ps")
                .config("spark.ndb.num_of_splits", "2")
                .config("spark.ndb.vast_transaction_keep_alive_interval_seconds", 1)
                .appName("example")
                .master("local[*]")
                .getOrCreate();
    }

    private static String getEndpointString(int testPort)
    {
        return format("http://localhost:%d", testPort);
    }

    public static VastConfig getTestConfig()
    {
        return new VastConfig()
                .setEndpoint(URI.create(getEndpointString(1234)))
                .setAccessKeyId("pIX3SzyuQVmdrIVZnyy0")
                .setSecretAccessKey("5c5HqW3cDQsUNg68OlhJmq72TM2nZxcP5lR6D1ps")
                .setRegion("us-east-1");
    }
}
