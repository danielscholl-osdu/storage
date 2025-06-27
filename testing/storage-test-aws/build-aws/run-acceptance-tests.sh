# Copyright © Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

CUR_DIR=$(pwd)
SCRIPT_SOURCE_DIR=$(dirname "$0")
echo "Script source location"
echo "$SCRIPT_SOURCE_DIR"
cd $SCRIPT_SOURCE_DIR

# Required variables
export ENTITLEMENTS_DOMAIN=example.com
export TENANT_NAME=int-test-storage

export AWS_COGNITO_AUTH_FLOW="USER_PASSWORD_AUTH"
export PRIVILEGED_USER_TOKEN=$(aws cognito-idp initiate-auth --region ${AWS_REGION} --auth-flow ${AWS_COGNITO_AUTH_FLOW} --client-id ${AWS_COGNITO_CLIENT_ID} --auth-parameters "{\"USERNAME\":\"${ADMIN_USER}\",\"PASSWORD\":\"${ADMIN_PASSWORD}\"}" --query AuthenticationResult.AccessToken --output text)
export NO_ACCESS_USER_TOKEN=$(aws cognito-idp initiate-auth --region ${AWS_REGION} --auth-flow ${AWS_COGNITO_AUTH_FLOW} --client-id ${AWS_COGNITO_CLIENT_ID} --auth-parameters "{\"USERNAME\":\"${USER_NO_ACCESS}\",\"PASSWORD\":\"${USER_NO_ACCESS_PASSWORD}\"}" --query AuthenticationResult.AccessToken --output text)
export ROOT_USER_TOKEN=$PRIVILEGED_USER_TOKEN

export TEST_REPLAY_ENABLED=false
export COLLABORATION_ENABLED=false
export OPA_INTEGRATION_ENABLED=true

mvn clean test
TEST_EXIT_CODE=$?

cd $CUR_DIR

if [ -n "$1" ]
  then
    mkdir -p "$1"
    mkdir -p $1/os-storage
    cp -R $SCRIPT_SOURCE_DIR/target/surefire-reports/* $1/os-storage
fi

exit $TEST_EXIT_CODE
