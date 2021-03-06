package edu.brown.hstore.callbacks;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.google.protobuf.RpcCallback;

import edu.brown.hstore.HStoreSite;
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
public class TransactionForwardToReplicaCallback implements RpcCallback<TransactionForwardToReplicaResponse> {
    private static final Logger LOG = Logger.getLogger(TransactionRedirectResponseCallback.class);
    private static final LoggerBoolean debug = new LoggerBoolean();
    private static final LoggerBoolean trace = new LoggerBoolean();
    static {
        LoggerUtil.attachObserver(LOG, debug, trace);
    }
    
    private int numDestinationSites;
//    private Semaphore permits;

    /**
     * Default Constructor
     */
    public TransactionForwardToReplicaCallback(int numDestinationSites) {
//    	this.numDestinationSites = numDestinationSites;
//    	LOG.info(String.format("initializing callback %s with %s permits",this.toString(), this.numDestinationSites));
//        this.permits = new Semaphore(this.numDestinationSites, true);
//        try {
//			this.permits.acquire(this.numDestinationSites);
//		} catch (InterruptedException e) {
//			// ignore silently
//		}
//        LOG.info(String.format("callback %s now has %s permits available", this.toString(),this.permits.availablePermits()));
    }
    
    @Override
    public void run(TransactionForwardToReplicaResponse parameter) {
        if (debug.val)
            LOG.debug(String.format("Reached forwarded callback"));
        LOG.info(String.format("Reached forwarded callback %s", this.toString()));
//        this.permits.release();
//        LOG.info(String.format("now there are %s permits for %s", this.permits.availablePermits(), this.toString()));
    }
    
    public void waitForFinish() {
//    	LOG.info(String.format("current number of permits available: %s",this.permits.availablePermits()));
//    	try {
//			boolean acquired = this.permits.tryAcquire(this.numDestinationSites, 5, TimeUnit.SECONDS);
//			if (acquired) {
//				LOG.info("successfully acquired while waiting for finish");
//			} else {
//				LOG.info("reached timeout when waiting for finish");
//			}
//		} catch (InterruptedException e) {
//			LOG.info("uh oh error 2");
//		}
//    	try {
//			this.permits.acquire(this.numDestinationSites);
//		} catch (InterruptedException e) {
//			LOG.info("uh oh error 2");
//			// silently ignore
//		}
    	
    	// all done! (this is probably a bad way to do this)
//    	this.permits.release(this.numDestinationSites);
    }
}
