#!/bin/bash

set -euo pipefail

: ${MARDUK_GCP_BASE="gs://marduk/"}
: ${GRAPH_POINTER_FILE="current-otp2"}
: ${GRAPH_POINTER_PREFIX="netex-otp2"}

# Extract bucket name from gs:// URI
# e.g. "gs://ror-otp-graphs-gcp2-production/" -> "ror-otp-graphs-gcp2-production"
BUCKET="${MARDUK_GCP_BASE#gs://}"
BUCKET="${BUCKET%/}"
echo "Bucket: $BUCKET"

# Get OTP serialization version ID
SER_ID=$(java -jar otp-shaded.jar --serializationVersionId)
echo "Serialization version ID: $SER_ID"

# Get GKE Workload Identity OAuth2 token from metadata server
TOKEN=$(curl -sf -H "Metadata-Flavor: Google" \
  "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to get OAuth2 token from metadata server"
  exit 1
fi

# Read a GCS object's content via the JSON API
gcs_read() {
  local object="$1"
  local encoded
  encoded=$(printf '%s' "$object" | sed 's|/|%2F|g')
  curl -sf -H "Authorization: Bearer $TOKEN" \
    "https://storage.googleapis.com/storage/v1/b/${BUCKET}/o/${encoded}?alt=media"
}

# Read graph pointer file for this serialization version.
# When GRAPH_POINTER_PREFIX is set, the pointer is looked up under
# <prefix>/<serialization-id>/. When empty, the pointer is at the bucket root.
# If not found, the graph may still be building — sleep 5m to avoid
# CrashLoopBackOff and give the graph builder time to finish.
if [ -n "$GRAPH_POINTER_PREFIX" ]; then
  POINTER="${GRAPH_POINTER_PREFIX}/${SER_ID}/${GRAPH_POINTER_FILE}"
else
  POINTER="${GRAPH_POINTER_FILE}"
fi
if FILENAME=$(gcs_read "$POINTER"); then
  echo "Found graph pointer: $FILENAME"
else
  echo "** WARNING: No graph pointer file found for serialization ID $SER_ID **"
  echo "** The graph may still be building. Sleeping 5m before retrying. **"
  sleep 5m
  exit 1
fi

# Export GRAPH_URI so OTP substitutes ${GRAPH_URI} in build-config.json
export GRAPH_URI="gs://${BUCKET}/${FILENAME}"
echo "Graph URI: $GRAPH_URI"

exec "$@"
