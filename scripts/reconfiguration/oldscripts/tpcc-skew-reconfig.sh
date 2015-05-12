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
#    "reconfig-tpcc-hotspot-0 --partitions=4" \
#    "reconfig-tpcc-hotspot-20 --partitions=4" \
#    "reconfig-tpcc-hotspot-50 --partitions=4" \
    "reconfig-tpcc-hotspot-80 --partitions=8 --benchmark-size=16 --exp-suffix=tpcskew --reconfig=185000:2:0" \
#    "reconfig-tpcc-hotspot-100 --partitions=4" \
)

#for b in smallbank tpcc seats; do
for b in tpcc; do
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
        --client.duration=210000 \
        --client.warmup=30000 \
        --client.output_results_csv=interval_res.csv
        --global.hasher_plan=scripts/reconfiguration/plans/tpcc-size16-8-skew.json\
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
