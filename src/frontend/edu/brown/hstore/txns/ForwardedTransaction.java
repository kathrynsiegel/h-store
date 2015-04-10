package edu.brown.hstore.txns;

import org.voltdb.ClientResponseImpl;
import org.voltdb.ParameterSet;
import org.voltdb.catalog.Procedure;

import com.google.protobuf.RpcCallback;

import edu.brown.hstore.HStoreSite;
import edu.brown.hstore.Hstoreservice.TransactionForwardToReplicaResponse;
import edu.brown.utils.PartitionSet;

public class ForwardedTransaction extends LocalTransaction {
	
	/**
     * Final RpcCallback to the client
     */
    private RpcCallback<TransactionForwardToReplicaResponse> replica_callback;

    /**
     * Constructor
     * This does not fully initialize this transaction.
     * You must call init() before this can be used
     */
	public ForwardedTransaction(HStoreSite hstore_site) {
		super(hstore_site);
	}

	public ForwardedTransaction init(Long txn_id,
            long initiateTime,
            long clientHandle,
            int base_partition,
            PartitionSet predict_touchedPartitions,
            boolean predict_readOnly,
            boolean predict_abortable,
            Procedure catalog_proc,
            ParameterSet params,
            RpcCallback<ClientResponseImpl> client_callback,
            RpcCallback<TransactionForwardToReplicaResponse> replica_callback) {
		super.init(txn_id, 
				initiateTime, 
				clientHandle, 
				base_partition, 
				predict_touchedPartitions, 
				predict_readOnly, 
				predict_abortable, 
				catalog_proc, 
				params, 
				client_callback);
		this.replica_callback = replica_callback;
		return (this);
	}
	
	/**
     * Return the original callback that will send the final results back to the client
     * @return
     */
    public RpcCallback<TransactionForwardToReplicaResponse> getReplicaCallback() {
        return (this.replica_callback);
    }
}
