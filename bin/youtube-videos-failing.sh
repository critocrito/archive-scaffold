#!/usr/bin/env bash

DATE=$(date +%Y-%m-%d)

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_youtube_videos.json \
              -q queries/mail-recipients.json \ \
              -d
}

echo "Starting a check for failing youtube videos."

doit | tee -a ./logs/failing-youtube-videos-"$DATE".log
