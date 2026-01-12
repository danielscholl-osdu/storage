# Storage Service
storage-aws is a [Spring Boot](https://spring.io/projects/spring-boot) service that provides a set of APIs to manage metadata record storage and retrieval. 


## Running Locally

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites
Pre-requisites

* JDK 17 (https://docs.aws.amazon.com/corretto/latest/corretto-17-ug/downloads-list.html)
* Maven 3.8.3 or later
* Lombok 1.28 or later
* OSDU Instance deployed on AWS

### Service Configuration
In order to run the service locally or remotely, you will need to have the following environment variables defined.

| name                      | example value                                        | required | description                                                                                   | sensitive? |
|---------------------------|------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------|------------|
| `LOCAL_MODE`              | `true`                                               | yes      | Set to 'true' to use env vars in place of the k8s variable resolver                           | no         |
| `APPLICATION_PORT`        | `8080`                                               | yes      | The port the service will be hosted on.                                                       | no         |
| `AWS_REGION`              | `us-east-1`                                          | yes      | The region where resources needed by the service are deployed                                 | no         |
| `AWS_ACCESS_KEY_ID`       | `ASIAXXXXXXXXXXXXXX`                                 | yes      | The AWS Access Key for a user with access to Backend Resources required by the service        | yes        |
| `AWS_SECRET_ACCESS_KEY`   | `abcde`                                              | yes      | The AWS Secret Key for a user with access to Backend Resources required by the service        | yes        |
| `AWS_SESSION_TOKEN`       | `session-token-xxxxxxxxxx`                           | no       | AWS Session token needed if using an SSO user session to authenticate                         | yes        |
| `ENVIRONMENT`             | `osdu-prefix`                                        | yes      | The Resource Prefix defined during deployment                                                 | no         |
| `LOG_LEVEL`               | `DEBUG`                                              | yes      | The Log Level severity to use (https://www.tutorialspoint.com/log4j/log4j_logging_levels.htm) | no         |
| `SSM_ENABLED`             | `true`                                               | yes      | Set to 'true' to use SSM to resolve config properties, otherwise use env vars                 | no         |
| `SSL_ENABLED`             | `false`                                              | no       | Set to 'false' to disable SSL for local development                                           | no         |
| `ENTITLEMENTS_BASE_URL`   | `http://localhost:8081` or `https://some-hosted-url` | yes      | Specify the base url for an entitlements service instance. Can be run locally or remote       | no         |
| `PARTITION_BASE_URL`      | `http://localhost:8082` or `https://some-hosted-url` | yes      | Specify the base url for a partition service instance. Can be run locally or remote           | no         | 
| `LEGALTAG_BASE_URL`       | `http://localhost:8083` or `https://some-hosted-url` | yes      | Specify the base url for a legal service instance. Can be run locally or remote               | no         | 
| `CRS_CONVERSION_BASE_URL` | `http://localhost:8084` or `https://some-hosted-url` | yes      | Specify the base url for a CRS Conversion service instance. Can be run locally or remote      | no         | 
| `POLICY_BASE_URL`         | `http://localhost:8085` or `https://some-hosted-url` | yes      | Specify the base url for a Policy service instance. Can be run locally or remote              | no         | 
| `SCHEMA_BASE_URL`         | `http://localhost:8086` or `https://some-hosted-url` | yes      | Specify the base url for a Schema service instance. Can be run locally or remote              | no         | 
| `OPA_URL`                 | `http://opa-agent`                                   | yes      | Specify the url for the OPA agent.                                                            | no         | 
| `DISABLE_CACHE`           | `true`                                               | no       | Set to true to disable caching to redis. Either set this or configure cache config env vars   | no         | 
| `CACHE_CLUSTER_ENDPOINT`  | `true`                                               | no       | Redis endpoint uri. Either set this or DISABLE_CACHE                                          | no         | 
| `CACHE_CLUSTER_PORT`      | `6379`                                               | no       | Redis port. Either set this or DISABLE_CACHE                                                  | no         | 
| `CACHE_CLUSTER_KEY`       | `xxxxxx`                                             | no       | Redis auth key. Either set this or DISABLE_CACHE                                              | no         | 
| `storage-sns-topic-arn`   | `sns-topic-name`                                     | yes      | The SNS topic that storage events are published to                                            | no         |
| `REPLAY_TOPIC`            | `replay-records`                                     | no       | The name of the SNS topic for replay operations (required for replay feature)                 | no         |
| `replay-records-sns-topic-arn` | `arn:aws:sns:region:account:replay-records`     | no       | The ARN of the SNS topic for replay operations (required for replay feature)                  | no         |
| `replay-records-sqs-queue-url` | `https://sqs.region.amazonaws.com/account/replay-records` | no | The URL of the SQS queue for replay operations (required for replay feature)            | no         |

### Run Locally
Check that maven is installed:

example:
```bash
$ mvn --version
Apache Maven 3.8.3 (ff8e977a158738155dc465c6a97ffaf31982d739)
Maven home: /usr/local/Cellar/maven/3.8.3/libexec
Java version: 17.0.7
...
```

You may need to configure access to the remote maven repository that holds the OSDU dependencies. Copy one of the below files' content to your .m2 folder
* For development against the OSDU GitLab environment, leverage: `<REPO_ROOT>~/.mvn/community-maven.settings.xml`
* For development in an AWS Environment, leverage: `<REPO_ROOT>/provider/storage-aws/maven/settings.xml`

* Navigate to the service's root folder and run:

```bash
mvn clean package -pl storage-core,provider/storage-aws
```

* If you wish to build the project without running tests

```bash
mvn clean package -pl storage-core,provider/storage-aws -DskipTests
```

After configuring your environment as specified above, you can follow these steps to run the application. These steps should be invoked from the *repository root.*
<br/>
<br/>
NOTE: If not on osx/linux: Replace `*` with version numbers as defined in the provider/storage-aws/pom.xml file

```bash
java -jar provider/storage-aws/target/storage-aws-*.*.*-SNAPSHOT-spring-boot.jar
```

## Replay Feature

The AWS implementation of the Replay API provides a scalable and resilient solution for replaying records. This feature is essential for reindexing records and ensuring data consistency across the system. The replay feature currently does not support collaboration headers.

### Architecture

The AWS Replay implementation uses a consolidated messaging approach:

```
User Request → Storage API → Single SNS Topic → Single SQS Queue → Storage Message Processor -> Storage Indexing Queue
                    ↕
               DynamoDB (status tracking)
```

### Key Features


1. **Schema Service Integration**: When performing a "replay all" operation with no specific kinds provided, the implementation queries the Schema Service to get a complete list of all registered kinds in the system. Scanning the existing records for unique kinds is not scalable with DynamoDB.

2. **Asynchronous Processing**: The API returns immediately with a replay ID, while processing continues in the background. This ensures the API remains responsive even for large replay operations and avoids timeouts.

3. **Batch Processing**: Records are processed in configurable batches to optimize performance and resource utilization:
   - Kinds are processed in batches (default: 50 kinds per batch)
   - Records are retrieved in pages (default: 1000 records per page)
   - Messages are published in smaller batches (default: 50 records per batch)

4. **Parallel Processing**: Multiple kinds can be processed in parallel using a configurable thread pool (default: 4 threads).

5. **Status Tracking**: Detailed status information is stored in DynamoDB with per-kind granularity, allowing users to monitor the progress of replay operations.

6. **Error Handling**: Robust error handling with exponential backoff retries and dead letter queue support ensures reliability.

7. **Resume-on-Failure**: DynamoDB tracks the current cursor for each batch retrieval, allowing any pod to resume processing from the last saved position if the original pod fails.


## Testing
 
 ### Running Integration Tests 
 This section describes how to run OSDU Integration tests (testing/storage-test-aws).
 
 You will need to have the following environment variables defined.
 
 | name                                     | example value                           | description                                                                            | sensitive? |
 |------------------------------------------|-----------------------------------------|----------------------------------------------------------------------------------------|------------|
 | `AWS_ACCESS_KEY_ID`                      | `ASIAXXXXXXXXXXXXXX`                    | The AWS Access Key for a user with access to Backend Resources required by the service | yes        |
 | `AWS_SECRET_ACCESS_KEY`                  | `abcde`                                 | The AWS Secret Key for a user with access to Backend Resources required by the service | yes        |
 | `AWS_SESSION_TOKEN`                      | `session-token-xxxxxxxxx`               | AWS Session token needed if using an SSO user session to authenticate                  | yes        |
 | `AWS_COGNITO_USER_POOL_ID`               | `us-east-1_xxxxxxxx`                    | User Pool Id for the reference cognito                                                 | no         |
 | `AWS_COGNITO_CLIENT_ID`                  | `xxxxxxxxxxxx`                          | Client ID for the Auth Flow integrated with the Cognito User Pool                      | no         |
 | `AWS_COGNITO_AUTH_FLOW`                  | `USER_PASSWORD_AUTH`                    | Auth flow used by reference cognito deployment                                         | no         |
 | `AWS_COGNITO_AUTH_PARAMS_USER`           | `int-test-user@testing.com`             | Int Test Username                                                                      | no         |
 | `AWS_COGNITO_AUTH_PARAMS_USER_NO_ACCESS` | `no-access-user@testing.com`            | Int Test No Access Username                                                            | no         |
 | `AWS_COGNITO_AUTH_PARAMS_PASSWORD`       | `some-secure-password`                  | Int Test User/NoAccessUser Password                                                    | yes        |
 | `STORAGE_URL`                            | `http://localhost:8080/api/storage/v2/` | The url where the Storage API is hosted                                                | no         |
 | `LEGAL_URL`                              | `http://localhost:8080/api/legal/v1/`   | The url where the Legal API is hosted                                                  | no         |
 | `SEARCH_URL`                             | `http://localhost:8080/api/search/v2/`  | The url where the Search API is hosted                                                 | no         |
 | `INDEXER_URL`                            | `http://localhost:8080/api/indexer/v2/` | The url where the Indexer API is hosted                                                | no         |
 | `LEGAL_TAG`                              | `opendes-public-usa-dataset-1`          | Legal tag name to use for integration tests                                            | no         |
 | `OTHER_RELEVANT_DATA_COUNTRIES`          | `US`                                    | Relevant Data Countries applied to Legal Tag                                           | no         |
 | `DOMAIN`                                 | `example.com`                           | Domain used by ACL                                                                     | no         |
 | `DEPLOY_ENV`                             | `empty`                                 | Unused env var, set to 'empty'.                                                        | no         |
 | `TENANT_NAME`                            | `int-test-storage`                      | Data Partition Id used by int tests                                                    | no         |
 

 **Creating a new user to use for integration tests**
 ```
 aws cognito-idp admin-create-user --user-pool-id ${AWS_COGNITO_USER_POOL_ID} --username ${AWS_COGNITO_AUTH_PARAMS_USER} --user-attributes Name=email,Value=${AWS_COGNITO_AUTH_PARAMS_USER} Name=email_verified,Value=True --message-action SUPPRESS

 aws cognito-idp initiate-auth --auth-flow ${AWS_COGNITO_AUTH_FLOW} --client-id ${AWS_COGNITO_CLIENT_ID} --auth-parameters USERNAME=${AWS_COGNITO_AUTH_PARAMS_USER},PASSWORD=${AWS_COGNITO_AUTH_PARAMS_PASSWORD}
 ```
 
 **Entitlements group configuration for integration accounts**
 <br/>
 In order to add user entitlements, run entitlements bootstrap scripts in the entitlements project
 
 | AWS_COGNITO_AUTH_PARAMS_USER | AWS_COGNITO_AUTH_PARAMS_USER_NO_ACCESS | 
 |------------------------------|----------------------------------------|
 | service.entitlements.user    | service.entitlements.user              |
 | service.storage.admin        | service.storage.admin                  |
 | service.storage.editor       |                                        |
 | service.storage.viewer       |                                        |
 | service.legal.admin          |                                        |
 | service.legal.editor         |                                        |
 | data.test1                   |                                        |
 | data.integration.test        |                                        |

Execute following command to build code and run all the integration tests:

### Run Tests simulating Pipeline

* Prior to running tests, scripts must be executed locally to generate pipeline env vars

```bash
testing/storage-test-aws/build-aws/prepare-dist.sh

#Set Neccessary ENV Vars here as defined in run-tests.sh

dist/testing/integration/build-aws/run-tests.sh 
```

### Run Tests using mvn
Set required env vars and execute the following:
```
mvn clean package -f testing/pom.xml -pl storage-test-core,storage-test-aws -DskipTests
mvn test -f testing/storage-test-aws/pom.xml
```



## License
Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
