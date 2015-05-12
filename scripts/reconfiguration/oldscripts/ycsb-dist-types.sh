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
#    "stopcopy-2 --partitions=8 --benchmark-size=1000000 --exp-suffix=med1contract --reconfig=445000:1:0 " \
#    "reconfig-2 --partitions=8 --benchmark-size=1000000 --exp-suffix=med1contract --reconfig=445000:1:0 " \
    "stopcopy-ycsb-zipf --partitions=8 --benchmark-size=1000000 --exp-suffix=med2contract --reconfig=245000:2:0 " \
    "stopcopy-ycsb-hotspot --partitions=8 --benchmark-size=1000000 --exp-suffix=med2contract --reconfig=245000:2:0 " \
    "stopcopy-ycsb-uniform --partitions=8 --benchmark-size=1000000 --exp-suffix=med2contract --reconfig=245000:2:0 " \
    "reconfig-ycsb-zipf --partitions=8 --benchmark-size=1000000 --exp-suffix=med2contract --reconfig=245000:2:0 " \
    "reconfig-ycsb-hotspot --partitions=8 --benchmark-size=1000000 --exp-suffix=med2contract --reconfig=245000:2:0 " \
    "reconfig-ycsb-uniform --partitions=8 --benchmark-size=1000000 --exp-suffix=med2contract --reconfig=245000:2:0 " \
#    "stopcopy-2 --partitions=8 --benchmark-size=1000000 --exp-suffix=med3contract --reconfig=445000:3:0 " \
#    "reconfig-2 --partitions=8 --benchmark-size=1000000 --exp-suffix=med3contract --reconfig=445000:3:0 " \
#    "stopcopy-2 --partitions=8 --benchmark-size=1000000 --exp-suffix=med4contract --reconfig=445000:4:0 " \
#    "reconfig-2 --partitions=8 --benchmark-size=1000000 --exp-suffix=med4contract --reconfig=445000:4:0 " \
)

#for b in smallbank tpcc seats; do
for b in ycsb; do
# for b in seats; do
    PARAMS=( \
        --no-update \
        --results-dir=$DATA_DIR \
        --benchmark=$b \
        --stop-on-error \
        --exp-trials=1 \
        --exp-attempts=1 \        
        --no-json \
	      --sweep-reconfiguration \
        --client.interval=1000 \
        --client.output_interval=true \
        --client.duration=240000 \
        --client.warmup=10000 \
        --client.output_results_csv=interval_res.csv
        
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
