package edu.mit.benchmark.affinity.procedures;

import org.voltdb.ProcInfo;
import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

import org.apache.log4j.Logger;

import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;


@ProcInfo(
        partitionInfo = "USES.PRODUCT_KEY: 0",
        singlePartition = true
    )
public class GetPartsByProduct extends VoltProcedure {
    //private static final Logger LOG = Logger.getLogger(VoltProcedure.class);
    //private static final LoggerBoolean debug = new LoggerBoolean();
    //private static final LoggerBoolean trace = new LoggerBoolean();
    //static {
//        LoggerUtil.setupLogging();
//        LoggerUtil.attachObserver(LOG, debug, trace);
//    }
    
    public final SQLStmt getProductInfoStmt = new SQLStmt("SELECT FIELD1, FIELD2, FIELD3 FROM PRODUCTS WHERE PRODUCT_KEY = ? ");
    public final SQLStmt getPartsByProductStmt = new SQLStmt("SELECT PART_KEY FROM USES WHERE PRODUCT_KEY = ? ");    
    public final SQLStmt getPartInfoStmt = new SQLStmt("SELECT FIELD1, FIELD2, FIELD3 FROM PARTS WHERE PART_KEY = ? ");
    
    public VoltTable[] run(long product_key){
        voltQueueSQL(getProductInfoStmt, product_key);
        voltQueueSQL(getPartsByProductStmt, product_key);
        final VoltTable[] results = voltExecuteSQL();
        assert results.length == 2;
        	
        for(int i = 0; i < results[1].getRowCount(); ++i) {
        	voltQueueSQL(getPartInfoStmt, results[1].fetchRow(i).getLong(0));
        }
        return voltExecuteSQL(true);
    }

}
