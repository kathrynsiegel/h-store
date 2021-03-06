/**
 * 
 */
package edu.brown.hstore.reconfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.antcontrib.math.Numeric;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.voltdb.catalog.CatalogType;
import org.voltdb.exceptions.ReconfigurationException;
import org.voltdb.exceptions.ReconfigurationException.ExceptionTypes;
import org.voltdb.catalog.Table;

import edu.brown.hashing.ExplicitPartitions;
import edu.brown.hashing.ReconfigurationPlan;
import edu.brown.hashing.ReconfigurationPlan.ReconfigurationRange;
import edu.brown.hstore.HStoreConstants;
import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;

/**
 * Class to track the reconfiguration state and progress for a single partition
 * Not thread safe
 * @author aelmore
 *
 */
public class ReconfigurationTracking implements ReconfigurationTrackingInterface {

    private static final Logger LOG = Logger.getLogger(ReconfigurationTracking.class);
    private static final LoggerBoolean debug = new LoggerBoolean();
    private static final LoggerBoolean trace = new LoggerBoolean();
    static {
        LoggerUtil.setupLogging();
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    public static boolean PULL_SINGLE_KEY = true;
    private List<ReconfigurationRange> outgoing_ranges;
    private List<ReconfigurationRange> incoming_ranges;
    public List<ReconfigurationRange> dataMigratedOut;
    public List<ReconfigurationRange> dataPartiallyMigratedOut;

    public List<ReconfigurationRange> dataPartiallyMigratedIn;
    public List<ReconfigurationRange> dataMigratedIn;
    private int rangesMigratedInCount = 0;
    private int rangesMigratedOutCount = 0;
    private int incomingRangesCount = 0;
    private int outgoingRangesCount = 0;
    
    //set of individual keys migrated out/in status, stored in a map by table name as key 
    public Map<String,Set<List<Object>>> migratedKeyIn;
    public Map<String,Set<List<Object>>> migratedKeyOut;
    
    private int partition_id;
    private ExplicitPartitions partitionPlan;
    
    
    protected ReconfigurationTracking(ExplicitPartitions partitionPlan,List<ReconfigurationRange> outgoing_ranges,
            List<ReconfigurationRange> incoming_ranges, int partition_id) {
        super();
        this.partitionPlan = partitionPlan;
        this.outgoing_ranges = new ArrayList<ReconfigurationRange>();
        if (outgoing_ranges!= null)
        	this.outgoing_ranges.addAll(outgoing_ranges);
        this.incoming_ranges = new ArrayList<ReconfigurationRange>();
        if (incoming_ranges != null)
        	this.incoming_ranges.addAll(incoming_ranges);
        for(ReconfigurationRange range : this.incoming_ranges) {
        	incomingRangesCount += range.getMinIncl().size();
        }
        for(ReconfigurationRange range: this.outgoing_ranges){
        	outgoingRangesCount += range.getMinIncl().size();
        }
        this.partition_id = partition_id;
        this.migratedKeyIn = new HashMap<String, Set<List<Object>>>();
        this.migratedKeyOut = new HashMap<String, Set<List<Object>>>();
        this.dataMigratedIn = new ArrayList<>();
        this.dataMigratedOut = new ArrayList<>();
        this.dataPartiallyMigratedOut = new ArrayList<>();
        this.dataPartiallyMigratedIn = new ArrayList<>();
    }
    
    public ReconfigurationTracking(ExplicitPartitions partitionPlan, ReconfigurationPlan plan, int partition_id){
        this(partitionPlan,plan.getOutgoing_ranges().get(partition_id), plan.getIncoming_ranges().get(partition_id),partition_id);
    }
  
    @Override 
    public boolean markRangeAsMigratedOut(ReconfigurationRange range ) throws ReconfigurationException{
        boolean added =  this.dataMigratedOut.add(range);
        if(added){
            rangesMigratedOutCount++;
            if(rangesMigratedOutCount==outgoingRangesCount){
                throw new ReconfigurationException(ExceptionTypes.ALL_RANGES_MIGRATED_OUT);
            }
        }
        return added;
    }
    
    @Override 
    public boolean markRangeAsPartiallyMigratedOut(ReconfigurationRange range ) throws ReconfigurationException{
        return this.dataPartiallyMigratedOut.add(range);
    }
    
    
    @Override 
    public boolean markRangeAsMigratedOut(List<ReconfigurationRange> ranges ){
        boolean allAdded = true;
        for(ReconfigurationRange range : ranges){
            boolean res = this.markRangeAsMigratedOut(range);
            if (!res)
                allAdded =false;
        }
        return allAdded;
    }
    
    @Override
    public boolean markRangeAsReceived(List<ReconfigurationRange> ranges ){
        boolean allAdded = true;
        for(ReconfigurationRange range : ranges){
            boolean res = this.markRangeAsReceived(range);
            if (!res)
                allAdded =false;
        }
        return allAdded;
    }
    
    @Override
    public boolean markRangeAsReceived(ReconfigurationRange range ){
        boolean added =  this.dataMigratedIn.add(range);
        if(added){
            rangesMigratedInCount++;if(rangesMigratedInCount==incomingRangesCount){
                throw new ReconfigurationException(ExceptionTypes.ALL_RANGES_MIGRATED_IN);
            }
        }
        return added;
    }
    
    @Override
    public boolean markRangeAsPartiallyReceived(ReconfigurationRange range ){
        return this.dataPartiallyMigratedIn.add(range);        
    }
    

    private boolean markAsMigrated(Map<String,Set<List<Object>>> migratedMapSet, String table_name, List<Object> key){
        if(migratedMapSet.containsKey(table_name) == false){
            migratedMapSet.put(table_name, new HashSet<List<Object>>());
        }
        return migratedMapSet.get(table_name).add(key);
        	
    }

    private boolean checkMigratedMapSet(Map<String,Set<List<Object>>> migratedMapSet, String table_name, Object key){
    	if(migratedMapSet.containsKey(table_name) == false){
           if (trace.val) LOG.trace("Checking a key for which there is no table tracking for yet " + table_name); 
           return false;
        }
        return migratedMapSet.get(table_name).contains(key);
    }
    
    @Override
    public boolean markKeyAsMigratedOut(String table_name, List<Object> key) {
    	Object[] key_arr = key.toArray();
		for (ReconfigurationRange range : this.outgoing_ranges) {
            if (range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                markRangeAsPartiallyMigratedOut(range);
            }
        }
        return markAsMigrated(migratedKeyOut, table_name, key);
    }

    @Override
    public boolean markKeyAsReceived(String table_name, List<Object> key) {
    	Object[] key_arr = key.toArray();
		for (ReconfigurationRange range : this.incoming_ranges) {
            if (range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                markRangeAsPartiallyReceived(range);
            }
        }
        return markAsMigrated(migratedKeyIn, table_name, key);
    }
    
    @Override
    public boolean checkKeyOwned(CatalogType catalog, Object key) throws ReconfigurationException{
    	return checkKeyOwned(Arrays.asList(catalog), Arrays.asList(key));
    }

    @Override
    public boolean checkKeyOwned(List<CatalogType> catalog, List<Object> key) throws ReconfigurationException{

    	Table table = this.partitionPlan.getTable(catalog);
    	String tableName = table.getName().toLowerCase();    	
    	if (trace.val) LOG.trace(String.format("Checking Key owned for catalog:%s table:%s",catalog.toString(),tableName));
    	return checkKeyOwned(table, key);
    }
    
    @Override
    public boolean quickCheckKeyOwned(int previousPartition, int expectedPartition, CatalogType catalog, Object key) {
    	return quickCheckKeyOwned(previousPartition, expectedPartition, Arrays.asList(catalog), Arrays.asList(key));
    }
    
    @Override
    public boolean quickCheckKeyOwned(int previousPartition, int expectedPartition, List<CatalogType> catalog, List<Object> key) {
        try{
        	Object[] key_arr = key.toArray();
    		Table table = this.partitionPlan.getTable(catalog);
            String table_name = table.getName().toLowerCase();
            if (expectedPartition == partition_id &&  previousPartition == partition_id)
            {
                //This partition should have the key and it didnt move     
                if (trace.val) LOG.trace(String.format("Key %s is at %s and did not migrate ",key,partition_id));
                return true;
            } else if (expectedPartition == partition_id &&  previousPartition != partition_id) {
              //Key should be moving to here
                if (debug.val) LOG.debug(String.format("Key %s should be at %s. Checking if migrated in",key,partition_id));
                
                //Has the key been received as a single key
                if (checkMigratedMapSet(migratedKeyIn,table_name,key)== true){
                    if (debug.val) LOG.debug(String.format("Key has been migrated in %s (%s)",key,table_name));
                    return true;
                } else {                       
                    //check if the key was received out in a range        
                    for(ReconfigurationRange range : this.dataMigratedIn){
                        if(range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                            if (debug.val) LOG.debug(String.format("Key has been migrated in range %s %s (%s)",range, key,table_name));
                            return true;
                        }
                    }
                    return false;
                }
            } else if (expectedPartition != partition_id &&  previousPartition == partition_id) {
                //Key should be moving away
                if (debug.val) LOG.debug(String.format("Key %s was at %s. Checking if migrated ",key,partition_id));                
                //Check to see if we migrated this key individually
                if (checkMigratedMapSet(migratedKeyOut,table_name,key)){
                    if (debug.val) LOG.debug(String.format("Key has been migrated out %s (%s)",key,table_name));                    
                    return false;
                }                
                //check to see if this key was migrated in a range
                for(ReconfigurationRange range : this.dataMigratedOut){
                    if(range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                        if (debug.val) LOG.debug(String.format("Key has been migrated out range %s %s (%s)",range, key,table_name));
                        return false;
                    }
                }                
                //check to see if this key was migrated in a range
                for(ReconfigurationRange range : this.dataPartiallyMigratedOut){
                    if(range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                        if (debug.val) LOG.debug(String.format("Key may have been migrated out in partially dirtied range %s %s (%s)",range, key,table_name));
                        return false;
                    }
                }                
                return true;
            } else if (expectedPartition != partition_id &&  previousPartition != partition_id) {
                //We didnt have nor are are expected to
                if (debug.val) LOG.debug(String.format("Key %s is not at %s, nor was it previously",key,partition_id));

                return false;
            }
        } catch (Exception e) {
            LOG.error("Exception quickCheckKeyOwned",e);
            return false;
        }
        LOG.error("Should never get here");
        return false;

    }
    
    @Override
    public boolean checkKeyOwned(Table table, List<Object> key) throws ReconfigurationException {
        
    		Object[] key_arr = key.toArray();
    		String table_name = table.getName().toLowerCase();
            int expectedPartition;
            int previousPartition;
            try {
                expectedPartition = partitionPlan.getPartitionId(table_name, key);
                previousPartition =  partitionPlan.getPreviousPartitionId(table_name, key); 
            } catch (Exception e) {
                LOG.error("Exception trying to get partition IDs",e);
                throw new RuntimeException(e);
            }   
            if (expectedPartition == partition_id &&  previousPartition == partition_id)
            {
                //This partition should have the key and it didnt move     
                if (trace.val) LOG.trace(String.format("Key %s is at %s and did not migrate ",key,partition_id));
                return true;
            } else if (expectedPartition == partition_id &&  previousPartition != partition_id) {
                //Key should be moving to here
                if (debug.val) LOG.debug(String.format("Key %s should be at %s. Checking if migrated in",key,partition_id));
                
                //Has the key been received as a single key
                if (checkMigratedMapSet(migratedKeyIn,table_name,key)== true){
                    if (debug.val) LOG.debug(String.format("Key has been migrated in %s (%s)",key,table_name));
                    return true;
                } else {                       
                    //check if the key was received out in a range        
                    for(ReconfigurationRange range : this.dataMigratedIn){
                        if(range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                            return true;
                        }
                    }
                    List<String> relatedTables = partitionPlan.getRelatedTables(table_name);
                    if (debug.val && relatedTables != null){
                        LOG.debug(String.format("Table %s has related tables:%s",table_name, StringUtils.join(relatedTables, ',')));
                    }
                    // The key has not been received. Throw an exception to notify
                    //It could be in a partial range, but that doesn't matter to us. Still need to pull the full range.
                    if (PULL_SINGLE_KEY) {
                        ReconfigurationException ex = null;
                        if(relatedTables == null){
                            ex = new ReconfigurationException(ExceptionTypes.TUPLES_NOT_MIGRATED, table, previousPartition, expectedPartition, key);
                        } else {
                            List<Table> relatedTablesToPull = new ArrayList<>();
                            for(String rTableName : relatedTables){
                            	Table rTable = this.partitionPlan.getCatalogContext().getTableByName(rTableName);
                            	if (checkMigratedMapSet(migratedKeyIn,rTableName,key)== false) {
                                    if (debug.val) LOG.debug(" Related table key not migrated, adding to pull : " + rTable);
                                    relatedTablesToPull.add(rTable);
                                } else {
                                    if (debug.val) LOG.debug(" Related table already pulled for key " + rTable);
                                }
                            }
                            ex = new ReconfigurationException(ExceptionTypes.TUPLES_NOT_MIGRATED, relatedTablesToPull, previousPartition, expectedPartition, key);
                        }
                        throw ex;
                    } else {
                        //FIXME highly inefficient
                        List<ReconfigurationRange> rangesToPull = new ArrayList<>();    
                        for(ReconfigurationRange range : this.incoming_ranges){
			    if(relatedTables == null) {
                                if(range.getTableName().equalsIgnoreCase(table_name) && range.inRangeIgnoreNullCols(key_arr)){
                                    LOG.info(String.format("Access for key %s, pulling entire range :%s (%s)", key, range.toString(),table_name));
                                    rangesToPull.add(range);
                                    //we only have table to match
                                    break;
                                }
                            }
                            else {
                            	for(String rTableName : relatedTables){
                                    if(range.getTableName().equalsIgnoreCase(rTableName) && range.inRangeIgnoreNullCols(key_arr)){
                                        if (dataMigratedIn.contains(range)){
                                            LOG.info(String.format("Range %s has already been migrated in. Not pulling again", range));
                                        } else {
                                            LOG.info(String.format("Access for key %s, pulling entire range :%s (%s)", key, range.toString(),rTableName));
                                            rangesToPull.add(range);
                                        }
                                    }
                                }
                            }

                        }
                        ReconfigurationException ex = new ReconfigurationException(ExceptionTypes.TUPLES_NOT_MIGRATED, previousPartition,expectedPartition,rangesToPull);
                        throw ex;
                    }
                }
                
            } else if (expectedPartition != partition_id &&  previousPartition == partition_id) {
                //Key should be moving away
                if (debug.val) LOG.debug(String.format("Key %s was at %s. Checking if migrated ",key,partition_id));
                
                //Check to see if we migrated this key individually
                if (checkMigratedMapSet(migratedKeyOut,table_name,key)){
                    ReconfigurationException ex = new ReconfigurationException(ExceptionTypes.TUPLES_MIGRATED_OUT,table, previousPartition,expectedPartition, key);
                    throw ex;
                }
                
                
                
                //check to see if this key was migrated in a range
                for(ReconfigurationRange range : this.dataMigratedOut){
                    if(range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                        ReconfigurationException ex = new ReconfigurationException(ExceptionTypes.TUPLES_MIGRATED_OUT,table, previousPartition,expectedPartition, key);
                        throw ex;
                    }
                }
                
              //check to see if this key was migrated in a range
                for(ReconfigurationRange range : this.dataPartiallyMigratedOut){
                    if(range.getTableName().equalsIgnoreCase(table_name) && range.inRange(key_arr)){
                        ReconfigurationException ex = new ReconfigurationException(ExceptionTypes.TUPLES_MIGRATED_OUT,table, previousPartition,expectedPartition, key);
                        throw ex;
                    }
                }
                
                return true;
            } else if (expectedPartition != partition_id &&  previousPartition != partition_id) {
                //We didnt have nor are are expected to
                if (debug.val) LOG.debug(String.format("Key %s is not at %s, nor was it previously",key,partition_id));

                return false;
            }
            
        
        return false;
    }

    @Override
    public boolean checkIfAllRangesAreMigratedIn() {       
        if(incomingRangesCount == rangesMigratedInCount){
            return true;
        } 
        return false;     
    }
    
    @Override
    public Set<Integer> getAllPartitionIds(List<CatalogType> catalog, List<Object> key) throws Exception {

    	String table_name = this.partitionPlan.getTableName(catalog);
    	Set<Integer> partitionIds = new HashSet<Integer>();
    	partitionIds.addAll(this.partitionPlan.getAllPartitionIds(table_name, key));
    	partitionIds.addAll(this.partitionPlan.getAllPreviousPartitionIds(table_name, key));
    	return partitionIds;
    }
    
    
}
