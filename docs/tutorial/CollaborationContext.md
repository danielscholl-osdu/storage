# Collaboration context

## Table of Contents
- [Introduction](#introduction)
- [Collaboration Context <a name="collaboration-context">](#collaboration-context)
    - [HTTP header syntax <a name="http-header-syntax"></a>](#http-header-syntax)
    - [Request directives <a name="request-directives"></a>](#request-directives)
    - [Examples <a name="example-requests"></a>](#example-requests)
- [Reference <a name="reference"></a>](#reference)

## Introduction <a name="introduction"></a>

Collaboration enables domain users to always consume quality data from the OSDU, share data within your team and control how and what you want to share back. This maintains the data integrity of the OSDU while enabling teams to succeed in creating new value.

## Collaboration Context <a name="collaboration-context"></a>
All APIs in storage service can be collaboration context-aware. Please refer to the [Collaboration Integration](CollaborationIntegration.md) tutorial for further implementation details. This functionality is behind a collaboration feature flag which is set to false by default. The functionality of the existing storage service will not be changed with this feature flag set to false.
When it is set to true the old functionality is still not changed however you can work with Records in new contexts using the x-collaboration header when it is optionally provided.

In order to use storage apis in a collaboration context, the API user needs to add a __x-collaboration HTTP header__ to the requests.
The header holds directives instructing the OSDU to handle in context of the provided collaboration instance and not in the context of the promoted or trusted data.

### Sample implementation to integrate with records changed messages
Please refer to this MR for [implementation of Azure](https://community.opengroup.org/osdu/platform/system/storage/-/merge_requests/546).

Consumers who want to integrate with record change messages that include changes made within a collaboration context need to register the records to the new topic "recordstopic-v2". Refer the [DataNotification.md](https://community.opengroup.org/osdu/platform/system/notification/-/blob/master/docs/tutorial/DataNotification.md) file for details about the recordstopics-v2.

This topic exists in addition to the current record changed topic and receives both collaboration and non collaboration messages when the collaborations feature flag is enabled.

The current record changed topic however does not receive messages when collaboration context is provided. Meaning, the original functionality of storage should not be changed if collaboration context is not provided.

In summary,
1. If feature flag is set to true:
   1. A request with x-collaboration header: should send a message to recordstopic-v2
   2. A request without x-collaboration header: should send a message to recordstopic and recordstopic-v2
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

### HTTP header syntax <a name="http-header-syntax"></a>
* Caching directives are case-insensitive but lowercase is recommended
* Multiple directives are comma-separated

### Request directives <a name="request-directives"></a>
| Directive    | Optionality | Description                                                                                                              |
|:-------------|:------------|:-------------------------------------------------------------------------------------------------------------------------|
| id          | Mandatory   | ID of the collaboration to handle the request against.                                                                   |
| application | Mandatory   | Name of the application sending the request                                                                              |
| other directives | Optional    | Other directives include but not limited to transaction ID to handle this request against. The transaction must exist and be in an active state on the collaboration |

### Example requests <a name="example-requests"></a>
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

##### Reference <a name="reference"></a>
More info about __Namespacing storage records__ can be found [here](https://community.opengroup.org/osdu/platform/system/storage/-/issues/149).
