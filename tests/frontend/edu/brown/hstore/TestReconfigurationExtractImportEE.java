/**
 * 
 */
package edu.brown.hstore;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.voltdb.VoltTable;
import org.voltdb.VoltType;
import org.voltdb.benchmark.tpcc.TPCCConstants;
import org.voltdb.catalog.Site;
import org.voltdb.catalog.Table;
import org.voltdb.client.Client;
import org.voltdb.jni.ExecutionEngine;
import org.voltdb.jni.ExecutionEngineJNI;
import org.voltdb.utils.Pair;
import org.voltdb.utils.VoltTableUtil;

import edu.brown.BaseTestCase;
import edu.brown.benchmark.ycsb.YCSBConstants;
import edu.brown.catalog.CatalogUtil;
import edu.brown.designer.MemoryEstimator;
import edu.brown.hashing.PlannedHasher;
import edu.brown.hashing.PlannedPartitions.PartitionRange;
import edu.brown.hashing.PlannedPartitions.PartitionedTable;
import edu.brown.hashing.ReconfigurationPlan.ReconfigurationRange;
import edu.brown.hashing.ReconfigurationPlan.ReconfigurationTable;
import edu.brown.hstore.conf.HStoreConf;
import edu.brown.hstore.reconfiguration.ReconfigurationUtil;
import edu.brown.utils.CollectionUtil;
import edu.brown.utils.ProjectType;

/**
 * @author aelmore
 *
 */
public class TestReconfigurationExtractImportEE extends BaseTestCase {

    private static final Logger LOG = Logger.getLogger(TestReconfigurationExtractImportEE.class);
    private static final int NUM_PARTITIONS = 1;
    private static final long NUM_TUPLES = 100;
    private static final String CUSTOMER_TABLE_NAME = TPCCConstants.TABLENAME_CUSTOMER;
    private static final String NEW_ORDER_TABLE_NAME = TPCCConstants.TABLENAME_NEW_ORDER;
    private static final int DEFAULT_LIMIT = ExecutionEngineJNI.DEFAULT_EXTRACT_LIMIT_BYTES;
    
    private HStoreSite hstore_site;
    private HStoreConf hstore_conf;
    private Client client;
    
    private PartitionExecutor executor;
    private ExecutionEngine ee;
    private Table customer_tbl;
    private Table neworder_tbl;
    private int neworder_p_index;
    private int cust_p_index;
    private int undo=1;
    
    //private int ycsbTableId(Catalog catalog) {
    //    return catalog.getClusters().get("cluster").getDatabases().get("database").getTables().get(TARGET_TABLE).getRelativeIndex();
    // }
    @Before
    public void setUp() throws Exception {
        super.setUp(ProjectType.TPCC);
        
        initializeCatalog(1, 1, NUM_PARTITIONS);
        
        // Just make sure that the Table has the evictable flag set to true
        this.customer_tbl = getTable(CUSTOMER_TABLE_NAME);
        this.cust_p_index = this.customer_tbl.getPartitioncolumn().getIndex();
        this.neworder_tbl = getTable(NEW_ORDER_TABLE_NAME);
        this.neworder_p_index = this.neworder_tbl.getPartitioncolumn().getIndex();
        
        Site catalog_site = CollectionUtil.first(CatalogUtil.getCluster(catalog).getSites());
        hstore_conf = HStoreConf.singleton();
        
        hstore_conf.site.coordinator_sync_time = false;
        hstore_conf.global.reconfiguration_enable = true;
        //hstore_conf.global.hasher_class = "edu.brown.hashing.PlannedHasher";
        //hstore_conf.global.hasher_plan = PlannedHasher.YCSB_TEST;
        
        hstore_conf.site.status_enable = false;

        
        this.hstore_site = createHStoreSite(catalog_site, hstore_conf);
        this.executor = hstore_site.getPartitionExecutor(0);
        assertNotNull(this.executor);
        this.ee = executor.getExecutionEngine();
        assertNotNull(this.executor);
        
        this.client = createClient();
    }
    
    @Override
    protected void tearDown() throws Exception {
        if (this.client != null) this.client.close();
        if (this.hstore_site != null) this.hstore_site.shutdown();
    }
    
    private void loadTPCCData(Long numTuples, Table table, int widIndex, int wid ) throws Exception {
        // Load in a bunch of dummy data for this table
        VoltTable vt = CatalogUtil.getVoltTable(table);
        assertNotNull(vt);
        for (int i = 0; i < numTuples; i++) {
            Object row[] = VoltTableUtil.getRandomRow(table);
            row[0] = i;
            row[widIndex] = wid;
            vt.addRow(row);
        } // FOR
        this.executor.loadTable(1000L, table, vt, false);

    }
    
    int[] scales = { 1,2,3,5};//,11,29,51,97 };
    int[] scales2 = { 2 };

    
    @Test 
    public void testMultiExtract() throws Exception {
        int wid = 2;
        ReconfigurationRange range; 
        VoltTable extractTable;
        range = ReconfigurationUtil.getReconfigurationRange(customer_tbl, new Long[][]{{ new Long(wid) }}, new Long[][]{{ new Long(wid+1) }}, 1, 2);
        extractTable = ReconfigurationUtil.getExtractVoltTable(range);   
        this.loadTPCCData(NUM_TUPLES * 10, this.customer_tbl,this.cust_p_index, wid);
        int EXTRACT_LIMIT = 2048;
        ((ExecutionEngineJNI)(this.ee)).DEFAULT_EXTRACT_LIMIT_BYTES = EXTRACT_LIMIT;
        
        long tupleBytes = MemoryEstimator.estimateTupleSize(this.customer_tbl);
        int tuplesInChunk = (int)(EXTRACT_LIMIT / tupleBytes);
        int expectedChunks = ((int)(NUM_TUPLES * 10)/tuplesInChunk);
        int resCount = 0;
        int chunks = 0;
        Pair<VoltTable,Boolean> resTable = 
                this.ee.extractTable(this.customer_tbl, this.customer_tbl.getRelativeIndex(), extractTable, 1, 1, undo++, -1, 1);
        assertTrue(resTable.getSecond());
        resCount += resTable.getFirst().getRowCount();
        chunks++;
        while(resTable.getSecond()){
            resTable = 
                    this.ee.extractTable(this.customer_tbl, this.customer_tbl.getRelativeIndex(), extractTable, 1, 1, undo++, -1, 1);
            resCount += resTable.getFirst().getRowCount();
            chunks++;
        }
        assertEquals(expectedChunks, chunks);
        assertEquals(NUM_TUPLES*10, resCount);
    }
    
    
    @Test
    public void testExtractDataDiffKeys() throws Exception {
        ((ExecutionEngineJNI)(this.ee)).DEFAULT_EXTRACT_LIMIT_BYTES = DEFAULT_LIMIT;

        for (int i=0; i< scales.length; i++) {
            long tuples = NUM_TUPLES * scales[i];
            LOG.info(String.format("Loading %s tuples for customers with W_ID :%s ", tuples,scales[i]));
            this.loadTPCCData(tuples, this.customer_tbl,this.cust_p_index,scales[i]);
                
        }
        LOG.info("load done");

        
    	assertTrue(true);
    	
    	ReconfigurationRange range; 
    	VoltTable extractTable;
    	long start, extract, load;
    	Pair<VoltTable,Boolean> resTable;
    	Pair<VoltTable,Boolean> resTableVerify;
        
    	
    	for (int i=0; i< scales.length; i++) {
    	    int scale = scales[i];
    	    LOG.info("Testing for scale : " + scale);
    	    //extract
            range = ReconfigurationUtil.getReconfigurationRange(customer_tbl, new Long[][]{{ new Long(scale) }}, new Long[][]{{ new Long(scale+1) }}, 1, 2);
            extractTable = ReconfigurationUtil.getExtractVoltTable(range);   
            start = System.currentTimeMillis();
            resTable= this.ee.extractTable(this.customer_tbl, this.customer_tbl.getRelativeIndex(), extractTable, 1, 1, undo++, -1, 1);
            extract = System.currentTimeMillis()-start; 
            assertFalse(resTable.getSecond());
            LOG.info("Tuples : " + resTable.getFirst().getRowCount());
            assertTrue(resTable.getFirst().getRowCount()==NUM_TUPLES *scale);
            
            //assert empty     
            resTableVerify= this.ee.extractTable(this.customer_tbl, this.customer_tbl.getRelativeIndex(), extractTable, 1, 1, undo++, -1, 1);
            assertTrue(resTableVerify.getFirst().getRowCount()==0);
            
            
            //load
            start = System.currentTimeMillis();
            this.executor.loadTable(2000L, this.customer_tbl, resTable.getFirst(), false);
            load = System.currentTimeMillis() - start;
            LOG.info(String.format("size=%s Extract=%s Load=%s Diff:%s", NUM_TUPLES*scale, extract, load, load-extract));

            //re extract and check its there
            resTableVerify= this.ee.extractTable(this.customer_tbl, this.customer_tbl.getRelativeIndex(), extractTable, 1, 1, undo++, -1, 1);
            assertTrue(resTableVerify.getFirst().getRowCount()==NUM_TUPLES *scale);
            
            
            
    	    
        }
        

        
    	/*
    	range = new ReconfigurationRange<Long>("usertable", VoltType.BIGINT, new Long(998), new Long(1002), 1, 2);
        extractTable = ReconfigurationUtil.getExtractVoltTable(range);        
        resTable= this.ee.extractTable(this.customer_tbl.getRelativeIndex(), extractTable, 1, 1, 1, executor.getNextRequestToken());       
        assertTrue(resTable.getRowCount()==2);
        */
    }
    
    
}
