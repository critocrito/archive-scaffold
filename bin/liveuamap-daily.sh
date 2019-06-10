#!/usr/bin/env bash

REGIONS="./queries/regions.txt"
DATE=$(date +%Y-%m-%d)

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/liveuamap_daily.json \
              -q queries/mail-recipients.json \
              -Q liveuamap_region:"$1" \
              -d
}

echo "Starting the daily scrape of a Liveuamap region."

while IFS="" read -r REGION
do
  doit "$REGION" 2>&1 | tee -a ./logs/liveuamap-region-"$REGION"-"$DATE".log
done < "$REGIONS"
