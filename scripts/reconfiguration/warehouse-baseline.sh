#!/bin/bash

# ---------------------------------------------------------------------

trap onexit 1 2 3 15
function onexit() {
    local exit_status=${1:-$?}
    exit $exit_status
}

# ---------------------------------------------------------------------

DATA_DIR="out"
FABRIC_TYPE="ssh"
FIRST_PARAM_OFFSET=1


EXP_TYPES=( \
    "warehouse-baseline-2 --partitions=8 --benchmark.size=8"  \
    "warehouse-baseline-2 --partitions=8 --benchmark.size=12"  \
    "warehouse-baseline-2 --partitions=8 --benchmark.size=16"  \
    "warehouse-baseline-2 --partitions=8 --benchmark.size=20"  \
    "warehouse-baseline-2 --partitions=8 --benchmark.size=24"  \
    "warehouse-baseline-3 --partitions=12 --benchmark.size=12"  \
    "warehouse-baseline-3 --partitions=12 --benchmark.size=16"  \
    "warehouse-baseline-3 --partitions=12 --benchmark.size=20"  \
    "warehouse-baseline-3 --partitions=12 --benchmark.size=24"  \
    "warehouse-baseline-4 --partitions=16 --benchmark.size=16"  \
    "warehouse-baseline-4 --partitions=16 --benchmark.size=20"  \
    "warehouse-baseline-4 --partitions=16 --benchmark.size=24"  \
)


#for b in smallbank tpcc seats; do
for b in tpcc ycsb; do
# for b in seats; do
    PARAMS=( \
        --no-update \
        --benchmark=$b \
        --stop-on-error \
        --exp-trials=1 \
        --exp-attempts=1 \        
        --client.interval=30000 \
        --client.output_interval=true \
        --client.duration=150000 \
        --client.warmup=60000 \
        --client.output_results_csv=interval_res.csv \
  	    --overwrite \
    )
    
    i=0
    cnt=${#EXP_TYPES[@]}
    while [ "$i" -lt "$cnt" ]; do
        ./experiment-runner.py $FABRIC_TYPE ${PARAMS[@]:$FIRST_PARAM_OFFSET} \
            --exp-type=${EXP_TYPES[$i]}
        FIRST_PARAM_OFFSET=0
        i=`expr $i + 1`
    done

done
