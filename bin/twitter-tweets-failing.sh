#!/usr/bin/env bash

. bin/subr.sh

PROJECT_DIR=$(basename "$PWD")
PROJECT_NAME=$(snake_case "$PROJECT_DIR")
PIPELINE_CFG="./pipelines/check_failing_twitter_tweets.json"
PIPELINE_NAME=$(pipeline_name "$PIPELINE_CFG")
LABEL=$(snake_case "$PIPELINE_NAME")
DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"
REPORT_TMP_DIR="$REPORT_DIR/tmp"
RUN_ID=$(make_id)
LOGFILE="./$REPORT_DIR/twitter-tweets-$DATE.log"

# on mac use the GNU version of find
FIND="find"
if (is_mac); then FIND="gfind"; fi

mkdir -p "$REPORT_TMP_DIR"

export NODE_OPTIONS=--max_old_space_size=16384

do_prepare_videos() {
  "$(npm bin)/sugarcube" \
    -c "$PIPELINE_CFG" \
    -p mongodb_query_units,tap_writef \
    --marker "$RUN_ID" \
    --csv.data_dir "$REPORT_DIR" \
    --csv.label twitter-tweets \
    --csv.append \
    --tap.filename "$REPORT_TMP_DIR/tweets.json" \
    --tap.chunk_size 10000 \
    -d
}

do_check() {
  # $1 -> json input file
  "$(npm bin)"/sugarcube \
    -c "$PIPELINE_CFG" \
    -p fs_from_json,twitter_filter_failing \
    --marker "$RUN_ID" \
    -Q glob_pattern:"$1" \
    --csv.data_dir "$REPORT_DIR" \
    -d
}

echo "Starting a check for failing twitter tweets."

ALL_TWEETS=$(./bin/stats-sources.sh | awk 'BEGIN{FS=","; c=0; } $1~/^twitter/{ c+=$2 } END{print c}')

do_prepare_videos 2>&1 | tee -a "$LOGFILE"

COUNT_INPUT_FILES=$(ls -1 "$REPORT_TMP_DIR" | wc -l | sed 's/ //g')
COUNTER=0

# For every file with data we run a pipeline to check it's availability. We
# suspend failure checking in the morning during the time of the daily scrapes.
for f in "$REPORT_TMP_DIR"/*; do
  while :; do
   currenttime=$(date +%H:%M)
   if [[ "$currenttime" > "23:45" ]] || [[ "$currenttime" < "04:15" ]]; then
     echo "Suspending failure checks during daily scrape." | tee -a "$LOGFILE"
     sleep 900
   else
     echo "No need to suspend during the daily scrapes." | tee -a "$LOGFILE"
     break
   fi
  done

  do_check "$f" 2>&1 | tee -a "$LOGFILE"

  COUNTER=$((COUNTER+1))

  echo ""
  echo ""
  echo "Processed $COUNTER/$COUNT_INPUT_FILES input files."
  echo ""
  echo ""
done

rm -rf "$REPORT_TMP_DIR"

FAILED_STATS=$("$FIND" "$REPORT_DIR"  -name "*failed-stats-twitter-tweets*.csv" -type f -printf '%T+ %p\n' | sort -r | head -n 1 | awk '{print $2}')
if [ -n "$FAILED_STATS" ] && [ "$FAILED_STATS" != " " ]
then
  MISSING=$(xsv count "$FAILED_STATS")
else
  MISSING="0"
fi

EXISTING=$((ALL_TWEETS-MISSING))

# Send the metrics to statsd
echo "sugarcube.$PROJECT_NAME.$LABEL.twitter_filter_failing.missing:$MISSING|c" | nc -w 1 -cu localhost 8125
echo "sugarcube.$PROJECT_NAME.$LABEL.twitter_filter_failing.existing:$EXISTING|c" | nc -w 1 -cu localhost 8125
echo "sugarcube.$PROJECT_NAME.$LABEL.twitter_filter_failing.total:$ALL_TWEETS|c" | nc -w 1 -cu localhost 8125

FREQUENCIES="$REPORT_DIR/frequencies-twitter-tweets.csv"

{
  echo "reason";
  xsv select reason "$FAILED_STATS" \
    | awk 'BEGIN {getline} /^".*[^"]$/{getline x}{print $0 " " x; x=""}' \
    | sed 's/"//g' \
    | awk '{$1=$1};1' \
    | sed 's/\(.*\)/"\1"/g'
} | xsv frequency | xsv fmt -t "#" > "$FREQUENCIES"

cat "$FREQUENCIES"

REPORT="Report: Failing Twitter tweets

Verification Date: $DATE

In total $(numfmt --grouping "$MISSING") of $(numfmt --grouping "$ALL_TWEETS") twitter tweets failed.

"

echo "$REPORT" | tee "$REPORT_DIR/report-twitter-tweets-$DATE.txt"
