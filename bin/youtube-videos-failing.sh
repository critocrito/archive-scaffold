#!/usr/bin/env bash

. bin/subr.sh

PIPELINE_CFG="./pipelines/check_failing_youtube_videos.json"
PIPELINE_NAME=$(pipeline_name "$PIPELINE_CFG")
LABEL=$(snake_case "$PIPELINE_NAME")
DATE=$(date +%Y-%m-%d)
MONTH=$(date +%B)
YEAR=$(date +%Y)
REPORT_DIR="reports/$YEAR/$MONTH"
REPORT_TMP_DIR="$REPORT_DIR/tmp"
RUN_ID=$(make_id)
RUN_DIR="$PWD/tmp/$RUN_ID"
LOGFILE="./$REPORT_DIR/youtube-videos-$DATE.log"

# on mac use the GNU version of find
FIND="find"
if (is_mac); then FIND="gfind"; fi

mkdir -p "$REPORT_DIR/tmp"

provision_vps "$RUN_ID" "small" "$LABEL" | tee -a "$LOGFILE"

export NODE_OPTIONS=--max_old_space_size=16384

percent() {
  echo "scale=2; $2*100/$1" | bc -l | sed -e 's/^\.\(.*\)/0\.\1/g'
}

do_prepare_videos() {
  "$(npm bin)/sugarcube" \
    -c "$PIPELINE_CFG" \
    -p elastic_import,tap_writef \
    --marker "$RUN_ID" \
    -Q glob_pattern:es-queries/all-youtube-videos.json \
    --csv.data_dir "$REPORT_DIR" \
    --csv.label youtube-videos \
    --csv.append \
    --tap.filename "$REPORT_TMP_DIR/videos.json" \
    --tap.chunk_size 10000 \
    -d
}

do_check() {
  # $1 -> json input file
  "$(npm bin)"/sugarcube \
    -c "$PIPELINE_CFG" \
    -p fs_from_json,youtube_filter_failing \
    --marker "$RUN_ID" \
    -Q glob_pattern:"$1" \
    --csv.data_dir "$REPORT_DIR" \
    --media.youtubedl_cmd "$RUN_DIR"/youtube-dl-wrapper-sudo.sh \
    -d
}

echo "Starting a check for failing youtube videos."

ALL_YT_VIDEOS=$(./bin/stats-sources.sh | awk 'BEGIN{FS=","; c=0; } $1~/^youtube/{ c+=$2 } END{print c}')

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

destroy_vps "$RUN_ID" | tee -a "$LOGFILE"

rm -rf "$REPORT_TMP_DIR"

FAILED_STATS=$("$FIND" "$REPORT_DIR"  -name "*failed-stats-youtube-video*.csv" -type f -printf '%T+ %p\n' | sort -r | head -n 1 | awk '{print $2}')
FREQUENCIES="$REPORT_DIR/frequencies-youtube-videos.csv"

{
  echo "reason";
  xsv select reason "$FAILED_STATS" \
    | awk 'BEGIN {getline} /^".*[^"]$/{getline x}{print $0 " " x; x=""}' \
    | sed 's/"//g' \
    | awk '{$1=$1};1' \
    | sed 's/\(.*\)/"\1"/g'
} | xsv frequency | xsv fmt -t "#" > "$FREQUENCIES"

TOTAL=$(awk 'BEGIN{FS="#"} {c+=$3} END {print c}' "$FREQUENCIES")

USER_REGEX="removed.*user\|uploader.*closed"
COPYRIGHT_REGEX="copyright"
UNAVAILABLE_REGEX="This video is unavailable\."
TERMINATED_REGEX="video has been terminated"
TOS_REGEX="terms of service"

USER=$(grep -i "$USER_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
COPYRIGHT=$(grep -i "$COPYRIGHT_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
UNAVAILABLE=$(grep -i "$UNAVAILABLE_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
TERMINATED=$(grep -i "$TERMINATED_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')
TOS=$(grep -i "$TOS_REGEX" "$FREQUENCIES" | awk 'BEGIN{FS="#"; c=0; } {c+=$3} END {print c}')

REST=$(grep -iv "$USER_REGEX" "$FREQUENCIES" \
         | grep -v "$COPYRIGHT_REGEX" \
         | grep -iv "$UNAVAILABLE_REGEX" \
         | grep -iv "$TERMINATED_REGEX" \
         | grep -iv "$TOS_REGEX" \
         | tail -n+2 \
         | awk 'BEGIN{FS="#"} {print $2 ": " $3}')

USER_PERCENT=$(percent "$TOTAL" "$USER")
COPYRIGHT_PERCENT=$(percent "$TOTAL" "$COPYRIGHT")
UNAVAILABLE_PERCENT=$(percent "$TOTAL" "$UNAVAILABLE")
TERMINATED_PERCENT=$(percent "$TOTAL" "$TERMINATED")
TOS_PERCENT=$(percent "$TOTAL" "$TOS")

rm "$FREQUENCIES"

REPORT="Report: Failing Youtube videos

Verification date: $DATE

In total $(numfmt --grouping "$TOTAL") of $(numfmt --grouping "$ALL_YT_VIDEOS") youtube videos failed to download

Videos deleted after Youtube terminated the account: $(numfmt --grouping "$TERMINATED") ($TERMINATED_PERCENT%)
Videos deleted on ground of Terms of Service violations: $(numfmt --grouping "$TOS") ($TOS_PERCENT%)
Videos deleted on grounds of copyright violations: $(numfmt --grouping "$COPYRIGHT") ($COPYRIGHT_PERCENT%)
Videos failing with no further reasons: $(numfmt --grouping "$UNAVAILABLE") ($UNAVAILABLE_PERCENT%)
Videos removed by the user: $(numfmt --grouping "$USER") ($USER_PERCENT%)

The other counts look like this:

$REST

"

echo "$REPORT" | tee "$REPORT_DIR/report-youtube-videos.txt"
