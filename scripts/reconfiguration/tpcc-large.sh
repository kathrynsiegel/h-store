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
FIRST_PARAM_OFFSET=0

EXP_TYPES=( \
    "reconfig-dynsplit --partitions=12 --benchmark-size=72 --splitplan=10 --plandelay=2000 --chunksize=20000 --asyncsize=2000 --asyncdelay=2000 --exp-suffix=s10-p2-cM-a2" 
    )

for b in tpcc; do
    PARAMS=( \
        --no-update \
        --results-dir=$DATA_DIR \
        --benchmark=$b \
        --stop-on-error \
        --exp-trials=1 \
        --exp-attempts=1 \        
        --no-json \
        --plot \
	    --client.interval=1000 \
        --client.output_interval=true \
        --client.duration=300000 \
        --client.warmup=30000 \
        --client.output_results_csv=interval_res.csv \
        --reconfig=60000:1:0 \
        --sweep-reconfiguration 
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
