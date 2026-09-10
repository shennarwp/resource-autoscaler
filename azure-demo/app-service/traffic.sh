#!/bin/bash

if [ -z "$TOKEN" ]; then
  echo "Set TOKEN first: export TOKEN=your_bearer_token"
  exit 1
fi

URL="https://autoscaler-busy.azurewebsites.net"
REQUESTS=${1:-1000}

echo "Sending $REQUESTS requests to $URL..."
for i in $(seq 1 $REQUESTS); do
  curl -s -H "Authorization: Bearer $TOKEN" "$URL" > /dev/null
done

echo "Done."
