# THIS SCRIPT MIGHT CHANGE CONSIDERABLY LATER

#!/bin/bash

# Exit as soon as a command fails
set -e

echo "  Environment = $ENVIRONMENT"
echo "  Project = $GCLOUD_PROJECT"
echo "  Source branch = $BUILD_SOURCEBRANCHNAME"
echo "  Build definition = $BUILD_DEFINITIONNAME"
echo "  Build number = $BUILD_BUILDNUMBER"

echo "current working directory"
pwd

echo "working directory contents"
ls

DEPLOY_DIR="deploy_dir"
mkdir -p $DEPLOY_DIR

cp app.yaml $DEPLOY_DIR
cp storage-gcp-*.jar $DEPLOY_DIR

# Go to deploy directory
cd $DEPLOY_DIR

# apply sed to replace ENVIRONMENT with its value in app.yaml
sed -i -e "s|ENVIRONMENT|$ENVIRONMENT|g" app.yaml
cat app.yaml

# check the current deployed version of the same service
SERVICE_NAME="os-storage"
count=$(gcloud app services list --project $GCLOUD_PROJECT | grep $SERVICE_NAME | wc -l)
if [ $count -gt 0 ]; then
  CURRENT_VERSION=$(gcloud app services describe $SERVICE_NAME --project $GCLOUD_PROJECT --format=json | jq --raw-output '.split.allocations | keys[0]')
else
  CURRENT_VERSION=""
fi

NEW_VERSION=$BUILD_BUILDNUMBER #to keep it unique
NEW_VERSION=$(echo "$NEW_VERSION" | tr _ - | tr . - | tr '[:upper:]' '[:lower:]')

echo "Current version = $CURRENT_VERSION"
echo "Version to be deployed = $NEW_VERSION"

if [ "$NEW_VERSION" != "$CURRENT_VERSION" -o "$BUILD_FORCE_DEPLOY" = "true" ]
then
    gcloud app deploy --quiet --version=$NEW_VERSION --project=$GCLOUD_PROJECT app.yaml
else
    echo "Not deploying the application because $NEW_VERSION is already deployed and force deploy flag is not set to true"
fi

