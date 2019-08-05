#!/bin/sh

op="$1"
file="$2"

if [ -z "$op" ] || [ -z "$file" ];
then
  echo "Usage: ./scripts/fix-website-data.sh [missing-location] <observations.json>"
  exit 1
fi

clj -A:verify-website-data "$op" "$file"
