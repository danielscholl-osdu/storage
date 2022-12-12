# Collaboration context

## Table of Contents
- [Introduction](#introduction)
- [Collaboration Context <a name="collaboration-context">](#collaboration-context)
    - [HTTP header syntax <a name="http-header-syntax"></a>](#http-header-syntax)
    - [Request directives <a name="request-directives"></a>](#request-directives)
    - [Examples <a name="example-requests"></a>](#example-requests)
- [Integration <a name="integration"></a>](#integration)
    - [How to integrate collaboration context <a name="how-to-integrate-collaboration-context"></a>](#how-to-integrate-collaboration-context)
    - [Sample implementation of Azure <a name="sample-implementation-of-azure"></a>](#sample-implementation-of-azure)
- [Reference <a name="reference"></a>](#reference)

## Introduction <a name="introduction"></a>

Collaboration enables domain users to always consume quality data from the OSDU, share data within your team and control how and what you want to share back. This maintains the data integrity of the OSDU while enabling teams to succeed in creating new value.

## Collaboration Context <a name="collaboration-context"></a>
All APIs in storage service can be collaboration context-aware. Please refer to the '[Integration <a name="integration"></a>](#integration)' section for further implementation details. This functionality is behind a collaboration feature flag which is set to false by default. The functionality of the existing storage service will not be changed with this feature flag set to false.
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

#Integration <a name="integration"></a>
All APIs in storage service are collaboration context-aware in Azure CSP when the collaboration context feature flag is set to true.
Meaning that any interface changes (that are implemented by all CSPs) have a "dummy" implementation that ignores the functionality related to collaboration. 
Other CSPs can implement this functionality at their own pace. 

### How to integrate collaboration context <a name="how-to-integrate-collaboration-context"></a>
There are few things to consider when implementing APIs to support collaboration context.
  1. The current record changed topic is not triggered by any changes when a collaboration context is provided.
  2. There should be a new topic that is triggered for all requests regardless of collaboration context. Note that collaboration context should be sent as part of the message if provided.
  3. The old functionality of storage should not be changed if collaboration context is not provided.

### Sample implementation of Azure <a name="sample-implementation-of-azure"></a>
Please refer to this MR for [implementation of Azure](https://community.opengroup.org/osdu/platform/system/storage/-/merge_requests/546).

##### Reference <a name="reference"></a>
More info about __Namespacing storage records__ can be found [here](https://community.opengroup.org/osdu/platform/system/storage/-/issues/149).
