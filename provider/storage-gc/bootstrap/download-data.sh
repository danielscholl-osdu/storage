#! /bin/sh
set -x

if [ -z "${MANIFESTS_DIR}" ]; then 
    echo "Set 'MANIFESTS_DIR' variable"
    exit 1
else 
   mkdir -p "${MANIFESTS_DIR}" 
fi

if [ -z "${DATA_BRANCH}" ]; then 
    echo "Set 'DATA_BRANCH' variable to master"
    DATA_BRANCH="master"
fi

REFERENCE_VALUES="https://community.opengroup.org/osdu/data/data-definitions/-/archive/${DATA_BRANCH}/data-definitions-${DATA_BRANCH}.zip?path=ReferenceValues/Manifests/reference-data"
TNO_VOLVE_VALUES="https://community.opengroup.org/osdu/platform/data-flow/data-loading/open-test-data/-/archive/${DATA_BRANCH}/open-test-data-${DATA_BRANCH}.zip?path=rc--3.0.0/4-instances"

curl -o reference-data.zip "$REFERENCE_VALUES"
if unzip -t "reference-data.zip" > /dev/null; then
    echo "Succesfully downloaded and verified reference-data.zip"
else
    echo "reference-data.zip has not been downloaded."
    exit 1
fi

curl -o tno-volve-data.zip "$TNO_VOLVE_VALUES"
if unzip -t "tno-volve-data.zip" > /dev/null; then
    echo "Succesfully downloaded and verified tno-volve-data.zip"
else
    echo "tno-volve-data.zip has not been downloaded."
    exit 1
fi

unzip -o reference-data.zip -d  "$MANIFESTS_DIR" > /dev/null 
unzip -o tno-volve-data.zip -d "$MANIFESTS_DIR" > /dev/null 

rm -f reference-data.zip  
rm -f tno-volve-data.zip 

find "$MANIFESTS_DIR" -type f -name 'IngestionSequence.json' -delete
find "$MANIFESTS_DIR" -type f -name 'ReferenceValueTypeDependencies.json' -delete
find "$MANIFESTS_DIR" -type d -name 'work-products' -exec rm -rf {} +
