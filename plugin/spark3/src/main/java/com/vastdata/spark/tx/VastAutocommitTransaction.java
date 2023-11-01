/*
 *  Copyright (C) Vast Data Ltd.
 */

package com.vastdata.spark.tx;

import com.vastdata.client.VastClient;
import com.vastdata.client.error.VastIOException;
import com.vastdata.client.tx.SimpleVastTransaction;
import com.vastdata.client.tx.VastTraceToken;
import com.vastdata.client.tx.VastTransaction;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkContext$;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.mutable.HashMap;

import java.io.Serializable;
import java.util.Optional;
import java.util.function.Supplier;

public class VastAutocommitTransaction implements VastTransaction, AutoCloseable, Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(VastAutocommitTransaction.class);
    private final VastClient client;
    private final VastTransaction transaction;
    private final boolean autoCreated;
    private boolean rollback = false;

    private VastAutocommitTransaction(VastClient client, VastTransaction transaction, boolean autoCreated) {
        this.client = client;
        if (transaction == null) {
            throw new RuntimeException("missing transaction");
        }
        this.transaction = transaction;
        this.autoCreated = autoCreated;
    }

    public VastAutocommitTransaction(SimpleVastTransaction fromString, boolean autoCreated) {
        this.client = null;
        this.transaction = fromString;
        this.autoCreated = autoCreated;
    }

    @Override
    public void close()
    {
        if (!autoCreated) {
            LOG.debug("VastAutocommitTransaction.wrap CLOSE explicit tx: tx: {}", transaction);
            // manually created, therefore should be manually closed
            return;
        }
        if (client != null) {
            if (rollback) {
                LOG.debug("VastAutocommitTransaction.wrap ROLLBACK: tx: {}", transaction);
                client.rollbackTransaction(transaction);
            }
            else {
                LOG.debug("VastAutocommitTransaction.wrap COMMIT: tx: {}", transaction);
                client.commitTransaction(transaction);
            }
        }
        else {
            LOG.warn("VastAutocommitTransaction.wrap CLOSE autocommit without client: tx: {}", transaction);
        }
    }

    public VastTransaction getTransaction()
    {
        return transaction;
    }

    @Override
    public long getId()
    {
        return transaction.getId();
    }

    @Override
    public boolean isReadOnly()
    {
        return transaction.isReadOnly();
    }

    @Override
    public VastTraceToken generateTraceToken(Optional<String> userTraceToken)
    {
        return transaction.generateTraceToken(userTraceToken);
    }

    public static SimpleVastTransaction getExisting()
            throws VastIOException
    {
        SparkContext sparkContext = SparkContext$.MODULE$.getActive().get();
        HashMap<String, String> env = sparkContext.executorEnvs();
        boolean contains = env.contains("tx");
        if (contains) {
            String tx = env.get("tx").get();
            LOG.info("VastAutocommitTransaction.wrap EXISTING: tx: {}", tx);
            return SimpleVastTransaction.fromString(tx);
        }
        else {
            LOG.debug("VastAutocommitTransaction.wrap EXISTING: null");
            return null;
        }
    }

    public static VastAutocommitTransaction wrap(VastClient vastClient, Supplier<VastTransaction> vastTransactionSupplier) {
        SparkContext sparkContext = SparkContext$.MODULE$.getActive().get();
        HashMap<String, String> env = sparkContext.executorEnvs();
        boolean contains = env.contains("tx");
        if (contains) {
            String tx = env.get("tx").get();
            try {
                LOG.info("VastAutocommitTransaction.wrap REUSE: tx: {}", tx);
                return new VastAutocommitTransaction(SimpleVastTransaction.fromString(tx), false);
            }
            catch (VastIOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            VastAutocommitTransaction vastAutocommitTransaction = new VastAutocommitTransaction(vastClient, vastTransactionSupplier.get(), true);
            LOG.info("VastAutocommitTransaction.wrap NEW: {}", vastAutocommitTransaction);
            return vastAutocommitTransaction;
        }
    }

    public void setCommit(boolean mode)
    {
        this.rollback = !mode;
    }
}
