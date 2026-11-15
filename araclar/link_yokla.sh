#!/bin/bash
# Her link için HTTP kodu ve ilk satırları yazar.
while read -r ad url; do
  [ -z "$url" ] && continue
  kod=$(curl -s -m 15 -A "Mozilla/5.0" -o /tmp/govde -w "%{http_code} %{redirect_url}" "$url")
  echo "$ad | $kod | $(head -c 200 /tmp/govde | tr '\n' ' ')"
done
