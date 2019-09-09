#!/bin/sh
NS="$1"
shift
U="$1"
shift
ip netns exec "$NS" sudo -u "$U" youtube-dl "$@"
