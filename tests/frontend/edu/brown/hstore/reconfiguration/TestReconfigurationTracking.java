package edu.brown.hstore.reconfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.voltdb.VoltType;
import org.voltdb.catalog.Column;
import org.voltdb.catalog.Table;
import org.voltdb.exceptions.ReconfigurationException;
import org.voltdb.exceptions.ReconfigurationException.ExceptionTypes;

import edu.brown.BaseTestCase;
import edu.brown.hashing.ExplicitPartitions;
import edu.brown.hashing.PlannedPartitions;
import edu.brown.hashing.ReconfigurationPlan;
import edu.brown.hashing.TwoTieredRangePartitions;
import edu.brown.hashing.ReconfigurationPlan.ReconfigurationRange;
import edu.brown.utils.FileUtil;
import edu.brown.utils.ProjectType;

public class TestReconfigurationTracking extends BaseTestCase {
  
public String test_json1 = "{"
            + "       \"default_table\":\"usertable\","
            + "       " +
                "\"partition_plan\":{"
            + "            \"tables\":{"
            + "              \"usertable\":{"
            + "                \"partitions\":{"
            + "                  1 : \"1-100\","
            + "                  2 : \"100-300\","
            + "                  3 : \"300-301,350-400,302-303\","
            + "                  4 : \"301-302,303-304,304-350\"       "
            + "                }     "
            + "              }"
            + "            }"
            + "          }"
            + "}";

public String test_json2 = "{"
            + "       \"default_table\":\"usertable\","
            + "       " +
                "\"partition_plan\":{"
            + "            \"tables\":{"
            + "              \"usertable\":{"
            + "                \"partitions\":{"
            + "                  1 : \"1-400\","
            + "                }     "
            + "              }"
            + "            }"            
            + "          }"
            + "}";

    private File json_path1;
    private File json_path2;
    String tbl = "usertable";
    Table catalog_tbl;
    Column catalog_col;
    @Override
    protected void setUp() throws Exception {
      super.setUp(ProjectType.YCSB);
      catalog_tbl = this.getTable(tbl);
      catalog_col = this.getColumn(catalog_tbl, "YCSB_KEY");
      catalog_tbl.setPartitioncolumn(catalog_col);
      String tmp_dir = System.getProperty("java.io.tmpdir");
      json_path1 = FileUtil.join(tmp_dir, "test1.json");
      FileUtil.writeStringToFile(json_path1, test_json1);
      json_path2 = FileUtil.join(tmp_dir, "test2.json");
      FileUtil.writeStringToFile(json_path2, test_json2);
      
    }
    
    public TestReconfigurationTracking() {
        super();
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testTrackReconfigurationRange() throws Exception{
        ExplicitPartitions p = new TwoTieredRangePartitions(catalogContext, json_path1);
        p.setPartitionPlan(json_path1);    
        ReconfigurationPlan plan = p.setPartitionPlan(json_path2);
        //PE 1
        ReconfigurationTrackingInterface tracking1 = new ReconfigurationTracking(p,plan,1);
        //PE 2
        ReconfigurationTrackingInterface tracking2 = new ReconfigurationTracking(p, plan,2);
        
        ReconfigurationRange migrRange = ReconfigurationUtil.getReconfigurationRange(catalog_tbl, new Long[]{100L}, new Long[]{110L}, 2, 1);
        
        //Test single range
        tracking1.markRangeAsReceived(migrRange);
        boolean migrOut = false;
        try{
            tracking2.markRangeAsMigratedOut(migrRange);
        } catch(ReconfigurationException re){
            if (re.exceptionType == ExceptionTypes.ALL_RANGES_MIGRATED_OUT)
                migrOut = true;
        }
        assertTrue(migrOut);
        
        //check keys that should have been migrated
        assertTrue(tracking1.checkKeyOwned(catalog_col, 100L));
        assertTrue(tracking1.checkKeyOwned(catalog_col, 108L));
        
        //a key that has not been migrated
        ReconfigurationException ex = null;
        try{
            tracking1.checkKeyOwned(catalog_col, 110L);
        } catch(ReconfigurationException e){
          ex =e;  
        } catch(Exception e){
          e.printStackTrace();   
        }
        assertNotNull(ex);
        assertEquals(ReconfigurationException.ExceptionTypes.TUPLES_NOT_MIGRATED,ex.exceptionType);        
        assertTrue(ex.dataNotYetMigrated.size()== 1);
        ReconfigurationRange range = (ReconfigurationRange) ex.dataNotYetMigrated.toArray()[0];
        
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  110L);
        assertTrue(((Long)range.getMaxExcl().get(0)[0]) == 110L);
        
        //source still has the key
        assertTrue(tracking2.checkKeyOwned(catalog_col, 110L));
        
        
        //verify moved away from 2 
        ex = null;
        try{
            tracking2.checkKeyOwned(catalog_col, 104L);
        } catch(ReconfigurationException e){
          ex =e;  
        }
        assertNotNull(ex);
        assertEquals(ReconfigurationException.ExceptionTypes.TUPLES_MIGRATED_OUT,ex.exceptionType);         
        range = (ReconfigurationRange) ex.dataMigratedOut.toArray()[0];//.get(0);
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  104L && ((Long)range.getMaxExcl().get(0)[0]) == 104L); 
        
        
        //verify moved away from 2 
        ex = null;
        try{
            tracking1.checkKeyOwned(catalog_col, 371L);
        } catch(ReconfigurationException e){
          ex =e;
        }
        assertEquals(ExceptionTypes.TUPLES_NOT_MIGRATED, ex.exceptionType);         
        range = (ReconfigurationRange) ex.dataNotYetMigrated.toArray()[0];
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  371L && ((Long)range.getMaxExcl().get(0)[0]) == 371L); 
                
        //Testing an existing range split
        migrRange = ReconfigurationUtil.getReconfigurationRange(catalog_tbl, new Long[]{370L}, new Long[]{373L}, 3, 1);
        tracking1.markRangeAsReceived(migrRange);
        assertTrue(tracking1.checkKeyOwned(catalog_col, 370L));        
        assertTrue(tracking1.checkKeyOwned(catalog_col, 371L));
        assertTrue(tracking1.checkKeyOwned(catalog_col, 372L));
        
        migrRange = ReconfigurationUtil.getReconfigurationRange(catalog_tbl, new Long[]{350L}, new Long[]{360L}, 3, 1);
        tracking1.markRangeAsReceived(migrRange);
        
        //Verify keys outside of split haven't made it
        //verify moved away from 2 
        ex = null;
        try{
            tracking1.checkKeyOwned(catalog_col, 365L);
        } catch(ReconfigurationException e){
          ex =e;
        }
        assertEquals(ExceptionTypes.TUPLES_NOT_MIGRATED, ex.exceptionType);         
        range = (ReconfigurationRange) ex.dataNotYetMigrated.toArray()[0];
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  365L && ((Long)range.getMaxExcl().get(0)[0]) == 365L);  
        
        ex = null;
        try{
            tracking1.checkKeyOwned(catalog_col, 369L);
        } catch(ReconfigurationException e){
          ex =e;
        }
        assertEquals(ExceptionTypes.TUPLES_NOT_MIGRATED, ex.exceptionType);         
        range = (ReconfigurationRange) ex.dataNotYetMigrated.toArray()[0];
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  369L && ((Long)range.getMaxExcl().get(0)[0]) == 369L);  
        
        
        assertTrue(tracking1.checkKeyOwned(catalog_col, 371L));
        assertTrue(tracking1.checkKeyOwned(catalog_col, 355L));
        
        ex = null;
        try{
            tracking1.checkKeyOwned(catalog_col, 390L);
        } catch(ReconfigurationException e){
          ex =e;
        }
        assertEquals(ExceptionTypes.TUPLES_NOT_MIGRATED, ex.exceptionType);         
        range = (ReconfigurationRange) ex.dataNotYetMigrated.toArray()[0];
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  390L && ((Long)range.getMaxExcl().get(0)[0]) == 390L);
        assertEquals(3, range.getOldPartition());
        
        
        //Test multiple ranges
        List<ReconfigurationRange> ranges = new ArrayList<>();
        ranges.add(ReconfigurationUtil.getReconfigurationRange(catalog_tbl, new Long[]{304L}, new Long[]{330L}, 4, 1));
        ranges.add(ReconfigurationUtil.getReconfigurationRange(catalog_tbl, new Long[]{340L}, new Long[]{350L}, 4, 1));
        ranges.add(ReconfigurationUtil.getReconfigurationRange(catalog_tbl, new Long[]{330L}, new Long[]{340L}, 4, 1));
        tracking1.markRangeAsReceived(ranges);

        assertTrue(tracking1.checkKeyOwned(catalog_col, 308L));
        assertTrue(tracking1.checkKeyOwned(catalog_col, 334L));
        assertTrue(tracking1.checkKeyOwned(catalog_col, 349L));

    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testTrackReconfigurationKey() throws Exception{
        ExplicitPartitions p = new TwoTieredRangePartitions(catalogContext, json_path1);
        p.setPartitionPlan(json_path1);    
        ReconfigurationPlan plan = p.setPartitionPlan(json_path2);
        //PE 1
        ReconfigurationTrackingInterface tracking1 = new ReconfigurationTracking(p,plan,1);
        System.out.println(((ReconfigurationTracking)tracking1).PULL_SINGLE_KEY);
        //PE 2
        ReconfigurationTrackingInterface tracking2 = new ReconfigurationTracking(p, plan,2);
        ReconfigurationTrackingInterface tracking3 = new ReconfigurationTracking(p, plan,3);
        ReconfigurationTrackingInterface tracking4 = new ReconfigurationTracking(p, plan,4);
        
        //keys that should be present
        assertTrue(tracking1.checkKeyOwned(catalog_col, 1L));
        assertTrue(tracking1.checkKeyOwned(catalog_col, 99L));
        
        //Check a  key that is not migrated yet, but should be

        assertTrue(tracking2.checkKeyOwned(catalog_col, 100L));
        ReconfigurationException ex = null;
        try{
            tracking1.checkKeyOwned(catalog_col, 100L);
        } catch(ReconfigurationException e){
          ex =e;  
        } catch(Exception e){
          e.printStackTrace();   
        }
        assertNotNull(ex);
        assertEquals(ReconfigurationException.ExceptionTypes.TUPLES_NOT_MIGRATED,ex.exceptionType);        
        assertTrue(ex.dataNotYetMigrated.size()== 1);
        ReconfigurationRange range = (ReconfigurationRange) ex.dataNotYetMigrated.toArray()[0];
        System.out.println(range);
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  100L); 
        assertTrue(((Long)range.getMaxExcl().get(0)[0]) == 100L); 

        //'migrate key'
        tracking1.markKeyAsReceived(tbl, Arrays.asList(new Object[]{100L}));
        tracking2.markKeyAsMigratedOut(tbl, Arrays.asList(new Object[]{100L}));
        
        //verify moved
        assertTrue(tracking1.checkKeyOwned(catalog_col, 100L));
        
        //verify moved away from 2 
        ex = null;
        try{
            tracking2.checkKeyOwned(catalog_col, 100L);
        } catch(ReconfigurationException e){
          ex =e;  
        }

        assertNotNull(ex);
        assertEquals(ReconfigurationException.ExceptionTypes.TUPLES_MIGRATED_OUT,ex.exceptionType);        
        assertTrue(ex.dataNotYetMigrated.size()== 0);
        assertTrue(ex.dataMigratedOut.size()== 1);
        range = (ReconfigurationRange) ex.dataMigratedOut.toArray()[0];
        assertTrue(((Long)range.getMinIncl().get(0)[0]) ==  100L && ((Long)range.getMaxExcl().get(0)[0]) == 100L);        
        
        
    }

}
