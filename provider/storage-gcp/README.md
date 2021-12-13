# Storage Service

storage-gcp is a [Spring Boot](https://spring.io/projects/spring-boot) service that provides a set of APIs to manage the
entire metadata life-cycle such as ingestion (persistence), modification, deletion, versioning and data schema.

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing
purposes. See deployment for notes on how to deploy the project on a live system.

# Features of implementation

This is a universal solution created using EPAM OSM, OBM and OQM mappers technology. It allows you to work with various
implementations of KV stores, Blob stores and message brokers.

## Limitations of the current version

In the current version, the mappers are equipped with several drivers to the stores and the message broker:

- OSM (mapper for KV-data): Google Datastore; Postgres
- OBM (mapper to Blob stores): Google Cloud Storage (GCS); MinIO
- OQM (mapper to message brokers): Google PubSub; RabbitMQ

## Extensibility

To use any other store or message broker, implement a driver for it. With an extensible set of drivers, the solution is
unrestrictedly universal and portable without modification to the main code.

Mappers support "multitenancy" with flexibility in how it is implemented. They switch between datasources of different
tenants due to the work of a bunch of classes that implement the following interfaces:

- Destination - takes a description of the current context, e.g., "data-partition-id = opendes"
- DestinationResolver – accepts Destination, finds the resource, connects, and returns Resolution
- DestinationResolution – contains a ready-made connection, the mapper uses it to get to data

# Settings and Configuration

## Requirements

### Mandatory

* JDK 8
* Lombok 1.16 or later
* Maven

### for Google Cloud only

* GCloud SDK with java (latest version)

## General Tips

## Mapper tuning mechanisms

This service uses specific implementations of DestinationResolvers based on the tenant information provided by the OSDU
Partition service. A total of 6 resolvers are implemented, which are divided into two groups:

### for universal technologies:

- for Postgres: mappers/osm/PgTenantOsmDestinationResolver.java
- for MinIO: mappers/obm/MinioTenantObmDestinationResolver.java
- for RabbitMQ: mappers/oqm/MqTenantOqmDestinationResolver.java

#### Their algorithms are as follows:

- incoming Destination carries data-partition-id
- resolver accesses the Partition service and gets PartitionInfo
- from PartitionInfo resolver retrieves properties for the connection: URL, username, password etc.
- resolver creates a data source, connects to the resource, remembers the datasource
- resolver gives the datasource to the mapper in the Resolution object

### for native Google Cloud technologies:

- for Datastore: mappers/osm/DsTenantOsmDestinationResolver.java
- for GCS: mappers/obm/GcsTenantObmDestinationResolver.java
- for PubSub: mappers/oqm/PsTenantOqmDestinationResolver.java

#### Their algorithms are similar,

Except that they do not receive special properties from the Partition service for connection, because the location of
the resources is unambiguously known - they are in the GCP project. And credentials are also not needed - access to data
is made on behalf of the Google Identity SA under which the service itself is launched. Therefore, resolver takes only
the value of the **projectId** property from PartitionInfo and uses it to connect to a resource in the corresponding GCP
project.

# Configuration

## Service Configuration

Define the following environment variables. Most of them are common to all hosting environments, but there are
properties that are only necessary when running in Google Cloud.

#### Common properties for all environments

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
| `POLICY_API` | ex `http://localhost:8080/api/policy/v1/` | Police service endpoint | no | output of infrastructure deployment |
| `POLICY_ID` | ex `search` | policeId from ex `http://localhost:8080/api/policy/v1/policies`. Look at `POLICY_API` | no | - |
| `PARTITION_API` | ex `http://localhost:8081/api/partition/v1` | Partition service endpoint | no | - |

#### For Mappers, to activate drivers

| name      | value     | description                                             |
|-----------|-----------|---------------------------------------------------------|
| OSMDRIVER | datastore | to activate **OSM** driver for **Google Datastore**     |
| OSMDRIVER | postgres  | to activate **OSM** driver for **PostgreSQL**           |
| OBMDRIVER | gcs       | to activate **OBM** driver for **Google Cloud Storage** |
| OBMDRIVER | minio     | to activate **OBM** driver for **MinIO**                |
| OQMDRIVER | pubsub    | to activate **OQM** driver for **Google PubSub**        |
| OQMDRIVER | rabbitmq  | to activate **OQM** driver for **Rabbit MQ**            |

#### For Google Cloud only

| name                         | value                                 | description                                                        | sensitive? | source                                            |
|------------------------------|---------------------------------------|--------------------------------------------------------------------|------------|---------------------------------------------------|
| `GOOGLE_AUDIENCES` | ex `*****.apps.googleusercontent.com` | Client ID for getting access to cloud resources | yes | https://console.cloud.google.com/apis/credentials |
| `GOOGLE_APPLICATION_CREDENTIALS` | ex `/path/to/directory/service-key.json` | Service account credentials, you only need this if running locally | yes | https://console.cloud.google.com/iam-admin/serviceaccounts |

### Requirements for requests.

Record identifiers cannot contain a space character. At the same time, they may contain a % character, which, when
combined with subsequent numeric characters, may cause the application to misinterpret that combination. For example,
the "%20" combination will be interpreted as a space " " character. To correctly transfer such an identifier, you should
additionally perform the url-encode operation on it. This functionality can be built into the front-end application, or
you can use an online url-encoder tool ( eg.: https://www.urlencoder.org/). Thus, having ID "osdu:
work-product-component--WellboreMarkerSet:3D%20Kirchhoff%20DepthMigration" (with %20 combination)
you should url-encode it and request
"osdu%3Awork-product-component--WellboreMarkerSet%3A3D%2520Kirchhoff%2520DepthMigration" instead.

## Configuring mappers' Datasources

When using non-Google-Cloud-native technologies, property sets must be defined on the Partition service as part of
PartitionInfo for each Tenant.

They are specific to each storage technology:

#### for OSM - Postgres:

**database structure**
OSM works with data logically organized as "partition"->"namespace"->"kind"->"record"->"columns". The above sequence
describes how it is named in Google Datastore, where "partition" maps to "GCP project".

For example, this is how **Datastore** OSM driver contains records for "RecordsChanged" data register:

| hierarchy level     | value                            |
|---------------------|----------------------------------|
| partition (opendes) | osdu-cicd-epam                   |
| namespace           | opendes                          |
| kind                | StorageRecord                    |
| record              | `<multiple kind records>`        |
| columns             | acl; bucket; kind; legal; etc... |

And this is how **Postges** OSM driver does. Notice, the above hierarchy is kept, but Postgres uses alternative entities
for it.

| Datastore hierarchy level |     | Postgres alternative used  |
|---------------------------|-----|----------------------------|
| partition (GCP project)   | ==  | Postgres server URL        |
| namespace                 | ==  | Schema                     |
| kind                      | ==  | Table                      |
| record                    | ==  | '<multiple table records>' |
| columns                   | ==  | id, data (jsonb)           |

As we can see in the above table, Postgres uses different approach in storing business data in records. Not like
Datastore, which segments data into multiple physical columns, Postgres organises them into the single JSONB "data"
column. It allows provisioning new data registers easily not taking care about specifics of certain registers structure.
In the current OSM version (as on December'21) the Postgres OSM driver is not able to create new tables in runtime. 

So this is a responsibility of DevOps / CICD to provision all required SQL tables (for all required data kinds) when on new
environment or tenant provisioning when using Postgres. Detailed instructions (with examples) for creating new tables is
in the **OSM module Postgres driver README.md** `org/opengroup/osdu/core/gcp/osm/translate/postgresql/README.md`

As a quick shortcut, this example snippet can be used by DevOps DBA:
```postgres-psql
--CREATE SCHEMA "exampleschema";
CREATE TABLE exampleschema."ExampleKind"(
    id text COLLATE pg_catalog."default" NOT NULL,
    pk bigint NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    data jsonb NOT NULL,
    CONSTRAINT ExampleKind_id UNIQUE (id)
);
CREATE INDEX ExampleKind_datagin ON exampleschema."ExampleKind" USING GIN (data);
```

**prefix:** `osm.postgres`
It can be overridden by:

- through the Spring Boot property `osm.postgres.partitionPropertiesPrefix`
- environment variable `OSM_POSTGRES_PARTITIONPROPERTIESPREFIX`

**Propertyset:**

| Property | Description |
| --- | --- |
| osm.postgres.datasource.url | server URL |
| osm.postgres.datasource.username | username |
| osm.postgres.datasource.password | password |

<details><summary>Example of a definition for a single tenant</summary>

```

curl -L -X PATCH 'https://dev.osdu.club/api/partition/v1/partitions/opendes' -H 'data-partition-id: opendes' -H 'Authorization: Bearer ...' -H 'Content-Type: application/json' --data-raw '{
  "properties": {
    "osm.postgres.datasource.url": {
      "sensitive": false,
      "value": "jdbc:postgresql://35.239.205.90:5432/postgres"
    },
    "osm.postgres.datasource.username": {
      "sensitive": false,
      "value": "osm_poc"
    },
    "osm.postgres.datasource.password": {
      "sensitive": true,
      "value": "osm_poc"
    }
  }
}'

```

</details>

#### for OBM - MinIO:

**prefix:** `obm.minio`
It can be overridden by:

- through the Spring Boot property `osm.postgres.partitionPropertiesPrefix`
- environment variable `OBM_MINIO_PARTITIONPROPERTIESPREFIX`

**Propertyset:**

| Property            | Description            |
|---------------------|------------------------|
| obm.minio.endpoint  | server URL             |
| obm.minio.accessKey | credentials access key |
| obm.minio.secretKey | credentials secret key |

<details><summary>Example of a definition for a single tenant</summary>

```

curl -L -X PATCH 'https://dev.osdu.club/api/partition/v1/partitions/opendes' -H 'data-partition-id: opendes' -H 'Authorization: Bearer ...' -H 'Content-Type: application/json' --data-raw '{
  "properties": {
    "obm.minio.endpoint": {
      "sensitive": false,
      "value": "http://localhost:9000"
    },
    "obm.minio.accessKey": {
      "sensitive": false,
      "value": "QU2D8DWD3RT7XUPSCCXH"
    },
    "obm.minio.secretKey": {
      "sensitive": true,
      "value": "9sJd5v23Ywr6lEflQjxtmaKoITsVBOdKYMQ2XSoK"
    }
  }
}'

```

</details>

#### for OQM - RabbitMQ:

**prefix:** `oqm.rabbitmq`
It can be overridden by:

- through the Spring Boot property `oqm.rabbitmq.partitionPropertiesPrefix`
- environment variable `OQM_RABBITMQ_PARTITIONPROPERTIESPREFIX`

**Propertyset** (for two types of connection: messaging and admin operations):

| Property | Description |
| --- | --- |
| oqm.rabbitmq.amqp.host | messaging hostnameorIP |
| oqm.rabbitmq.amqp.port | - port |
| oqm.rabbitmq.amqp.path | - path |
| oqm.rabbitmq.amqp.username | - username |
| oqm.rabbitmq.amqp.password | - password |
| oqm.rabbitmq.admin.schema | admin host schema |
| oqm.rabbitmq.admin.host | - host name |
| oqm.rabbitmq.admin.port | - port |
| oqm.rabbitmq.admin.path | - path |
| oqm.rabbitmq.admin.username | - username |
| oqm.rabbitmq.admin.password | - password |

<details><summary>Example of a single tenant definition</summary>

```

curl -L -X PATCH 'https://dev.osdu.club/api/partition/v1/partitions/opendes' -H 'data-partition-id: opendes' -H 'Authorization: Bearer ...' -H 'Content-Type: application/json' --data-raw '{
  "properties": {
    "oqm.rabbitmq.amqp.host": {
      "sensitive": false,
      "value": "localhost"
    },
    "oqm.rabbitmq.amqp.port": {
      "sensitive": false,
      "value": "5672"
    },
    "oqm.rabbitmq.amqp.path": {
      "sensitive": false,
      "value": ""
    },
    "oqm.rabbitmq.amqp.username": {
      "sensitive": false,
      "value": "guest"
    },
    "oqm.rabbitmq.amqp.password": {
      "sensitive": true,
      "value": "guest"
    },

     "oqm.rabbitmq.admin.schema": {
      "sensitive": false,
      "value": "http"
    },
     "oqm.rabbitmq.admin.host": {
      "sensitive": false,
      "value": "localhost"
    },
    "oqm.rabbitmq.admin.port": {
      "sensitive": false,
      "value": "9002"
    },
    "oqm.rabbitmq.admin.path": {
      "sensitive": false,
      "value": "/api"
    },
    "oqm.rabbitmq.admin.username": {
      "sensitive": false,
      "value": "guest"
    },
    "oqm.rabbitmq.admin.password": {
      "sensitive": true,
      "value": "guest"
    }
  }
}'

```

</details>

## Interaction with message brokers

### Specifics of work through PULL subscription

To receive messages from brokers, this solution uses the PULL-subscriber mechanism to get 'legaltags_changed' messages.
This is its cardinal difference from other implementations that use PUSH-subscribers (webhooks). This opens a wide
choice when choosing brokers.

When using PULL-subscribers, there is a need to restore Storage service subscribers at the start of Storage service.
This magic happens in the `provider/gcp/pubsub/OqmSubscriberManager.java` class in the @PostConstruct method.

# Run and test the service

### Run Locally

Check that maven is installed:

```bash
$ mvn --version
Apache Maven 3.6.0
Maven home: /usr/share/maven
Java version: 1.8.0_212, vendor: AdoptOpenJDK, runtime: /usr/lib/jvm/jdk8u212-b04/jre
...
```

You may need to configure access to the remote maven repository that holds the OSDU dependencies. This file should live
within `~/.mvn/community-maven.settings.xml`:

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

After configuring your environment as specified above, you can follow these steps to build and run the application.
These steps should be invoked from the *repository root.*

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

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
