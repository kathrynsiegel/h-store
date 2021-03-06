package edu.brown.hstore.callbacks;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.voltdb.ClientResponseImpl;
import org.voltdb.messaging.FastSerializer;

import com.google.protobuf.ByteString;
import com.google.protobuf.RpcCallback;

import edu.brown.hstore.HStoreSite;
import edu.brown.hstore.HStoreThreadManager;
import edu.brown.hstore.Hstoreservice.TransactionForwardToReplicaResponse;
import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;
import edu.brown.pools.Poolable;

/**
 * This callback is used by the receiving HStoreSite during a transaction redirect.
 * It must be given the TransactionRedirectResponse callback so that we know how
 * pass the ClientResponse back to the other HStoreSite, which will then send the
 * results back to the client
 * @author pavlo
 */
public class TransactionForwardToReplicaResponseCallback implements RpcCallback<ClientResponseImpl>, Poolable {
    private static final Logger LOG = Logger.getLogger(TransactionRedirectResponseCallback.class);
    private static final LoggerBoolean debug = new LoggerBoolean();
    private static final LoggerBoolean trace = new LoggerBoolean();
    static {
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    
    private RpcCallback<TransactionForwardToReplicaResponse> orig_callback;
    private HStoreSite hstore_site;
    private long orig_txn_id;
    
    /** Our local site id */
    private int sourceSiteId = -1;
    private int destSiteId = -1;

    /**
     * Default Constructor
     */
    public TransactionForwardToReplicaResponseCallback(HStoreSite hstore_site) {
    	this.hstore_site = hstore_site;
    }
    
    public void init(int source_id, int dest_id, long orig_txn_id, RpcCallback<TransactionForwardToReplicaResponse> orig_callback) {
        this.orig_callback = orig_callback;
        this.sourceSiteId = source_id;
        this.destSiteId = dest_id;
        this.orig_txn_id = orig_txn_id;
        LOG.info(String.format("initializing callback %s",this.toString()));
    }

    @Override
    public boolean isInitialized() {
        return (this.orig_callback != null);
    }
    
    @Override
    public void finish() {
        this.orig_callback = null;
        this.sourceSiteId = -1;
        this.destSiteId = -1;
    }
    
    @Override
    public void run(ClientResponseImpl parameter) {
    	LOG.info(String.format("About to call response callback for txn %s", this.orig_txn_id));
        FastSerializer fs = new FastSerializer();
        try {
            parameter.writeExternal(fs);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        ByteString bs = ByteString.copyFrom(fs.getBuffer());
        TransactionForwardToReplicaResponse response = TransactionForwardToReplicaResponse.newBuilder()
                                                              .setSenderSite(this.sourceSiteId)
                                                              .setOutput(bs)
                                                              .setOrigTxnId(this.orig_txn_id)
                                                              .build();
        this.orig_callback.run(response);
        if (debug.val)
            LOG.debug(String.format("Sent back ClientResponse for txn #%d to %s [bytes=%d]",
                      parameter.getTransactionId(), HStoreThreadManager.formatSiteName(this.destSiteId),
                      bs.size()));
    }
    
    public long getOrigTxnId() {
    	return this.orig_txn_id;
    }
}
