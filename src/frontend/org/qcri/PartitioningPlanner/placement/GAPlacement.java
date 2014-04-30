package org.qcri.PartitioningPlanner.placement;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jaga.definitions.GAParameterSet;
import org.jaga.definitions.GAResult;
import org.jaga.definitions.Individual;
import org.jaga.definitions.ReproductionAlgorithm;
import org.jaga.definitions.Fitness;
import org.jaga.hooks.AnalysisHook;
import org.jaga.individualRepresentation.greycodedNumbers.NDecimalsIndividual;
import org.jaga.individualRepresentation.greycodedNumbers.NDecimalsIndividualSimpleFactory;
import org.jaga.individualRepresentation.greycodedNumbers.RangeConstraint;
import org.jaga.masterAlgorithm.ReusableSimpleGA;
import org.jaga.masterAlgorithm.InitialPopulationGA;
import org.jaga.reproduction.greycodedNumbers.SimpleBinaryXOverWithMutation;
import org.jaga.selection.RouletteWheelSelection;
import org.jaga.selection.AbsoluteFitness;
import org.jaga.util.DefaultParameterSet;
import org.jaga.util.FittestIndividualResult;
import org.voltdb.utils.Pair;
import org.qcri.PartitioningPlanner.placement.Plan;


public class GAPlacement extends Placement {
	
	Long coldPartitionWidth = 100000L; // redistribute cold tuples in chunks of 100000
	ArrayList<Long> tupleIds = null;
	ArrayList<Long> accesses = null; 
	ArrayList<Integer> locations = null; 
	ArrayList<List<Plan.Range>> slices = null;
	ArrayList<Long> sliceSizes = null;
	int tupleCount = 0;
	int sliceCount = 0;
	Long totalAccesses = 0L;
	
	public GAPlacement(){
		
	}
	
	// initialize the private data members based on the input parameters
		private void init(ArrayList<Map<Long, Pair<Long,Integer> >> hotTuplesList, Map<Integer, Pair<Long,Integer>> partitionTotals, Plan aPlan, int partitionCount) {
			tupleIds = new ArrayList<Long>();
			accesses = new ArrayList<Long>(); 
			locations = new ArrayList<Integer>(); 
			slices = new ArrayList<List<Plan.Range>>();
			sliceSizes = new ArrayList<Long>();

			// copy partitionTotals into oldLoad
			totalAccesses = 0L;
			Map<Integer, Pair<Long,Integer>> oldLoad = new HashMap<Integer, Pair<Long,Integer>> ();
			for(Integer i : partitionTotals.keySet()) {
				totalAccesses += partitionTotals.get(i).getFirst();
				oldLoad.put(i,  partitionTotals.get(i));
			}

			// copy aPlan into oldPlan
			Plan oldPlan = new Plan();
			Map<Integer, List<Plan.Range>> ranges = aPlan.getAllRanges();
			for(Integer i : ranges.keySet()) {
				List<Plan.Range> partitionRanges = ranges.get(i);
				oldPlan.addPartition(i);
				for(Plan.Range range : partitionRanges) {
					oldPlan.addRange(i, range.from, range.to);
				}
			}

			// calculate the load and plan if the hot tuples were removed, store
			// them in oldLoad and oldPlan
			tupleCount = 0;
			Integer partitionId = 0;
			for(Map<Long, Pair<Long,Integer> >  hotTuples : hotTuplesList) {
				tupleCount += hotTuples.keySet().size();
				for(Long i : hotTuples.keySet()) {
					oldLoad.put(partitionId, new Pair<Long, Integer>(oldLoad.get(partitionId).getFirst() - hotTuples.get(i).getFirst(),
							oldLoad.get(partitionId).getSecond() - hotTuples.get(i).getSecond()));
					oldPlan.removeTupleId(partitionId, i);
				}
				++partitionId;
			}

			// store the ids, access counts, and locations of each of the hot tuples
			partitionId = 0;
			for(Map<Long, Pair<Long,Integer> >  hotTuples : hotTuplesList) {
				for(Long i : hotTuples.keySet()) {
					tupleIds.add(i);
					sliceSizes.add(hotTuples.get(i).getSecond().longValue());
					accesses.add(hotTuples.get(i).getFirst());
					locations.add(partitionId);
				}
				++partitionId;
			}

			// store the ranges, sizes, access counts, and locations of each of the slices of cold tuples
			sliceCount = 0;
			for(Integer i : oldPlan.getAllRanges().keySet()) { // for each partition
				// VOTER HACK: we want each partition slice to contain ~1000 tuples, but we don't know how many tuples
				// are in a range
				Double tuplesPerKey = (double) oldLoad.get(i).getSecond() / Plan.getRangeListWidth(oldPlan.getAllRanges(i));
				List<List<Plan.Range>> partitionSlices = oldPlan.getRangeSlices(i,  (long) (coldPartitionWidth / tuplesPerKey));
				if(partitionSlices.size() > 0) {
					sliceCount += partitionSlices.size();
					Double tupleWeight = (double) oldLoad.get(i).getFirst() / oldLoad.get(i).getSecond(); // per tuple - VOTER HACK
					for(List<Plan.Range> slice : partitionSlices) {  // for each slice
						// VOTER HACK
						Long sliceSize = (long) (Plan.getRangeListWidth(slice) * tuplesPerKey);
						Long newWeight = (long) (tupleWeight *  ((double) sliceSize));
						slices.add(slice);
						sliceSizes.add(sliceSize);
						accesses.add(newWeight);
						locations.add(i);
					} // end for each slice
				}
			} // end for each partition
		}
		
	
	// hotTuples: tupleId --> access count
	// siteLoads: partitionId --> total access count
	public Plan computePlan(ArrayList<Map<Long, Pair<Long,Integer> >> hotTuplesList, Map<Integer, Pair<Long,Integer>> partitionTotals, String planFilename, int partitionCount, int timeLimit){
		
		Plan aPlan = new Plan(planFilename);
		this.init(hotTuplesList, partitionTotals, aPlan, partitionCount);

		int placementCount = tupleCount + sliceCount; // number of placements we will make
		Long meanAccesses = totalAccesses / partitionCount;
		getHottestTuple(hotTuplesList);

		System.out.println("Mean access count: " + meanAccesses);
		
		// Set up the genetic algorithm
		GAParameterSet params = new DefaultParameterSet();
		params.setPopulationSize(100);
		GAPlacementFitness fitness = new GAPlacementFitness();
		fitness.initialize(tupleIds,accesses,locations,slices,sliceSizes,tupleCount,
				sliceCount,totalAccesses,partitionCount,_hotAccessCount); 
		params.setFitnessEvaluationAlgorithm(fitness);
		params.setSelectionAlgorithm(new RouletteWheelSelection(-10E10));
		params.setMaxGenerationNumber(100);
		ReproductionAlgorithm reprAlg = new SimpleBinaryXOverWithMutation(0.65, 0.05);
		params.setReproductionAlgorithm(reprAlg);
		int precision = (int) Math.ceil(Math.log(partitionCount)/Math.log(2)) + 1;
		NDecimalsIndividualSimpleFactory fact = new NDecimalsIndividualSimpleFactory(placementCount, 0, precision);
		for(int i = 0; i < placementCount; ++i) {
			fact.setConstraint(i, new RangeConstraint(0, partitionCount-1));
		}
		params.setIndividualsFactory(fact);
		InitialPopulationGA ga = new InitialPopulationGA();
		
		// seed the initial population with individuals matching the current plan
		Individual [] initialIndivs = new Individual[10];
		for(int i = 0; i < 10; ++i) {
			NDecimalsIndividual indiv = new NDecimalsIndividual(placementCount, 1, precision);
			for(int j = 0; j < locations.size(); j++) {
			    indiv.setDoubleValue(j, (double) locations.get(j));
			}
			initialIndivs[i] = indiv;
		}
		System.out.println("Initial plan has fitness: " + fitness.evaluateFitness(initialIndivs[0], 0, null, params).toString());
		ga.setInitialPopulation(initialIndivs);
		
		// Analysis for debugging
		//AnalysisHook hook = new AnalysisHook();
		//hook.setLogStream(System.out);
		//hook.setUpdateDelay(100);
		//hook.setAnalyseGenMinFit(true);
		//ga.addHook(hook);

		// Execute the genetic algorithm
		GAResult result = ga.exec(params);
		FittestIndividualResult fittestResult = (FittestIndividualResult) result;
		AbsoluteFitness fit = (AbsoluteFitness) fittestResult.getBestFitness();
		System.out.println("Fittest individual found with fitness: " + fit.toString());
		NDecimalsIndividual indiv = (NDecimalsIndividual) fittestResult.getFittestIndividual();

		// Update the plan based on the results
		for(int i = 0; i < indiv.getSize(); ++i) {
			Integer srcPartition = locations.get(i);
			Integer dstPartition = (int) indiv.getDoubleValue(i);
			if(srcPartition != dstPartition) {
				if(i < tupleCount) {
					Long id = tupleIds.get(i);
					aPlan.removeTupleId(srcPartition, id);
					if(!aPlan.hasPartition(dstPartition)) {
						aPlan.addPartition(dstPartition);
					}
					aPlan.addRange(dstPartition, id, id);
				}
				else {
					List<Plan.Range> slice = slices.get(i - tupleCount);
					for(Plan.Range r : slice) { 
						if(!aPlan.hasPartition(dstPartition)) {
							aPlan.addPartition(dstPartition);
						}

						List<Plan.Range> oldRanges = aPlan.getRangeValues(srcPartition, r.from, r.to);
						for(Plan.Range oldRange : oldRanges) {
							aPlan.removeRange(srcPartition, oldRange.from);
							aPlan.addRange(dstPartition, Math.max(oldRange.from, r.from), Math.min(oldRange.to, r.to));

							if(oldRange.from < r.from) {
								aPlan.addRange(srcPartition, oldRange.from, r.from - 1);
							}
							if(r.to < oldRange.to) {
								aPlan.addRange(srcPartition, r.to + 1, oldRange.to);
							}
						}
					}
				}
			}
		}

		

		aPlan = demoteTuples(hotTuplesList, aPlan);
		removeEmptyPartitions(aPlan);
		return aPlan;
		
	}
	

}
