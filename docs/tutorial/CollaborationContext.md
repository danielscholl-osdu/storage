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
