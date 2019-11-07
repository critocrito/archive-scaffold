#!/usr/bin/env bash

DATE=$(date +%Y-%m-%d)
export NODE_OPTIONS=--max_old_space_size=16384
LOGFILE="./logs/mosaic-videos/$DATE.log"

mkdir -p "$(dirname "$LOGFILE")"

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/mosaic-videos.json \
              -d
}

echo "Generating missing mosaic images."

doit 2>&1 | tee -a "$LOGFILE"
