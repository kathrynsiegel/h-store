package edu.brown.hstore.internal;

import org.voltdb.VoltTable;

import edu.brown.hashing.ExplicitPartitions;
//import edu.brown.hashing.ReconfigurationPlan;
//import edu.brown.hstore.reconfiguration.ReconfigurationConstants.ReconfigurationProtocols;
//import edu.brown.hstore.reconfiguration.ReconfigurationCoordinator.ReconfigurationState;
import edu.brown.profilers.ProfileMeasurement;

public class ReplicaLoadTableMessage extends InternalMessage {
	public enum RequestType {
		INIT_RECONFIGURATION,
		END_RECONFIGURATION
	}
    
	private RequestType requestType;	
	public String clusterName;
	public String databaseName;
	public String tableName;
	public VoltTable data;
	public int allowELT;
	public long createTime;
    
    
    public ReplicaLoadTableMessage(
    		String clusterName,
			String databaseName, String tableName, 
			VoltTable data, int allowELT) {
        super();
        this.clusterName = clusterName;
        this.databaseName = databaseName;
        this.tableName = tableName;
        this.data = data;
        this.allowELT = allowELT;
        this.createTime = ProfileMeasurement.getTime();
    }

    public long getQueueTime(){
        return ProfileMeasurement.getTime() - this.createTime;
    }

    public String getClusterName(){
        return clusterName;
    }
    
    public String getDatabaseName() {
    	return databaseName;
    }
    
	public String getTableName() {
		return tableName;
	}
	
    public VoltTable getData() {
    	return data;
    }
    
    public int allowELT() {
    	return allowELT;
    }
    
}
