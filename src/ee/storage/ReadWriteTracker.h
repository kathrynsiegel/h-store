/* Copyright (C) 2013 by H-Store Project
 * Brown University
 * Massachusetts Institute of Technology
 * Yale University
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

#ifndef HSTORE_READWRITETRACKER_H
#define HSTORE_READWRITETRACKER_H

#include <string>
#include "boost/unordered_set.hpp"
#include "boost/unordered_map.hpp"
#include "common/tabletuple.h"
#include "common/TupleSchema.h"
#include "storage/table.h"

#include "storage/tablefactory.h" //Essam
#include <iostream>
#include <stdio.h>
#include <fstream>
#include <algorithm>
#include <cassert>
using namespace std;
//#include "storage/TupleTrackerInfo.h"//Essam


typedef boost::unordered_set<uint32_t> RowOffsets;


/////////////////////////////////////////////////////////
namespace voltdb {

class ExecutorContext;
class TableTuple;
class TupleSchema;
class Table;


/**
 * Tuple Tracker for all transactions
 */
/////////////////////////////////////////////////
////Essam tuple tracker class
typedef struct {
    	//int hostID;
    	//int siteID;
    	int partitionId;
    	int64_t txnId;
    	std::string tableName;
    	uint32_t tupleID;
    	int64_t accesses;
    	} TrackingInfo;

 typedef  boost::unordered_map<std::string, TrackingInfo*> map_accesses;


class TupleTrackerInfo {

private:



    /*
     * trackingInfo per transaction are hashed by (tableName+TupleID) as a string Key.
     */
   boost::unordered_map<std::string, TrackingInfo*> m_trackingInfo;

   ExecutorContext *executorContext;
   TupleSchema *resultTupleTrackerSchema;
   Table *resultTupleTrackerTable;


public:

   /*
        * trackingInfo per partition are hashed by (tableName+TupleID) as a string Key.
        */
       boost::unordered_map<int64_t, map_accesses> m_transTrackingInfo;

    TupleTrackerInfo(){

    	CatalogId databaseId = 1;

    	this->resultTupleTrackerSchema = TupleSchema::createTrackerTupleSchema();



    	    std::string *resultColumnNames = new std::string[this->resultTupleTrackerSchema->columnCount()];
    	    resultColumnNames[0] = std::string("Partition_ID");
    	    resultColumnNames[0] = std::string("TABLE_NAME");
    	    resultColumnNames[1] = std::string("TUPLE_ID");
    	    resultColumnNames[0] = std::string("Accesses");

    	    //*
    	    this->resultTupleTrackerTable = reinterpret_cast<Table*>(voltdb::TableFactory::getTempTable(
    	                databaseId,
    	                std::string("TupleTrackerInfo"),
    	                this->resultTupleTrackerSchema,
    	                resultColumnNames,
    	                NULL));
    	    //*/

    }
   ~TupleTrackerInfo(){
	   /*

	boost::unordered_map<std::string, TrackingInfo*>::const_iterator ite;
   	for (ite = m_trackingInfo.begin(); ite != m_trackingInfo.end(); ++ite)
   	    delete ite->second;

   	   //*/

   }



   void incrementAccessesPerTrans(int partitionId,int64_t txnId,std::string tableName, uint32_t tupleId, int64_t accesses){

   	    boost::unordered_map<int64_t, map_accesses>::const_iterator lookup = m_transTrackingInfo.find(txnId); // map <table+tuple, trackingInfo > per trans

   	           	if(lookup != m_transTrackingInfo.end())
   	           	{

   	           		//This transaction has a map lookup->second

   	           	incrementAccessesPerTableTuple(lookup->second,partitionId,txnId,tableName.c_str(),tupleId,accesses);


   	           	}
   	           	else
   	           	{

   	           	m_transTrackingInfo.insert ( std::make_pair (txnId, map_accesses() ) );
   	            incrementAccessesPerTableTuple(m_transTrackingInfo[txnId],partitionId,txnId,tableName.c_str(),tupleId,accesses);



   	           	}


   	  }



   void incrementAccessesPerTableTuple(map_accesses map,int partitionId,int64_t txnId,std::string tableName, uint32_t tupleId, int64_t accesses){

   	   std::stringstream ss ;
   	   ss << tupleId ;

   	   std::string key = tableName + ss.str();

   	   boost::unordered_map<std::string, TrackingInfo*>::const_iterator lookup = map.find(key);

   	           	if(lookup != map.end())
   	           	{
   	           	    // key already exists
   	           		lookup->second->accesses = lookup->second->accesses + accesses;
   	           	}
   	           	else
   	           	{
   	           	    // the key does not exist in the map
   	           	    // add it to the map

   	           		TrackingInfo* tupleInfo= new TrackingInfo();

   	           		tupleInfo->partitionId= partitionId;
   	           	    tupleInfo->txnId= txnId;
   	           		tupleInfo->tableName= tableName;
   	           		tupleInfo->tupleID= tupleId;
   	           		tupleInfo->accesses= accesses;


   	           	    map.insert(std::make_pair(key, tupleInfo));

   	           	///Essam del
   	           	   	                     //*
   	           	   	                     ofstream myfile5;
   	           	   	                     myfile5.open ("incrementAccessesPerTableTuple.del");//Essam
   	           	   	                     myfile5 << " trans ="<<txnId<<" Table = "<<tableName;
   	           	   	                     myfile5 << " tupleId ="<<tupleId<<" accesses = "<<accesses;
   	           	   	                     myfile5 << "\n";
   	           	   	                     myfile5.close();
   	           	   	         			//*/

   	           	}


   	           //	printInfo();

       }


   void incrementAccesses(int partitionId,int64_t txnId,std::string tableName, uint32_t tupleID, int64_t accesses){

	   std::stringstream ss ;
	   ss << tupleID ;

	   std::string key = tableName + ss.str();

	   boost::unordered_map<std::string, TrackingInfo*>::const_iterator lookup = m_trackingInfo.find(key);

	           	if(lookup != m_trackingInfo.end())
	           	{
	           	    // key already exists
	           		lookup->second->accesses = lookup->second->accesses + accesses;
	           	}
	           	else
	           	{
	           	    // the key does not exist in the map
	           	    // add it to the map

	           		TrackingInfo* tupleInfo= new TrackingInfo();

	           		tupleInfo->partitionId= partitionId;
	           		tupleInfo->tableName= tableName;
	           		tupleInfo->tupleID= tupleID;
	           		tupleInfo->accesses= accesses;

	           	    m_trackingInfo.insert(std::make_pair(key, tupleInfo));

	           	}


	           //	printInfo();

    }




   void printInfoTransMap() {



    	  boost::unordered_map<std::string, TrackingInfo*>::const_iterator iter = m_trackingInfo.begin();

    	 ///Essam del
    	  ofstream myfile1;
    	  myfile1.open ("printInfoTransMap.del");
    	  myfile1 << " The Map Info size is "<<m_trackingInfo.size()<<"\n";

    	  /*/
    	 	   ofstream myfile1;
    	 	   std::stringstream ss ;
    	 	   ss << "TupleInfo_Trans"<<iter->second->txnId<<".del" ;

    	 	 std::string fileName=ss.str();
    	 	   myfile1.open (fileName.c_str());
    	 	   myfile1 << " The Map Info of Trans: "<<iter->second->txnId<<"its size is "<<m_trackingInfo.size()<<"\n";

    	        myfile1 << " |Partition ID";
    	        myfile1 << " |Trans ID";
    	        myfile1 << " |Table Name";
    	        myfile1 << " |Tuple ID";
    	        myfile1 << " |Accesses|";
    	  	   myfile1 << "\n";
    	  	   //*/

       /*
   	   int k=0;
          while (iter != m_trackingInfo.end()) {

              myfile1 << iter->second->partitionId<<"\t";
              myfile1 << iter->second->txnId<<"\t";
              myfile1 << iter->second->tableName<<"\t";
              myfile1 << iter->second->tupleID<<"\t";
              myfile1 << iter->second->accesses<<"\n";

              k++;
              if(k>100)
           	   break;

              iter++;
          } // WHILE
          //*/

          myfile1.close();

          return;
      }


   void printInfo() {



 	  boost::unordered_map<std::string, TrackingInfo*>::const_iterator iter = m_trackingInfo.begin();

 	 ///Essam del
 	 	   ofstream myfile1;
 	 	   std::stringstream ss ;
 	 	   ss << "TupleInfo"<<iter->second->partitionId<<".del" ;

 	 	 std::string fileName=ss.str();
 	 	   myfile1.open (fileName.c_str());
 	 	   myfile1 << " The Map Info of "<<iter->second->partitionId<<"its size is "<<m_trackingInfo.size()<<"\n";

 	        myfile1 << " |Partition ID";
 	        myfile1 << " |Table Name";
 	        myfile1 << " |Tuple ID";
 	        myfile1 << " |Accesses|";
 	  	   myfile1 << "\n";
 	  	   //*/

	   int k=0;
       while (iter != m_trackingInfo.end()) {

           myfile1 << iter->second->partitionId<<"\t";
           myfile1 << iter->second->tableName<<"\t";
           myfile1 << iter->second->tupleID<<"\t";
           myfile1 << iter->second->accesses<<"\n";

           k++;
           if(k>100)
        	   break;

           iter++;
       } // WHILE


       myfile1.close();
     	                                 	 	              //*/
       return;
   }

   static bool myFunction(std::pair<std::string, TrackingInfo*> first, std::pair<std::string, TrackingInfo*> second){

	    return (first.second->accesses > second.second->accesses);

   }

   void printSortedInfo() {

	   std::vector<std::pair<std::string, TrackingInfo*> > myVec(m_trackingInfo.begin(), m_trackingInfo.end());


	   std::sort(myVec.begin(), myVec.end() , myFunction);


	   std::vector< std::pair<std::string, TrackingInfo*> >::const_iterator iter = myVec.begin();

	   //*
   	 ///Essam del
   	 	   ofstream myfile1;
   	 	   std::stringstream ss ;
   	 	   ss << "TupleInfo"<<iter->second->partitionId<<".del" ;

   	 	 std::string fileName=ss.str();
   	 	   myfile1.open (fileName.c_str());
   	 	   myfile1 << " The Map Info of "<<iter->second->partitionId<<"its size is "<<m_trackingInfo.size()<<"\n";

   	        myfile1 << " |Partition ID";
   	        myfile1 << " |Table Name";
   	        myfile1 << " |Tuple ID";
   	        myfile1 << " |Accesses|";
   	  	   myfile1 << "\n";


  	   int k=0;
         while (iter != myVec.end()) {

             myfile1 << iter->second->partitionId<<"\t";
             myfile1 << iter->second->tableName<<"\t";
             myfile1 << iter->second->tupleID<<"\t";
             myfile1 << iter->second->accesses<<"\n";

             k++;
             if(k>100)
          	   break;

             iter++;
         } // WHILE


         myfile1.close();
       	//*/
         return;
     }






};//Class
    
/**
 * Read/Write Tuple Tracker for a single transaction
 */
class ReadWriteTracker {
    
    friend class ReadWriteTrackerManager;
    
    public:
        ReadWriteTracker(int64_t txnId, TupleTrackerInfo* tupleTrackerInfo,int32_t partId);
        ~ReadWriteTracker();
        
        void markTupleRead(const std::string tableName, TableTuple *tuple);
        void markTupleWritten(const std::string tableName, TableTuple *tuple);
        
        void clear();
        
        std::vector<std::string> getTablesRead();
        std::vector<std::string> getTablesWritten();
        
    private:
        void insertTuple(boost::unordered_map<std::string, RowOffsets*> *map, const std::string tableName, TableTuple *tuple);
        std::vector<std::string> getTableNames(boost::unordered_map<std::string, RowOffsets*> *map) const;
        
        int64_t txnId;
        
        // TableName -> RowOffsets
        boost::unordered_map<std::string, RowOffsets*> reads;
        boost::unordered_map<std::string, RowOffsets*> writes;
        
        //Essam
          TupleTrackerInfo* rw_tupleTrackerInfo;
          int32_t partitionId;



}; // CLASS

/**
 * ReadWriteTracker Manager 
 */
class ReadWriteTrackerManager {
    public:
        ReadWriteTrackerManager(ExecutorContext *ctx);
        ~ReadWriteTrackerManager();
    
        ReadWriteTracker* enableTracking(int64_t txnId, int32_t partId);

        ReadWriteTracker* getTracker(int64_t txnId);
        void removeTracker(int64_t txnId);
        
       Table* getTuplesRead(ReadWriteTracker *tracker);
        Table* getTuplesWritten(ReadWriteTracker *tracker);
        
        //Essam tuple tracker
        int32_t partitionId;
        TupleTrackerInfo* getTupleTracker(int64_t txnId);
        void removeTupleTracker(int64_t txnId);//Essam
        void printTupleTrackers();//Essam

    private:
        void getTuples(boost::unordered_map<std::string, RowOffsets*> *map) const;
        
        void aggregateTupleTrackingPerPart();
        void insertIntoTupleTrackingPerPart(boost::unordered_map<int64_t, map_accesses> map);
        void insertTrackingInfoPerPart(map_accesses map,int partitionId, std::string tableName, uint32_t tupleID, int64_t accesses);
        void incrementAccessesPerPart(int partitionId, std::string tableName, uint32_t tupleId, int64_t accesses);



        ExecutorContext *executorContext;
        TupleSchema *resultSchema;
        Table *resultTable;
        boost::unordered_map<int64_t, ReadWriteTracker*> trackers;

        //Essam
        static boost::unordered_map<int64_t, TupleTrackerInfo*> tupleTrackers; //per transaction
        static boost::unordered_map<int32_t, map_accesses> tupleTrackersPerPart; //per partition
        static int totalMonitoredTrans;



}; // CLASS




}
#endif
