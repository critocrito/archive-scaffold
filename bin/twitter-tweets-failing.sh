#!/usr/bin/env bash

DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"

mkdir -p "$REPORT_DIR"

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_twitter_tweets.json \
              -q queries/mail-recipients.json \
              -d
}

echo "Starting a check for failing twitter tweets."

doit 2>&1 | tee -a "./$REPORT_DIR/twitter-tweets-$DATE.log"
