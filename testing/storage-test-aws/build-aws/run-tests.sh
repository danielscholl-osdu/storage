# Copyright © 2020 Amazon Web Services
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script prepares the dist directory for the integration tests.
# Must be run from the root of the repostiory

# This script executes the test and copies reports to the provided output directory
# To call this script from the service working directory
# ./dist/testing/integration/build-aws/run-tests.sh "./reports/"


SCRIPT_SOURCE_DIR=$(dirname "$0")
echo "Script source location"
echo "$SCRIPT_SOURCE_DIR"
(cd "$SCRIPT_SOURCE_DIR"/../bin && ./install-deps.sh)

#### ADD REQUIRED ENVIRONMENT VARIABLES HERE ###############################################
# The following variables are automatically populated from the environment during integration testing
# see os-deploy-aws/build-aws/integration-test-env-variables.py for an updated list

# AWS_COGNITO_CLIENT_ID
# ELASTIC_HOST
# ELASTIC_PORT
# FILE_URL
# LEGAL_URL
# SEARCH_URL
# STORAGE_URL
export OTHER_RELEVANT_DATA_COUNTRIES=US
export DEPLOY_ENV=empty
export LEGAL_TAG=opendes-public-usa-dataset-1
export TENANT_NAME=int-test-storage
export AWS_COGNITO_AUTH_FLOW=USER_PASSWORD_AUTH
export AWS_COGNITO_AUTH_PARAMS_PASSWORD=$ADMIN_PASSWORD
export AWS_COGNITO_AUTH_PARAMS_USER=$ADMIN_USER
export AWS_COGNITO_AUTH_PARAMS_USER_NO_ACCESS=$USER_NO_ACCESS
export STORAGE_URL=$STORAGE_URL
export DOMAIN=example.com
export LEGAL_URL=$LEGAL_URL

#### RUN INTEGRATION TEST #########################################################################
JAVA_HOME=$JAVA17_HOME

mvn -ntp test -f "$SCRIPT_SOURCE_DIR"/../pom.xml
#mvn -Dmaven.surefire.debug test -f "$SCRIPT_SOURCE_DIR"/../pom.xml
TEST_EXIT_CODE=$?

#### COPY TEST REPORTS #########################################################################

if [ -n "$1" ]
  then
    mkdir -p "$1"
    cp -R "$SCRIPT_SOURCE_DIR"/../target/surefire-reports "$1"
fi

exit $TEST_EXIT_CODE

