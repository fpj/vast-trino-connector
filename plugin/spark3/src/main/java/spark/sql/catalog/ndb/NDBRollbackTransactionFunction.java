/*
 *  Copyright (C) Vast Data Ltd.
 */

package spark.sql.catalog.ndb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vastdata.client.VastConfig;
import com.vastdata.client.error.VastUserException;
import com.vastdata.client.tx.SimpleVastTransaction;
import com.vastdata.spark.tx.VastSimpleTransactionFactory;
import com.vastdata.spark.tx.VastSparkTransactionsManager;
import ndb.NDB;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkContext$;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.functions.BoundFunction;
import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.mutable.HashMap;

import java.io.IOException;
import java.io.Serializable;

import static com.vastdata.client.error.VastExceptionFactory.toRuntime;

public class NDBRollbackTransactionFunction
        implements UnboundFunction, Serializable
{
    private static final Logger LOG = LoggerFactory.getLogger(NDBRollbackTransactionFunction.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public NDBRollbackTransactionFunction(VastConfig conf)
    {
        VastSparkTransactionsManager transactionsManager;
        try {
            transactionsManager = VastSparkTransactionsManager.getInstance(NDB.getVastClient(conf), new VastSimpleTransactionFactory());
        }
        catch (VastUserException e) {
            throw toRuntime(e);
        }
        SparkContext sparkContext = SparkContext$.MODULE$.getActive().get();
        HashMap<String, String> env = sparkContext.executorEnvs();
        boolean contains = env.contains("tx");
        if (contains) {
            try {
                String tx_str = env.get("tx").get();
                LOG.info("rollback tx={}", tx_str);
                SimpleVastTransaction tx = OBJECT_MAPPER.readValue(env.get("tx").get(), SimpleVastTransaction.class);
                transactionsManager.rollback(tx);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            finally {
                env.remove("tx");
            }
        }
        else {
            throw toRuntime(new VastUserException("Failing rollback as active transaction was not found"));
        }
    }

    @Override
    public BoundFunction bind(StructType inputType)
    {
        return new ScalarFunction<Boolean>()
        {
            @Override
            public Boolean produceResult(InternalRow input)
            {
                return true;
            }

            @Override
            public DataType[] inputTypes()
            {
                return new DataType[0];
            }

            @Override
            public DataType resultType()
            {
                return DataTypes.BooleanType;
            }

            @Override
            public String name()
            {
                return NDBFunction.ROLLBACK_TX.getFuncName();
            }
        };
    }

    @Override
    public String description()
    {
        return "Rolling back a Vast transaction";
    }

    @Override
    public String name()
    {
        return NDBFunction.ROLLBACK_TX.getFuncName();
    }
}
