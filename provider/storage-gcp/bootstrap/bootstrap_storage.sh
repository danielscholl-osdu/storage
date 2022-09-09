#!/usr/bin/env bash
#
# Script that bootstraps storage service using Python scripts
# It creates legal tag via request to Legal service
# After that records are uploaded via requests to Storage service, using previously created Legal tag
# Contains logic for both onprem and gcp version

set -ex

source ./validate-env.sh "DATA_PARTITION_ID"
source ./validate-env.sh "MANIFESTS_DIR"
source ./validate-env.sh "STORAGE_HOST"

export ACL_OWNERS="[\"data.default.owners@${DATA_PARTITION_ID}.group\"]"
export ACL_VIEWERS="[\"data.default.viewers@${DATA_PARTITION_ID}.group\"]"
export LEGALTAGS="[\"${DATA_PARTITION_ID}-${DEFAULT_LEGAL_TAG}\"]"
export STORAGE_URL="${STORAGE_HOST}/api/storage/v2"

if [ "${ONPREM_ENABLED}" == "true" ]; then
    source ./validate-env.sh "OPENID_PROVIDER_URL"
    source ./validate-env.sh "OPENID_PROVIDER_CLIENT_ID"
    source ./validate-env.sh "OPENID_PROVIDER_CLIENT_SECRET"

    # Check that all env ANTHOS variables are provided
    export KEYCLOAK_AUTH_URL="${OPENID_PROVIDER_URL}/protocol/openid-connect/token"
    export KEYCLOAK_CLIENT_ID="${OPENID_PROVIDER_CLIENT_ID}"
    export KEYCLOAK_CLIENT_SECRET="${OPENID_PROVIDER_CLIENT_SECRET}"
    export CLOUD_PROVIDER="anthos"

    python3 /opt/bootstrap_data.py

else
    # Check that all GCP env variables are provided
    export CLOUD_PROVIDER="gcp"
    
    python3 /opt/bootstrap_data.py

fi

touch /tmp/bootstrap_ready
