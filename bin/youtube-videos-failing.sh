#!/usr/bin/env bash

DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"

mkdir -p "$REPORT_DIR"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_youtube_videos.json \
              -q queries/mail-recipients.json \
              --csv.data_dir "$REPORT_DIR" \
              -d
}

echo "Starting a check for failing youtube videos."

doit 2>&1 | tee -a "./$REPORT_DIR/youtube-videos-$DATE.log"
