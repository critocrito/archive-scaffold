#!/usr/bin/env bash

DATE=$(date +%Y-%m-%d)
export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/mosaic-videos.json \
              -q queries/mail-recipients.json
}

echo "Generating missing mosaic images."

doit 2>&1 | tee -a ./logs/mosaic-videos-"$DATE".log
