#!/usr/bin/env bash

DATE=$(date +%Y-%m-%d)

export NODE_OPTIONS=--max_old_space_size=16384

doit() {
  "$(npm bin)"/sugarcube \
              -c pipelines/check_failing_twitter_tweets.json \
              -q queries/mail-recipients.json \
              -d
}

echo "Starting a check for failing twitter tweets."

doit 2>&1 | tee -a ./logs/failing-twitter-tweets-"$DATE".log
