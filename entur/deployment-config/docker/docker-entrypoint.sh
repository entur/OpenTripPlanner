#!/bin/bash

: ${GRAPH_FILE_TARGET_PATH="/code/otpdata/norway/graph.obj"}
: ${FILE_TMP_PATH="/tmp/graph_obj_from_gcs"}
# Notice ending slash here, it is correct
: ${MARDUK_GCP_BASE="gs://marduk/"}
: ${GRAPH_POINTER_FILE="current-otp2"}

echo "GRAPH_FILE_TARGET_PATH: $GRAPH_FILE_TARGET_PATH"
serializationVersionId=$(java -jar otp-shaded.jar --serializationVersionId)
echo serializationVersionId: $serializationVersionId
gcloud storage cat ${MARDUK_GCP_BASE}netex-otp2/${serializationVersionId}/${GRAPH_POINTER_FILE} 2> temp 1>temp
   if grep -q 'ERROR' temp; then
      echo "RC Graph with serialId not found " $serializationVersionId
      echo "Use main graph file"
      FILENAME=$(gcloud storage cat ${MARDUK_GCP_BASE}${GRAPH_POINTER_FILE})
   else
      echo "Found RC graph use this one"
      FILENAME=$(gcloud storage cat ${MARDUK_GCP_BASE}netex-otp2/${serializationVersionId}/${GRAPH_POINTER_FILE})

   fi
   echo "FILENAME: " $FILENAME
rm temp

DOWNLOAD="${MARDUK_GCP_BASE}${FILENAME}"
echo "Downloading $DOWNLOAD"
gcloud storage --no-user-output-enabled cp $DOWNLOAD $FILE_TMP_PATH

# Testing exists and has a size greater than zero
if [ -s $FILE_TMP_PATH ] ;
then
  echo "Overwriting $GRAPH_FILE_TARGET_PATH"
  mv $FILE_TMP_PATH $GRAPH_FILE_TARGET_PATH
else
  echo "** WARNING: Downloaded file ($FILE_TMP_PATH) is empty or not present**"
  echo "** Not overwriting $GRAPH_FILE_TARGET_PATH**"
  wget -q --header 'Content-Type: application/json' --post-data='{"source":"otp", "message":":no_entry: Downloaded file is empty or not present. This makes OTP fail! Please check logs"}' http://hubot/hubot/say/
  echo "Now sleeping 5m in the hope that this will be manually resolved in the mean time, and then restarting."
  sleep 5m
  exit 1
fi

exec "$@"
