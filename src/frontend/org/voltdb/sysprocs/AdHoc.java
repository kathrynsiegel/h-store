/* This file is part of VoltDB.
 * Copyright (C) 2008-2012 VoltDB Inc.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.sysprocs;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.voltdb.BackendTarget;
import org.voltdb.DependencySet;
import org.voltdb.ParameterSet;
import org.voltdb.ProcInfo;
import org.voltdb.VoltSystemProcedure;
import org.voltdb.VoltTable;
import org.voltdb.jni.ExecutionEngine;

import edu.brown.hstore.HStoreConstants;
import edu.brown.hstore.PartitionExecutor;
import edu.brown.hstore.PartitionExecutor.SystemProcedureExecutionContext;
import edu.brown.hstore.txns.AbstractTransaction;
import edu.brown.logging.LoggerUtil;
import edu.brown.logging.LoggerUtil.LoggerBoolean;


////Essam Del
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Execute a user-provided SQL statement. This code coordinates the execution of
 * the plan fragments generated by the embedded planner process.
 */
@ProcInfo(singlePartition = false)
public class AdHoc extends VoltSystemProcedure {
    private static final Logger LOG = Logger.getLogger(AdHoc.class);
    private static final LoggerBoolean debug = new LoggerBoolean();
    static {
        LoggerUtil.attachObserver(LOG, debug);
    }


    final int AGG_DEPID = 1;
    final int COLLECT_DEPID = 2 | HStoreConstants.MULTIPARTITION_DEPENDENCY;

    @Override
    public void initImpl() {
        this.registerPlanFragment(SysProcFragmentId.PF_runAdHocFragment);
    }

    @Override
    public DependencySet executePlanFragment(Long txn_id, Map<Integer, List<VoltTable>> dependencies, int fragmentId, ParameterSet params, SystemProcedureExecutionContext context) {
        
    	// get the three params (depId, json plan, sql stmt)
        int outputDepId = (Integer) params.toArray()[0];
        String plan = (String) params.toArray()[1];
        String sql = (String) params.toArray()[2];
        int inputDepId = -1;
        
        // make dependency ids available to the execution engine
        if ((dependencies != null) && (dependencies.size() > 00)) {
            assert(dependencies.size() <= 1);
            for (int x : dependencies.keySet()) {
                inputDepId = x; break;
            }
            context.getExecutionEngine().stashWorkUnitDependencies(dependencies);
        }

        VoltTable table = null;

        if (executor.getBackendTarget() == BackendTarget.HSQLDB_BACKEND) {
            // Call HSQLDB
            assert(sql != null);
            // table = m_hsql.runDML(sql);
        }
        else
        {
            assert(plan != null);
            
            ExecutionEngine ee = context.getExecutionEngine();
            AbstractTransaction ts = this.hstore_site.getTransaction(txn_id);
            
            // Enable read/write set tracking
            if (hstore_conf.site.exec_readwrite_tracking) {
                if (debug.val)
                    LOG.trace(String.format("%s - Enabling read/write set tracking in EE at partition %d",
                              ts, this.partitionId));
                ee.trackingEnable(txn_id);
            }
            
            // Always mark this information for the txn so that we can
            // rollback anything that it may do
            ts.markExecNotReadOnly(this.partitionId);
            ts.markExecutedWork(this.partitionId);
            
            table = context.getExecutionEngine().
                executeCustomPlanFragment(plan, outputDepId, inputDepId, txn_id,
                                          context.getLastCommittedTxnId(),
                                          ts.getLastUndoToken(this.partitionId));
        }

        
      return new DependencySet(new int[]{ outputDepId }, new VoltTable[]{ table });
    }

    /**
     * Parameters to the run method are created internally and do not match
     * the input the user passes to {@link org.voltdb.client.Client#callProcedure}.
     * The user passes a single parameter, the SQL statement to compile and
     * execute.
     *
     * @param ctx                         Internal.
     * @param aggregatorFragment          Internal.
     * @param collectorFragment           Internal.
     * @param sql                         User provided SQL statement.
     * @param isReplicatedTableDML        Internal.
     * @return The result of the user's query. If the user's SQL statement was
     * a DML query, a table with a single untitled column is returned containing
     * a single {@link org.voltdb.VoltType#BIGINT} row value: the number of tuples
     * affected. This DML output matches the usual DML result from a VoltDB stored
     * procedure.
     */
    public VoltTable[] run(String aggregatorFragment, String collectorFragment,
                           String sql, int isReplicatedTableDML) {

        boolean replicatedTableDML = isReplicatedTableDML == 1;

        SynthesizedPlanFragment[] pfs = null;
        VoltTable[] results = null;
        ParameterSet params = null;
        if (executor.getBackendTarget() == BackendTarget.HSQLDB_BACKEND) {
            pfs = new SynthesizedPlanFragment[1];

            // JUST SEND ONE FRAGMENT TO HSQL, IT'LL IGNORE EVERYTHING BUT SQL AND DEPID
            pfs[0] = new SynthesizedPlanFragment();
            pfs[0].fragmentId = SysProcFragmentId.PF_runAdHocFragment;
            pfs[0].outputDependencyIds = new int[]{ AGG_DEPID };
            pfs[0].multipartition = false;
            params = new ParameterSet();
            params.setParameters(AGG_DEPID, "", sql);
            pfs[0].parameters = params;
        }
        else {
            pfs = new SynthesizedPlanFragment[2];

            if (collectorFragment != null) {
                pfs = new SynthesizedPlanFragment[2];

                // COLLECTION FRAGMENT NEEDS TO RUN FIRST
                pfs[1] = new SynthesizedPlanFragment();
                pfs[1].fragmentId = SysProcFragmentId.PF_runAdHocFragment;
                pfs[1].outputDependencyIds = new int[]{ COLLECT_DEPID };
                pfs[1].multipartition = true;
                params = new ParameterSet();
                params.setParameters(COLLECT_DEPID, collectorFragment, sql);
                pfs[1].parameters = params;
            }
            else {
                pfs = new SynthesizedPlanFragment[1];
            }

            // AGGREGATION FRAGMENT DEPENDS ON THE COLLECTION FRAGMENT
            pfs[0] = new SynthesizedPlanFragment();
            pfs[0].fragmentId = SysProcFragmentId.PF_runAdHocFragment;
            pfs[0].outputDependencyIds = new int[]{ AGG_DEPID };
            if (collectorFragment != null)
                pfs[0].inputDependencyIds = new int[] { COLLECT_DEPID };
            pfs[0].multipartition = false;
            params = new ParameterSet();
            params.setParameters(AGG_DEPID, aggregatorFragment, sql);
            pfs[0].parameters = params;
        }

        // distribute and execute these fragments providing pfs and id of the
        // aggregator's output dependency table.
        results =
            executeSysProcPlanFragments(pfs, AGG_DEPID);

        // rather icky hack to handle how the number of modified tuples will always be
        // inflated when changing replicated tables - the user really doesn't want to know
        // the big number, just the small one
        // PAVLO: 2013-07-07
        // This hack is no longer needed because we will aggregate the # of modified
        // tuples in the EE correctly.
//        if (replicatedTableDML) {
//            assert(results.length == 1);
//            long changedTuples = results[0].asScalarLong();
//            // int num_partitions = catalogContext.numberOfPartitions;
//            // assert((changedTuples % num_partitions) == 0);
//
//            VoltTable retval = new VoltTable(new VoltTable.ColumnInfo("", VoltType.BIGINT));
////            retval.addRow(changedTuples / num_partitions);
//            retval.addRow(changedTuples);
//            results[0] = retval;
//        }
        
        

        return results;
    }
}
