#!/usr/bin/env bash

. bin/subr.sh

REGIONS="./queries/regions.txt"
DATE=$(date +%Y-%m-%d)
RUN_ID=$(make_id)
RUN_DIR="$PWD/tmp/$RUN_ID"

provision_vps "$RUN_ID" "small"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/liveuamap_daily.json \
              -q queries/mail-recipients.json \
              -Q liveuamap_region:"$1" \
              --media.youtubedl_cmd "$RUN_DIR"/youtube-dl-wrapper-sudo.sh \
              -d
}

echo "Starting the daily scrape of a Liveuamap region."

while IFS="" read -r REGION
do
  doit "$REGION" 2>&1 | tee -a ./logs/liveuamap-region-"$REGION"-"$DATE".log
done < "$REGIONS"

destroy_vps "$RUN_ID"
