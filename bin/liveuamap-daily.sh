#!/usr/bin/env bash

. bin/subr.sh

PIPELINE_CFG="./pipelines/liveuamap_daily.json"
PIPELINE_NAME=$(pipeline_name "$PIPELINE_CFG")
LABEL=$(snake_case "$PIPELINE_NAME")
REGIONS="./queries/regions.txt"
DATE=$(date +%Y-%m-%d)
RUN_ID=$(make_id)
RUN_DIR="$PWD/tmp/$RUN_ID"
LOGFILE="./logs/liveuamap-region/$DATE.log"

mkdir -p "$(dirname "$LOGFILE")"

provision_vps "$RUN_ID" "small" "$LABEL" | tee -a "$LOGFILE"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c "$PIPELINE_CFG" \
              -Q liveuamap_region:"$1" \
              --media.youtubedl_cmd "$RUN_DIR"/youtube-dl-wrapper-sudo.sh \
              -d
}

echo "Starting the daily scrape of a Liveuamap region."

while IFS="" read -r REGION
do
  doit "$REGION" 2>&1 | tee -a "$LOGFILE"
done < "$REGIONS"

destroy_vps "$RUN_ID" | tee -a "$LOGFILE"
