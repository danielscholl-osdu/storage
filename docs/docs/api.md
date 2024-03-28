# Storage Service APIs
The Data Platform Storage service has two different categories of API's 1.Records 2.Query for schema and record management.

## Open API 3.0 - Swagger

- Swagger UI : https://host/context-path/swagger (will redirect to https://host/context-path/swagger-ui/index.html)
- api-docs ([JSON](https://community.opengroup.org/osdu/platform/system/storage/-/blob/master/docs/api/storage_openapi.yaml)
) : https://host/context-path/api-docs
- api-docs (YAML) : https://host/context-path/api-docs.yaml

All the Swagger and OpenAPI related common properties are managed here [swagger.properties](https://community.opengroup.org/osdu/platform/system/storage/-/blob/master/storage-core/src/main/resources/swagger.properties)

## Query

### Query all kinds
The API returns a list of all kinds in the specific {Data-Partition-Id}. 
```
 GET /api/storage/v2/query/kinds
```

#### Parameters

| Parameter | Description |
| :--- | :--- |
| limit | The maximum number of results to return from the given offset. If no limit is provided, then it will return __10__ items. Max number of items which can be fetched by the query is __100__.|

<details><summary>curl</summary>

```
curl --request GET \
  --url '/api/storage/v2/query/kinds' \
  --header 'accept: application/json' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: common' 
  --data '{
  "limit": 10,
 }
```
</details>

### Fetch Records
The API fetches multiple records(maximum 20) from storage service at once, it allows user to request data being converted to common standard by using customized header {frame-of-reference}. Common standard is units in SI, crs in wgs84, elevation in msl, azimuth in true north, dates in utc.
Currently only "none" and "units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;" are valid values for the header {frame-of-reference}. 


As of now, we only support conversion for units and crs. For Unit conversion, we only support conversions of arrays of values and properties of arrays of objects when the array element is the root object. For example, below Object/Array types are supported for unit conversion:
`VerticalMeasurement[].Measurements.VerticalMeasurement,
VerticalMeasurements[].VerticalMeasurement,
VerticalMeasurements.VerticalMeasurement`

However, below is not supported
`VerticalMeasurement.Measurements[].VerticalMeasurement`
`VerticalMeasurements[].Measurements[].VerticalMeasurement` (array element can not be nested inside) 

For Datetime conversion, Object and Array types are not supported yet. Elevation and Azimuth will be available later. Returned records could be either original value or converted(units=SI;crs=wgs84) value depending on users' requests and conversion status, original value will be returned when users not request the conversion or the conversion is requested but failed. In addition to records user requests, if conversion is requested, a list of conversion status of each record would be included in the response, indicating whether the conversion was successful or not, it not, what were the errors happened


```
POST /api/storage/v2/query/records:batch
```

<details><summary>curl</summary>

```
curl --request POST \
  --url '/api/storage/v2/query/records:batch' \
  --header 'Authorization: Bearer <JWT>' \
  --header 'Content-Type: application/json' \
  --header 'Data-Partition-Id: common' \
  --header 'frame-of-reference: units=SI;crs=wgs84;elevation=msl;azimuth=true north;dates=utc;' \
  --data '{
    "records": [
        "common:well:123456789",
        "common:wellTop:abc789456",
        "common:wellLog:4531wega22"
    ]
}
```
</details>

### unitOfMeasureID is now preferred unit declaration
The UoM Meta[] schema supports association of a Unit of Measure to one or more attributes in a JSON record. The core of the UoM schema is the unitOfMeasureID attribute which associates attributes defined in propertyNames to the ID of the UOM in the Unit of Measure Reference list e.g. for a Wellbore record
```
{
            "kind": "Unit",
            "name": "ft",
            "persistableReference": "",
            "propertyNames": [
                "FacilitySpecifications[0].FacilitySpecificationQuantity",
                "VerticalMeasurements[0].VerticalMeasurement"
            ],
            "unitOfMeasureID": "osdu:reference-data--UnitOfMeasure:ft:"
        }
```
`unitOfMeasureID` is taking precedence over `persistableReference` attribute.
`persistableReference` attribute is now updating in fly by fetching `persistableReference` attribute corresponding to `unitOfMeasureID` when `unitOfMeasureID` attribute exists.


## Records
### Create or Update records
The API represents the main injection mechanism into the Data Ecosystem. It allows records creation and/or update. When no record id is provided or when the provided id is not already present in the Data Ecosystemthen a new record is created. If the id is related to an existing record in the Data Ecosystemthen an update operation takes place and a new version of the record is created. 
More details available at [Creating records](#Creating-records) and [Ingesting records](#Ingesting-records) sections.

### Get record version
The API retrieves the specific version of the given record.
The modifyTime and modifyUser info will be version specific.
```
GET /api/storage/v2/records/{id}/{version}

```
#### Parameters

| Parameter | Description |
| :--- | :--- |
| attribute | Filter attributes to restrict the returned fields of the record. Usage: data.{record-data-field-name}.|

<details><summary>curl</summary>

```

 curl --request GET \
  --url '/api/storage/v2/records/{id}/{version}' \
  --header 'accept: application/json' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: common' 
  --data '{
    "attributes": [
    "data.msg"
  ]
}
```
</details>

### Get all record versions
The API returns a list containing all versions for the given record id. 
```
GET /api/storage/v2/records/versions/{id}

```

<details><summary>curl</summary>

```
curl --request GET \
  --url '/api/storage/v2/records/versions/{id}'\
  --header 'accept: application/json' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: common' 
```
</details>


### Get record
This API returns the latest version of the given record.
```
GET /api/storage/v2/records/{id}
```

#### Parameters

| Parameter | Description |
| :--- | :--- |
| attribute | Filter attributes to restrict the returned fields of the record. Usage: data.{record-data-field-name}.|

<details><summary>curl</summary>

```
curl --request GET \
  --url '/api/storage/v2/records/{id}'\
  --header 'accept: application/json' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: common' \
  --data '{
    "attributes": [
    "data.msg"
  ]
}

```
</details>


### Delete record
The API performs a logical deletion of the given record. This operation can be reverted later. This operation can be performed by the owner of the record.
```
POST /api/storage/v2/records/{id}:delete
```

<details><summary>curl</summary>

```
curl --request POST \
   --url '/api/storage/v2/records/{id}:delete' \
   --header 'accept: application/json' \
   --header 'authorization: Bearer <JWT>' \
   --header 'content-type: application/json'\
   --header 'Data-Partition-Id: common'
```
</details>

### Delete records
The API performs a logical deletion of batch of record (max size of a batch is 500 records). This operation can be reverted later by ingesting record with the same id one more time. The deleted (inactive) records will be removed from the index, and therefore will not be returned to the search result. This operation can be performed by the owner of the record.
```
POST /api/storage/v2/records/delete
```

<details><summary>curl</summary>

```
curl --request POST \
   --url '/api/storage/v2/records/delete' \
   --header 'accept: application/json' \
   --header 'authorization: Bearer <JWT>' \
   --header 'content-type: application/json'\
   --header 'Data-Partition-Id: common'
   --data-raw '[
          "tenant:type:unique-identifier",
          "tenant:type:unique-identifier",
          "tenant:type:unique-identifier"
     ]'     
```
</details>

### Purge record versions <a name="Purge-record-versions"></a>
The API performs the permanent physical deletion of the given record versions excluding latest version and any linked records or files if there are any.
If 'limit' query parameter is used then it will delete oldest versions defined by 'limit'.
This operation cannot be undone.
```
DELETE api/storage/v2/records/{id}/versions
```

#### Parameters <a name="parameters"></a>

| Parameter | Description                                                                             |
| :--- |:----------------------------------------------------------------------------------------|
| limit | API will delete oldest versions defined by 'limit', excluding the latest record version |

<details><summary>curl</summary>

```
curl --request DELETE \
   --url 'api/storage/v2/records/{id}/versions?limit=2' \
   --header 'accept: application/json' \
   --header 'authorization: Bearer <JWT>' \
   --header 'content-type: application/json'\
   --header 'Data-Partition-Id: common'
```
</details>

## Metadata update api

This API allows update of records metadata in batch. It takes an array of record ids with/without version
numbers with a maximum number of 500, and updates properties specified in the operation path with value and operation type
provided. If a version number is provided, updates will be applied to the specific version of the record. If not, the
latest version of the record will be updated. Users need to specify the corresponding data partition id in the header as
well.

Users need to provide op(operation type), path, and value in the field 'ops'. The currently supported operations are "
replace", "add", and "remove". The user should be part of the groups that are being replaced/added/removed as ACL. Users
specify the property they want to update in the "path" field, and new values should be provided in the "value" field.

Bulk Update API has the following response codes:


| Code | Description |
| :--- | :--- |
| 200 | The update operation succeeds fully, all records’ metadata get updated.|
| 206 | The update operation succeeds partially. Some records are not updated due to different reasons, including records not found or does not have permission to edit the records. For records whose version number was also provided in the request, they may be locked during metadata update, due to optimistic lock. In this case, the version users provided is not the latest one, the record may be updated by others. If the record version is locked, 'lockedRecordIds' field will be returned. They can retry later with the records’ latest version number, once the record is no longer locked.|
| 400 | The update operation fails when the remove operation makes Legal Tags or ACLs empty.|


```
PATCH /api/storage/v2/records
```

### Replace Tags, ACLs and Legal Tags

In the "replace" operation, property value in "path" would be fully replaced by values provided in the "value" field. If
we need to replace tags ops.value should be colon separated string value.
<details><summary>curl</summary>

```
curl --request PATCH \
   --url '/api/storage/v2/records' \
   --header 'accept: application/json' \
   --header 'authorization: Bearer <JWT>' \
   --header 'content-type: application/json'\
   --header 'Data-Partition-Id: common'
    --data-raw ‘{ 
      "query": { 
        "ids": [
          "tenant1:type:unique-identifier:version",
          "tenant2:type:unique-identifier:version",
          "tenant3:type:unique-identifier:version"
        ]
      }, 
      "ops": [ { 
        "op": "replace", 
        "path": "/legal/legaltags", 
        "value": [
          "opendes-sample-legaltag1",
          "opendes-sample-legaltag2"
        ]
        }, 
        { 
	    "op": "replace", 
	    "path": "/acl/owners", 
	    "value": [
	      "data.default.owner1@opendes.enterprisedata.cloud.slb-ds.com",
	      "data.default.owner2@opendes.enterprisedata.cloud.slb-ds.com"
	    ]
        }, 
        { 
        "op": "replace", 
        "path": "/acl/viewers", 
        "value": [
          "data.default.viewer1@opendes.enterprisedata.cloud.slb-ds.com",
          "data.default.viewer2@opendes.enterprisedata.cloud.slb-ds.com"
        ] 
        },
        {
        "op":"replace",
        "path":"/tags",
        "value":[
          "key1:value1",
          "key2:value2",
          "key3:value3"
          ]
        }
      ] 
    }
```

</details>

### Add Tags, ACLs and Legal Tags

In the "add" operation, the valid Tags, Legal Tags, and ACLs (Acl Viewers, Acl Owners) provided in the "value" field
will be added to the property value in the "path" field. If we need to add tags ops.value should be colon separated
string value.


<details><summary>curl</summary>

```
curl --request PATCH \
   --url '/api/storage/v2/records' \
   --header 'accept: application/json' \
   --header 'authorization: Bearer <JWT>' \
   --header 'content-type: application/json'\
   --header 'Data-Partition-Id: common'
    --data-raw ‘{ 
      "query": { 
        "ids": [
          "tenant1:type:unique-identifier:version",
          "tenant2:type:unique-identifier:version",
          "tenant3:type:unique-identifier:version"
        ]
      }, 
      "ops": [ { 
        "op": "add", 
        "path": "/legal/legaltags", 
        "value": [
          "opendes-sample-legaltag1",
          "opendes-sample-legaltag2"
        ]
        }, 
        { 
	    "op": "add", 
	    "path": "/acl/owners", 
	    "value": [
	      "data.default.owner1@opendes.enterprisedata.cloud.slb-ds.com",
	      "data.default.owner2@opendes.enterprisedata.cloud.slb-ds.com"
	    ]
        }, 
        { 
        "op": "add", 
        "path": "/acl/viewers", 
        "value": [
          "data.default.viewer1@opendes.enterprisedata.cloud.slb-ds.com",
          "data.default.viewer2@opendes.enterprisedata.cloud.slb-ds.com"
        ]
        },
        {
        "op":"add",
        "path":"/tags",
        "value":[
          "key1:value1",
          "key2:value2",
          "key3:value3"
        ]
        }
      ]
    }
```

</details>

### Remove Tags, ACLs and Legal Tags

In the "remove" operation, the valid Tags, Legal Tags, and ACLs (Acl Viewers, Acl Owners) provided in the "value" field
will be removed from the property value in the "path" field. When the given Tags, Legal Tags, or ACLs (Acl Viewers, Acl
Owners) do not exist in corresponding records, the remove succeeds without errors. The Legal Tags and ACLs (Acl Viewers,
Acl Owners) cannot be empty i.e. the user cannot remove all the Legal Tags or ACLs. If we need to remove tags ops.value
should be array of the tags keys which we are going to remove.


<details><summary>curl</summary>

```
curl --request PATCH \
   --url '/api/storage/v2/records' \
   --header 'accept: application/json' \
   --header 'authorization: Bearer <JWT>' \
   --header 'content-type: application/json'\
   --header 'Data-Partition-Id: common'
    --data-raw ‘{ 
      "query": { 
        "ids": [
          "tenant1:type:unique-identifier:version",
          "tenant2:type:unique-identifier:version",
          "tenant3:type:unique-identifier:version"
        ]
      }, 
      "ops": [ { 
        "op": "remove", 
        "path": "/legal/legaltags", 
        "value": [
          "opendes-sample-legaltag1",
          "opendes-sample-legaltag2"
        ]
        }, 
        { 
	    "op": "remove", 
	    "path": "/acl/owners", 
	    "value": [
	      "data.default.owner1@opendes.enterprisedata.cloud.slb-ds.com",
	      "data.default.owner2@opendes.enterprisedata.cloud.slb-ds.com"
	    ]
        }, 
        { 
        "op": "remove", 
        "path": "/acl/viewers", 
        "value": [
          "data.default.viewer1@opendes.enterprisedata.cloud.slb-ds.com",
          "data.default.viewer2@opendes.enterprisedata.cloud.slb-ds.com"
        ]
        },
        {
        "op":"remove",
        "path":"/tags",
        "value":[
          "key1",
          "key2",
          "key3"
        ]
        }
      ] 
    }
```

</details>

> You can use Search service's query or query_with_cursor [apis](https://community.opengroup.org/osdu/platform/system/search-service/-/blob/master/docs/tutorial/SearchService.md) to search for records based on tags. Since tags is part of metadata, it is automatically indexed. This may not work if the kind is old (older than when the tags feature was introduced ~02/25/2021). You may need to re-index the kind with the [reindex](https://community.opengroup.org/osdu/platform/system/indexer-service/-/blob/master/docs/tutorial/IndexerService.md#reindex) api (with `force_clean=true`) from indexer service.

## Records patch api
This API allows update of records data and/or metadata in batch. It takes an array of record ids (without version numbers) with a maximum number of 100, 
and updates properties specified in the operation path with value and operation type provided. Users need to specify the corresponding data partition id in the header as well.
The API response contains list of record IDs that were patched successfully, as well as list of record IDs that failed to be patched, with the list of errors.

**Note**: The input record IDs must not contain version of the records. However, the list of record IDs returned in the response
will have `<recordId>:<version>` format. This is because any `data` update increases the record version,
however `metadata` updates do not. The version returned in the response will be the latest version of each record.

- This API supports PATCH operation in compliant to the [Patch RFC spec](https://www.rfc-editor.org/rfc/rfc6902).
- Users need to provide a list of recordIDs and a list of operations to be performed on each record.
- Each operation has `op`(operation type), `path`, and `value` in the field 'ops' (unless the operation is `remove`, then the field `value` shouldn't be provided).
- The currently supported operations are "replace", "add", and "remove".
- The supported properties for metadata update are `tags`, `acl/viewers`, `acl/owners`, `legal/legaltags`, `ancestry/parents`, `kind` and `meta` (`meta` attribute out of the data block).
- The supported properties for data update are `data`. 
- If `acl` is being updated, the user should be part of the groups that are being replaced/added/removed as ACL.

Records patch API has the following response codes:

| Code | Description                                                                                                                                                                       |
|:-----|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 200  | The update operation succeeds fully, all records’ data and/or metadata are updated.                                                                                               |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 206  | The update operation succeeds partially. Some records are not updated due to different reasons, including records not found or user does not have permission to edit the records. |                                                                                                                                                                                                                                                                                                                                                                                                                      
| 400  | The update operation fails when the input validation fails. Please check below section for more details.                                                                          |

### Input Validation
To remain compliant with the domain data models and business requirements, we perform certain input validation
on the request payload. Please see below table for details:

|                                                                              | Add                                                                              | Replace                                                                     | Remove                                                                      | Remarks                                                                                                                                                                                                                                                  |
|------------------------------------------------------------------------------|----------------------------------------------------------------------------------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| /kind                                                                        | Bad Request                                                                      | Replaces kind                                                               | Bad Request                                                                 | `kind` can only be replaced; `value` must be a raw string & valid kind. Path must match exactly to `/kind`                                                                                                                                               |
| /tags                                                                        | Replaces tags with `value`. Creates `/tags` if it doesn't exist                  | Replaces tags with `value`. `/tags` must exist                              | Removes tags, `value` is ignored. `/tags` must exist                        | `add` and `replace` behavior similar because `/tags` is an object member                                                                                                                                                                                 |
| /tags/key                                                                    | Adds `"key" : "value"` to tags, `/tags` must exist                               | Replaces `/tags/key` with `value`. `/tags/key` must exist                   | Removes `"key" : "value"` from tags, `/tags/key` must exist                 |                                                                                                                                                                                                                                                          |
| /acl/viewers OR /acl/owners OR /legal/legaltags OR /ancestry/parents         | Replaces the target array with value. Creates the attribute if it doesn't exist  | Replaces the target attribute with new value. Target location must exist    | Only `/ancestry/parents` can be removed                                     | In case of add or replace, Path should be an exact match and value must be an array of string values                                                                                                                                                     |
| /acl/viewers/0 OR /acl/owners/0 OR /legal/legaltags/0 OR /ancestry/parents/0 | Adds value to the target index in the array                                      | Replaces value at the target index in the array. Target location must exist | Removes value at the target index in the array. Target location must exist  | Character `-` can be used to mention last index of the target array. For acl and legaltag, the target value must not be an empty array after applying Patch                                                                                              |
| /data                                                                        |                                                                                  |                                                                             |                                                                             | **`/data` doesn't adhere to a rigid structure, therefore users must be cautious when modifying `/data` attributes. Value type must adhere to attribute type defined in Schema service. Any type change can potentially cause indexing/search issues.**   |
| /meta                                                                        |                                                                                  |                                                                             |                                                                             | **if an update for `/meta`, it should be compliant with its structure (i.e. array of Map<String, Object>)**                                                                                                                                              |


Check out some examples below, but refer to the [Patch RFC spec](https://www.rfc-editor.org/rfc/rfc6902) for a comprehensive documentation on JsonPatch and more examples.

**Note**: The examples below only highlight the `ops` array from the input payload, a full curl sample is provided at the end.

### Add Operation
Please note that the `add` operation performs either an add or a replace operation, depending on the target location. Refer to [Patch RFC spec - add](https://www.rfc-editor.org/rfc/rfc6902.html#section-4.1) for the explaination.
1. Add legaltag `abc` to a record, at the end of the `legaltags` array. This will perform an addition because `path` points to an index in an array
    <details><summary>add legaltag</summary>
    
    ```
    "ops": [
            { 
              "op": "add", 
              "path": "/legal/legaltags/-",
              "value": "abc"
            }
          ]
    ```
    </details>

2. Add/Replace `tags` for a record. Note that although the operation is `add`, this adds `/tags` if it doesn't exist or replaces the current value with given value for `/tags`.
This is because the target location is an object member that already exists. Please read [RFC Spec](https://www.rfc-editor.org/rfc/rfc6902.html#section-4.1) for more details.
    <details><summary>replace tags</summary>
    
    ```
    "ops": [
            { 
              "op": "add", 
              "path": "/tags",
              "value": {
                "tag1": "value1"
              }
            }
          ]
    ```
    </details>

3. Add a new property `subprop` to `data` block. Note that `parent` must exist. This operation will add `child` under `parent` with the value specified:
    <details><summary>add to data block</summary>
    
    ```
    "ops": [
            {
              "op": "add", 
              "path": "/data/parent/child",
              "value": {
                "grandchild": {
                  "key": "value"
                }
              }
            }
          ]
    ```
    </details>

### Replace Operation
The `replace` operation is fairly straightforward, it replaces the value at the target location with a new value.
1. Replace `/acl/owners` array for a record.
    <details><summary>replace acl owners</summary>
    
    ```
    "ops": [
            { 
              "op": "replace", 
              "path": "/acl/owners",
              "value": [
                "newacl1",
                "newacl2"
              ]
            }
          ]
    ```
    </details>

### Remove Operation
The `remove` operation removes the value at the target location. The field `value` must not be provided for this operation.
1. Remove `/data/parent/child` from the data block
    <details><summary>remove data property</summary>
    
    ```
    "ops": [
            { 
              "op": "remove", 
              "path": "/data/parent/child"
            }
          ]
    ```
    </details>

2. Remove the first value from `/acl/viewers` array
    <details><summary>remove first acl viewer</summary>
    
    ```
    "ops": [
            { 
              "op": "remove", 
              "path": "/acl/viewers/0"
            }
          ]
    ```
    </details>


Below is a complete sample curl which performs multiple operations on a list of record IDs.
<details><summary>complete curl example</summary>

```
curl --request PATCH \
   --url '/api/storage/v2/records' \
   --header 'accept: application/json' \
   --header 'authorization: Bearer <JWT>' \
   --header 'content-type: application/json-patch+json'\
   --header 'Data-Partition-Id: common'
    --data-raw ‘{ 
      "query": { 
        "ids": [
          "tenant1:type:unique-identifier",
          "tenant2:type:unique-identifier",
          "tenant3:type:unique-identifier"
        ]
      }, 
      "ops": [ 
        {
          "op": "remove", 
          "path": "/legal/legaltags/0"
        }, 
        { 
	      "op": "remove", 
	      "path": "/ancestry/parents"
        }, 
        { 
          "op": "add", 
          "path": "/acl/viewers/-",
          "value": "data.default.viewer1@opendes.enterprisedata.cloud.slb-ds.com"
        },
        {
          "op":"replace",
          "path":"/kind",
          "value":"newKind"
        },
        {
          "op":"add",
          "path":"/tags",
          "value":{
            "tag1":"value1",
            "tag2":"value2"
          }
        },
        {
          "op":"replace",
          "path":"/data/someProperty/targetProperty",
          "value": { 
            "newValue": {
              "subProperty":"subValue"
            }
         }
        }
      ] 
    }
```
</details>

### Differences compared to metadata update api

|                             | Metadata Update API                                                                                                                                                                                  | Patch API                                           |
|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| Header `Content-Type`       | application/json                                                                                                                                                                                     | application/json-patch+json                         |
| Supported Record properties | acl, tags, legaltags                                                                                                                                                                                 | acl, tags, legaltags, ancestry, kind, data, meta    |
| `ops` field in payload      | array of [PatchOperation](https://community.opengroup.org/osdu/platform/system/lib/core/os-core-common/-/blob/master/src/main/java/org/opengroup/osdu/core/common/model/storage/PatchOperation.java) | [JsonPatch](https://www.rfc-editor.org/rfc/rfc6902) |
| Maximum number of records   | 500                                                                                                                                                                                                  | 100                                                 |


## Using service accounts to access Storage APIs
The Storage service relies on the Google native data access authorization mechanisms to provide access control on the records. 
Based on design decisions, when the Storage service caller is a federated user, no additional configuration is necessary, however if the API caller is a service account, a mandatory configuration is necessary as follows:

- Navigate to the GCP project which the caller service account belongs to;
- Go to IAM & admin > service accounts;
- Select the caller service account;
- In the right-hand side Permissions panel, click at "Add member" button;
- In the member text box add the following email ``{DATA_ECOSYSTEM_PROJECT}@appspot.gserviceaccount.com``. For instance, in P4D enviroment the member email is ``p4d-ddl-eu-services@appspot.gserviceaccount.com``;
- Select the role ``Service Accounts`` > ``Service Account Token Creator``.

## Using skipdupes
The skipdupes param is only related to update operations, which means you are calling the API with record IDs already present into the Data Ecosystem. If skipdupes==true, it means the service will not update the record if the payload is the same (duplicates). 
If there is a difference in the payload, then a new version of the record will be created. On the other hand, skipdupes == false, in an update operation, the service will not check whether the payload is the same or not and will always create a new version, even if identical to a previous version. On the response side, skipedRecordIds are the record IDs which weren't updated (skipped) due skipdupes == true and same payload. 
In PUT response, there will be no more replication in the record IDs, they will be in either recordIds or skippedRecordIds.

## Support for GeoJSON types
Storage service can now ingest records of type [GeoJson](https://geojson.org/). Following are some examples of the `data` block which can be used to ingest records of type GeoJSON using the PUT api.
```
"data": {
  "WellName": "Data Platform Services - 51",
  "GeoShape": {
    "type": "Point",
    "coordinates": [
      -105.01621,
      39.57422
    ]
  }
}
```
```
"data": {
  "WellName": "Data Platform Services - 53",
  "GeoShape": {
    "type": "LineString",
    "coordinates": [
      [
        -101.744384,
        39.32155
      ],
      [
        -101.552124,
        39.330048
      ],
      [
        -101.403808,
        39.330048
      ]
    ]
  }
}
```
```
"data": {
  "WellName": "Data Platform Services - 55",
  "GeoShape": {
    "type": "Polygon",
    "coordinates": [
      [
        [
          100,
          0
        ],
        [
          101,
          0
        ],
        [
          101,
          1
        ],
        [
          100,
          1
        ],
        [
          100,
          0
        ]
      ]
    ]
  }
}
```
Similarly, data of type MultiPoint, MultiLineString, MultiPolygon, GeometryCollection are also supported.

## Version info endpoint
For deployment available public `/info` endpoint, which provides build and git related information.
#### Example response:
```json
{
    "groupId": "org.opengroup.osdu",
    "artifactId": "storage-gcp",
    "version": "0.10.0-SNAPSHOT",
    "buildTime": "2021-07-09T14:29:51.584Z",
    "branch": "feature/GONRG-2681_Build_info",
    "commitId": "7777",
    "commitMessage": "Added copyright to version info properties file",
    "connectedOuterServices": [
      {
        "name": "elasticSearch",
        "version":"..."
      },
      {
        "name": "postgresSql",
        "version":"..."
      },
      {
        "name": "redis",
        "version":"..."
      }
    ]
}
```
This endpoint takes information from files, generated by `spring-boot-maven-plugin`,
`git-commit-id-plugin` plugins. Need to specify paths for generated files to matching
properties:
- `version.info.buildPropertiesPath`
- `version.info.gitPropertiesPath`

## Using Storage APIs in the Collaboration context
Query, Records and Patch API can also be used in the [Collaboration context](CollaborationContext.md)
