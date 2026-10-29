#!/bin/bash
# Verilen linkleri 40 sn boyunca gerçekten oynatıp (görüntü çözmeden) kaç saniyelik yayın alındığını ölçer.
while read -r ad url; do
  [ -z "$url" ] && continue
  s=$( { /usr/bin/time -f "%e" timeout 60 ffmpeg -hide_banner -loglevel error -user_agent "Mozilla/5.0" -i "$url" -t 40 -c copy -f null - ; } 2>&1 | tail -3 | tr '\n' ' ')
  echo "$ad | $url | $s"
done
