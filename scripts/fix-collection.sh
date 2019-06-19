#!/bin/sh

DATE=$(date +%Y-%m-%d)
OUTPUT=fix-collections-"$DATE".csv
COUNTERA=0
COUNTERB=0

# Fix the data in Elasticsearch and MongoDB.
clj -A:fix-collection > "$OUTPUT"

echo "Updated collection."

tail -n+2 "$OUTPUT" |   # Skip CSV header
  awk '!v[$0]++' |      # Filter for uniq rows
  awk 'BEGIN {FS=","} $2!~/^data/{ print $2 " " $3 " " $4}' |  # Select old locations outside of data
  {
    while read -r old_location new_location verified
    do
      mkdir -p "$(pwd)/$(dirname "$new_location")"
      case "$verified" in
        true)
          cp -v "$old_location" "$(pwd)/$new_location" >> copy-log-verified-"$DATE".log
          COUNTERA=$((COUNTERA+1))
          ;;
        *)
          mv -v "$old_location" "$(pwd)/$new_location" >> copy-log-unverified-"$DATE".log
          COUNTERB=$((COUNTERB+1))
          ;;
      esac
    done
    echo "copied $COUNTERA files, moved $COUNTERB files."
  }
