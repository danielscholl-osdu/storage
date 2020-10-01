# Storage Service
storage-gcp is a [Spring Boot](https://spring.io/projects/spring-boot) service that provides a set of APIs to manage the entire metadata life-cycle such as ingestion (persistence), modification, deletion, versioning and data schema.

## Getting Started
These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites
Pre-requisites

* GCloud SDK with java (latest version)
* JDK 8
* Lombok 1.16 or later
* Maven

### Installation
In order to run the service locally or remotely, you will need to have the following environment variables defined.

| name | value | description | sensitive? | source |
| ---  | ---   | ---         | ---        | ---    |
| `LOG_PREFIX` | `storage` | Logging prefix | no | - |
| `SERVER_SERVLET_CONTEXPATH` | `/api/storage/v2/` | Servlet context path | no | - |
| `AUTHORIZE_API` | ex `https://entitlements.com/entitlements/v1` | Entitlements API endpoint | no | output of infrastructure deployment |
| `LEGALTAG_API` | ex `https://legal.com/api/legal/v1` | Legal API endpoint | no | output of infrastructure deployment |
| `PUBSUB_SEARCH_TOPIC` | ex `records-changed` | PubSub topic name | no | https://console.cloud.google.com/cloudpubsub/topic |
| `REDIS_GROUP_HOST` | ex `127.0.0.1` | Redis host for groups | no | https://console.cloud.google.com/memorystore/redis/instances |
| `REDIS_STORAGE_HOST` | ex `127.0.0.1` | Redis host for storage | no | https://console.cloud.google.com/memorystore/redis/instances |
| `STORAGE_HOSTNAME` | ex `os-storage-dot-opendes.appspot.com` | Hostname | no | - |
| `GOOGLE_AUDIENCES` | ex `*****.apps.googleusercontent.com` | Client ID for getting access to cloud resources | yes | https://console.cloud.google.com/apis/credentials |
| `GOOGLE_APPLICATION_CREDENTIALS` | ex `/path/to/directory/service-key.json` | Service account credentials, you only need this if running locally | yes | https://console.cloud.google.com/iam-admin/serviceaccounts |

### Run Locally
Check that maven is installed:

```bash
$ mvn --version
Apache Maven 3.6.0
Maven home: /usr/share/maven
Java version: 1.8.0_212, vendor: AdoptOpenJDK, runtime: /usr/lib/jvm/jdk8u212-b04/jre
...
```

You may need to configure access to the remote maven repository that holds the OSDU dependencies. This file should live within `~/.mvn/community-maven.settings.xml`:

```bash
$ cat ~/.m2/settings.xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>community-maven-via-private-token</id>
            <!-- Treat this auth token like a password. Do not share it with anyone, including Microsoft support. -->
            <!-- The generated token expires on or before 11/14/2019 -->
             <configuration>
              <httpHeaders>
                  <property>
                      <name>Private-Token</name>
                      <value>${env.COMMUNITY_MAVEN_TOKEN}</value>
                  </property>
              </httpHeaders>
             </configuration>
        </server>
    </servers>
</settings>
```
* Update the Google cloud SDK to the latest version:

```bash
gcloud components update
```
* Set Google Project Id:

```bash
gcloud config set project <YOUR-PROJECT-ID>
```

* Perform a basic authentication in the selected project:

```bash
gcloud auth application-default login
```

* Navigate to storage service's root folder and run:

```bash
mvn clean install   
```

* If you wish to see the coverage report then go to target\site\jacoco\index.html and open index.html

* If you wish to build the project without running tests

```bash
mvn clean install -DskipTests
```

After configuring your environment as specified above, you can follow these steps to build and run the application. These steps should be invoked from the *repository root.*

```bash
cd provider/storage-gcp/ && mvn spring-boot:run
```

## Testing
 
 ### Running E2E Tests 
 This section describes how to run cloud OSDU E2E tests (testing/storage-test-gcp).
 
 You will need to have the following environment variables defined.
 
 | name | value | description | sensitive? | source |
 | ---  | ---   | ---         | ---        | ---    |
 | `INTEGRATION_TEST_AUDIENCE` | `*****.apps.googleusercontent.com` | client application ID | yes | https://console.cloud.google.com/apis/credentials |
 | `DEPLOY_ENV` | `empty` | Required but not used, should be set up with string "empty"| no | - |
 | `DOMAIN` | ex`opendes-gcp.projects.com` | OSDU R2 to run tests under | no | - |
 | `INTEGRATION_TESTER` | `********` | Service account base64 encoded string for API calls. Note: this user must have entitlements configured already | yes | https://console.cloud.google.com/iam-admin/serviceaccounts |
 | `LEGAL_URL` | ex`http://localhsot:8080/api/legal/v1/` | Legal API endpoint | no | - |
 | `NO_DATA_ACCESS_TESTER` | `********` | Service account base64 encoded string without data access | yes | https://console.cloud.google.com/iam-admin/serviceaccounts |
 | `PUBSUB_TOKEN` | `****` | ? | no | - |
 | `STORAGE_URL` | ex`http://localhost:8080/api/storage/v2/` | Endpoint of storage service | no | - |
 | `TENANT_NAME` | ex `opendes` | OSDU tenant used for testing | no | -- |
 
 **Entitlements configuration for integration accounts**
 
 | INTEGRATION_TESTER | NO_DATA_ACCESS_TESTER | 
 | ---  | ---   |
 | users<br/>service.entitlements.user<br/>service.storage.admin<br/>service.storage.creator<br/>service.storage.viewer<br/>service.legal.admin<br/>service.legal.editor<br/>data.test1<br/>data.integration.test | users<br/>service.entitlements.user<br/>service.storage.admin |
 
 Execute following command to build code and run all the integration tests:
 
 ```bash
 # Note: this assumes that the environment variables for integration tests as outlined
 #       above are already exported in your environment.
 # build + install integration test core
 $ (cd testing/storage-test-core/ && mvn clean install)
 ```
 ```bash
 # build + run GCP integration tests.
 $ (cd testing/storage-test-gcp/ && mvn clean test)
 ```

## Deployment
Storage Service is compatible with App Engine Flexible Environment and Cloud Run.

* To deploy into Cloud run, please, use this documentation:
https://cloud.google.com/run/docs/quickstarts/build-and-deploy

* To deploy into App Engine, please, use this documentation:
https://cloud.google.com/appengine/docs/flexible/java/quickstart

## License
Copyright © Google LLC

Copyright © EPAM Systems
 
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
 
[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)
 
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.