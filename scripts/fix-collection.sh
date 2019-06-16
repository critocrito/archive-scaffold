#!/bin/sh

DATE=$(date +%Y-%m-%d)
OUTPUT=fix-collections-"$DATE".csv

# Fix the data in Elasticsearch and MongoDB.
clj -A:fix-collection > "$OUTPUT"

tail -n+2 "$OUTPUT" |   # Skip CSV header
  awk '!v[$0]++' |      # Filter for uniq rows
  awk 'BEGIN {FS=","} $2!~/^data/{ print $2 " " $3}' |  # Select old locations outside of data
  while read -r old_location new_location
  do
    mkdir -p "$(pwd)/$(dirname "$new_location")"
    cp -v "$old_location" "$(pwd)/$new_location" > copy-log-"$DATE".log
  done
