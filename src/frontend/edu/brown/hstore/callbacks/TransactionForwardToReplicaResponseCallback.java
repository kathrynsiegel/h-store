package edu.brown.hstore.callbacks;

import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;

import com.google.protobuf.RpcCallback;

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
public class TransactionForwardToReplicaResponseCallback implements RpcCallback<TransactionForwardToReplicaResponse>, Poolable {
    private static final Logger LOG = Logger.getLogger(TransactionRedirectResponseCallback.class);
    private static final LoggerBoolean debug = new LoggerBoolean();
    private static final LoggerBoolean trace = new LoggerBoolean();
    static {
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    
    private int numDestinationSites;
    Semaphore permits;
    
    /** Our local site id */
//    private int sourceSiteId = -1;
//    private int destSiteId = -1;

    /**
     * Default Constructor
     */
    public TransactionForwardToReplicaResponseCallback() {
    	// TODO
    }
    
    public void init(int numDestinationSites) {
    	this.numDestinationSites = numDestinationSites;
        this.permits = new Semaphore(this.numDestinationSites, true);
        try {
			this.permits.acquire(this.numDestinationSites);
		} catch (InterruptedException e) {
			// ignore silently
		}
    }

    @Override
    public boolean isInitialized() {
        return (this.permits != null);
    }
    
    @Override
    public void finish() {
        this.permits = null;
        this.numDestinationSites = -1;
    }
    
    @Override
    public void run(TransactionForwardToReplicaResponse parameter) {
        if (debug.val)
            LOG.debug(String.format("Reached forwarded callback"));
        this.permits.release();
    }
    
    public void waitForFinish() {
    	try {
			this.permits.acquire(this.numDestinationSites);
		} catch (InterruptedException e) {
			// silently ignore
		}
    	// all done! (this is probably a bad way to do this)
    	this.permits.release(this.numDestinationSites);
    }
}
