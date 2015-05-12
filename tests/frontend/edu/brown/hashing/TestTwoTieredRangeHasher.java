/**
 * TestTwoTieredRangeHasher.java 
 */
package edu.brown.hashing;

import org.voltdb.catalog.Column;
import org.voltdb.catalog.Table;

import edu.brown.BaseTestCase;
import edu.brown.utils.ProjectType;

/**
 * @author aelmore
 */
public class TestTwoTieredRangeHasher extends BaseTestCase {
    private static final int NUM_PARTITIONS = 3;
    private AbstractHasher hasher;
    
    
    
    
    @Override
    protected void setUp() throws Exception {
        super.setUp(ProjectType.YCSB);
        hasher = new TwoTieredRangeHasher(catalogContext, NUM_PARTITIONS, null);
        
        Table catalog_tbl = this.getTable("USERTABLE");
        Column catalog_col = this.getColumn(catalog_tbl, "YCSB_KEY");
        catalog_tbl.setPartitioncolumn(catalog_col);
    }
    
    public void testNothing() throws Exception {
        assertEquals(true, true);
    }
    
    public void testHashValue() throws Exception {
        //Look at TestPartitionEstimator
        
        long val0 = 28;
        //int hash0 = this.hasher.hash(val0);
        
        long val1 = val0 + 1;
        //int hash1 = this.hasher.hash(val1);
        
        //assertNotSame(hash0, hash1);
    }
}
