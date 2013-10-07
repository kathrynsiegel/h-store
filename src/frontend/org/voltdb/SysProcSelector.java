/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
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
package org.voltdb;

public enum SysProcSelector {
    TABLE,            // invoked as @stat table
    INDEX,            // invoked as @stat index
    PROCEDURE,        // invoked as @stat procedure
    INITIATOR,         // invoked as @stat initiator
    PARTITIONCOUNT,
    IOSTATS,
    MEMORY,           // info about node's memory usage
    MANAGEMENT, //Returns pretty much everything
    SNAPSHOTSTATUS,
    TXNCOUNTER,     // transaction counter information
    TXNPROFILER,    // transaction profiler information
    EXECPROFILER,   // executor profiler information
    QUEUEPROFILER,  // transaction queue manager profiler information
    MARKOVPROFILER, // markov estimation profiler information
    SPECEXECPROFILER, // speculative execution profiler information
    SITEPROFILER, // network profiler information
    PLANNERPROFILER, // batch planner profiler information
    ANTICACHE,      // anti-cache manager information
    ANTICACHEEVICTIONS, // anti-cache eviction history
    ANTICACHEACCESS, // anti-cache evicted access history
    TXNRESPONSETIME,	// transaction response time - Marco
    CPUUSAGE,	// cpu usage - Essam
    TUPLE,	// invoked as @stat ttracker - Essam
    

}
