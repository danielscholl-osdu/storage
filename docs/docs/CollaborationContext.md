# Collaboration Context

## Introduction

The Collaboration Context feature enables domain users to work with records in isolated 
collaboration namespaces and maintaining data integrity in the OSDU. 

This feature allows teams to:

- Consume quality data from the OSDU
- Share data within teams
- Control how and what data is shared back
- Maintain data integrity while enabling collaboration

### Key Concepts

- **Collaboration Context**: A namespace that isolates records from the Source of Record (SOR) namespace
- **x-collaboration Header**: HTTP header that specifies the collaboration context for API requests
- **Feature Flag**: Controls whether collaboration functionality is enabled (default: `false`)
- **Message Topics**: Separate messaging topics for collaboration and non-collaboration messages

### Feature Flag Behavior

The collaboration feature is controlled by a `COLLABORATIONS_ENABLED` feature flag that defaults to `false`.

- **Feature Flag = `true`**:
   - Requests with `x-collaboration` header > messages sent to `recordstopic-v2` only
   - Requests without `x-collaboration` header > messages sent to `recordstopic-v2` only (single queue approach)

- **Feature Flag = `false`**:
   - Requests with `x-collaboration` header > rejected with Not implemented exception
   - Requests without `x-collaboration` header > messages sent to `recordstopic` only (normal behavior)

### Collaboration Header format

```
x-collaboration: id=<collaboration-uuid>,application=<application-name>
```

**Example**:
```
x-collaboration: id=7d34b896-6b55-40e0-a628-e696f3c00000,application=pws
```

---

## Architecture

### Collaboration filter 

The `CollaborationFilter` can exclude certain paths from collaboration validation:

**Default Excluded Paths**:
- `info`
- `swagger`
- `health`
- `api-docs`

**Custom Configuration**:
```properties
collaborationFilter.excludedPaths=info,health,liveness_check,swagger,swagger-ui/swagger-ui.css,swagger-ui/swagger-ui-standalone-preset.js,api-docs,api-docs.yaml,api-docs/swagger-config
```

### Topics

- **recordstopic (V1)**: Existing topic for non-collaboration messages, SOR namespace only, for backward compatibility

- **recordstopic-v2 (V2)**: New topic for collaboration messages, includes `x-collaboration` attribute when applicable,
Collaboration + SOR messages

### Indexer-Queue (if applicable)

The Indexer-Queue service subscribes to both V1 and V2 topics, queues messages in a single queue, 
and sends them to the Indexer service push API.

### Indexer

The Indexer service handle collaboration and non-collaboration messages based on 
the `x-collaboration` attribute. It adds a prefix to document IDs and includes a "Collaboration" 
property for collaboration records.

### Search Service

Search service supports the optional `x-collaboration` header to search within specified collaboration namespaces.

## Data Isolation

Records in collaboration contexts are isolated through:

- **Document ID Prefixing**: Elasticsearch document IDs include collaboration context
   - Example: `osdu:master-data--CollaborationProject:xxx:id=a99cef48-2ed6-4beb-8a43-002373431f21,application=pws`
- **Metadata Field**: `x-collaboration` field in Elasticsearch documents
- **Query Filtering**: Search queries automatically filter by collaboration context

---

## Configuration Guide

```properties
# Feature flag strategy. appProperty - get value from environment variable, dataPartition - get value from Partition service
featureFlag.strategy=appProperty

# Collaboration feature flag (default: false)
collaborations-enabled=${COLLABORATIONS_ENABLED:false}

# Topic configuration
records-changed-topic-name=${RECORDS_CHANGED_TOPIC_NAME:records-changed}
records-changed-v2-topic-name=${RECORDS_CHANGED_V2_TOPIC_NAME:records-changed-v2}
```

## Infrastructure Setup

- `recordstopic-v2` topic for collaboration messages
- Notification service related V2 topics (service, publishing, retry, dead-letter topics) 

## Collaboration Context adoption

All APIs in storage service can be collaboration context-aware. Please refer to the 
[Collaboration Integration](CollaborationIntegration.md) tutorial for further implementation details. 
This functionality is behind a collaboration feature flag which is set to false by default. 
The functionality of the existing storage service will not be changed with this feature flag set to false.
When it is set to true the old functionality is still not changed however you can work with Records 
in new contexts using the x-collaboration header when it is optionally provided.

In order to use storage apis in a collaboration context, the API user needs to add 
a __x-collaboration HTTP header__ to the requests.
The header holds directives instructing the OSDU to handle in context of the provided collaboration 
instance and not in the context of the promoted or trusted data.

### Sample implementation to integrate with records changed messages
Please refer to this MR for [implementation of Azure](https://community.opengroup.org/osdu/platform/system/storage/-/merge_requests/546).

Consumers who want to integrate with record change messages that include changes made within a 
collaboration context need to register the records to the new topic "recordstopic-v2". 
Refer the [DataNotification.md](https://community.opengroup.org/osdu/platform/system/notification/-/blob/master/docs/tutorial/DataNotification.md) file for details about the recordstopics-v2.

This topic exists in addition to the current record changed topic and receives both collaboration 
and non collaboration messages when the collaborations feature flag is enabled.

The current record changed topic however does not receive messages when collaboration context is provided. 
Meaning, the original functionality of storage should not be changed if collaboration context is not provided.

In summary,
1. If feature flag is set to true:
   1. A request with x-collaboration header: should send a message to recordstopic-v2
   2. A request without x-collaboration header: should send a message to recordstopic-v2 only (single queue approach)
2. If feature flag is set to false:
   1. A request with x-collaboration header: should not send a message to any topic
   2. A request without x-collaboration header: should send a message to recordstopic

The message contains the collaboration context header as an atribute when a change is made in context of a collaboration.

#### Example of a message when the x-collaboration header is provided -
```json
{
   "message": {
      "data": [
         {
            "id": "opendes:wellbore:f213e42d5fa848f592917a8df7fed132",
            "version": "1617915304347525",
            "modifiedBy": "abc@xyz.com",
            "kind": "common:welldb:wellbore:1.0.0",
            "op": "create"
         }
      ],
      "account-id": "opendes",
      "data-partition-id": "opendes",
      "correlation-id": "5t3c153e-8f03-4295-8b1a-edaae86dfafa",
      "x-collaboration": "id=7d34b896-6b55-40e0-a628-e696f3c00000,application=app"
   }
}
```
#### Example of a message when the x-collaboration header is not provided -
```json
{
   "message": {
      "data": [
         {
            "id": "opendes:inttest:1674654754283",
            "kind": "opendes:wks:inttest:1.0.1674654754283",
            "op": "create"
         }
      ],
      "account-id": "opendes",
      "data-partition-id": "opendes",
      "correlation-id": "2715a1b8-2ffb-406f-839c-6e6bfed27e5c"
   }
}
```

### HTTP header syntax
* Caching directives are case-insensitive but lowercase is recommended
* Multiple directives are comma-separated

### Request directives
| Directive    | Optionality | Description                                                                                                              |
|:-------------|:------------|:-------------------------------------------------------------------------------------------------------------------------|
| id          | Mandatory   | ID of the collaboration to handle the request against.                                                                   |
| application | Mandatory   | Name of the application sending the request                                                                              |
| other directives | Optional    | Other directives include but not limited to transaction ID to handle this request against. The transaction must exist and be in an active state on the collaboration |

### Example requests
<details><summary>GET the latest version of a record in collaboration context</summary>

```
curl --request GET \
  --url '/api/storage/v2/records/{id}'\
  --header 'accept: application/json' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: opendes' \
  --header 'x-collaboration: id=9e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=Test app'\
```
</details>
<details><summary>CREATE or UPDATE a new record in a collaboration context</summary>

```
curl --request PUT \
  --url '/api/storage/v2/records' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: opendes' \
  --header 'x-collaboration: id=9e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=Test app' \
  --data '[{
       "id": "data-partition-id:hello:123456",
       "kind": "schema-authority:wks:hello:1.0.0",
       "acl": {
         "viewers": ["data.default.viewers@data-partition-id.[osdu.opengroup.org]"],
         "owners": ["data.default.owners@data-partition-id.[osdu.opengroup.org]"]
       },
       "legal": {
         "legaltags": ["data-partition-id-sample-legaltag"],
         "otherRelevantDataCountries": ["FR","US","CA"]
       },
       "data": {
         "msg": "Hello World, Data Ecosystem!"
       }
    }]'
```
</details>

### Excluded Paths
CollaborationFilter, when enabled with data partition _feature flag strategy_, makes a call to Partition service. This call requires `data-partition-id` header, which is not passed/required for certain apis (_info, swagger, health, etc_)
We can short-circuit the CollaborationFilter class when url contains one of these paths.

Property used (CSP Specific)
- Default paths if not specified : [ _info,swagger,health,api-docs_ ]
- customized using ``collaborationFilter.excludedPaths=info,swagger,health,api-docs``


## Additional information
- [ADR: Namespacing storage records](https://community.opengroup.org/osdu/platform/system/storage/-/issues/149)
- [ADR - Project & Workflow Services - ADR Summary](https://community.opengroup.org/osdu/platform/system/home/-/issues/104)
- [ADR - Project & Workflow Services - Core Services Integration - Solution Overview](https://community.opengroup.org/osdu/platform/system/home/-/issues/105)
- [ADR - Project & Workflow Services - Core Services Integration - Collaboration Service](https://community.opengroup.org/osdu/platform/system/home/-/issues/106)
- [ADR - Project & Workflow Services - Core Services Integration - E&O](https://community.opengroup.org/osdu/platform/system/home/-/issues/107)
- [ADR - Project & Workflow Services - Core Services Integration - Search Service Support](https://community.opengroup.org/osdu/platform/system/home/-/issues/108)
- [ADR - Project & Workflow Services - Core Services Integration - Copy Record references between namespaces](https://community.opengroup.org/osdu/platform/system/home/-/issues/109)
- [ADR - Project & Workflow Services - Application Integration](https://community.opengroup.org/osdu/platform/system/home/-/issues/110)
- [ADR - Project & Workflow Services - Namespace-per-Kind & Kind-per-Namespace inventory features](https://community.opengroup.org/osdu/platform/system/home/-/issues/114)
- [PWS Project](https://community.opengroup.org/osdu/platform/system/project-and-workflow)
