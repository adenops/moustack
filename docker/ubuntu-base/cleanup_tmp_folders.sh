#!/bin/bash
set -e

# Note: starting with Docker 1.10 there will be a --tmpfs option that should be
# a better solution than manually removing everything.

echo "cleaning up /run and /tmp"
find /run/ ! -path '*/netns/*' -type f -delete 2>/dev/null || true
rm -rf /tmp/* 2>/dev/null || true
