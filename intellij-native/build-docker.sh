#!/bin/bash

gcloud builds submit \
  --project android-studio-build \
  --region us-west2 \
  --tag us-west2-docker.pkg.dev/android-studio-build/rbe-docker/intellij_native