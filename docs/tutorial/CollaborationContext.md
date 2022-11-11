# Collaboration context

> Collaboration enables domain users to always consume quality data from the OSDU, share data within your team and control how and what you want to share back. This maintains the data integrity of the OSDU while enabling teams to succeed in creating new value.

In order to use storage api in a collaboration context, the API user needs to add a __x-collaboration HTTP header__ to the requests.
The header holds directives instructing the OSDU to handle in context of the provided collaboration instance and not in the context of the promoted or trusted data.

### HTTP Header Syntax
* Caching directives are case-insensitive but lowercase is recommended
* Multiple directives are comma-separated

### Request Directives
| Directive    | Optionality | Description                                                                                                              |
|:-------------|:------------|:-------------------------------------------------------------------------------------------------------------------------|
| id          | Mandatory   | ID of the collaboration to handle the request against.                                                                   |
| application | Mandatory   | Name of the application sending the request                                                                              |
| transaction | Optional    | Transaction ID to handle this request against. The transaction must exist and be in an active state on the collaboration |

### Examples
<details><summary>GET the latest version in collaboration</summary>

```
curl --request GET \
  --url '/api/storage/v2/records/{id}'\
  --header 'accept: application/json' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: common' \
  --header 'x-collaboration: id=collaboration-id, application=app-name'\
```
</details>
<details><summary>CREATE or UPDATE a Wellbore entity in a collaboration</summary>

```
curl --request POST\
  --url '/api/os-wellbore-ddms/ddms/v3/wellbores' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: opendes' \
  --header  'x-collaboration: id=collaboration-id, transaction=transaction-id, application=app-name' \
  --data '[{
         "id": "<welllog-id-1>"
          ...
    }]' \
```
</details>

### Record structure 
For collaboration context each Cloud Service Provider should use collaboration id combined with the record id for the primary key of the metadata's data model.
That is, the __record id__ in this case can be represented as:

``{Data-Partition-Id}:{object-type}:{uuid}{collaboration_id}``

##### More info 
More info about __Namespacing storage records__ can be found [here](https://community.opengroup.org/osdu/platform/system/storage/-/issues/149).

