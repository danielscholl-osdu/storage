# Ingestion Workflow

For sake of demonstration of the schema and records concepts as well as their respective APIs, lets consider the following use case:

> The OSDU developer wants to ingest metadata information related to his/her well dataset. The metadata contains the following pieces of information: name of the well, company name, year when it was drilled, total depth, and the well location.

In summary, to execute the above workflow, the OSDU developer needs to perform the following tasks:

1. Be a valid Data Ecosystem user;
2. Define which partition to use;
3. Create and/or assign users to a existing partition data group;
4. Agree on the __kind__ attribute which will represent the developer's wells. Let's assume it to be ``common:welldb:wellbore:1.0.0``;
5. Create the __legal tag__ that represents the legal constraints for the metadata to be ingested;
6. Create a schema for the kind ``common:welldb:wellbore:1.0.0`` via the [schema service](https://community.opengroup.org/osdu/platform/system/schema-service);
7. Create and ingest records via the ``PUT /api/storage/v2/records`` API.


### Becoming a Data Ecosystem user
Please refer to [Entitlements Service](https://osdu.pages.opengroup.org/platform/security-and-compliance/entitlements/) to learn how to become a valid Data Ecosystem user.


### Choosing a partition 
The Data Ecosystem stores data in different tenants depending on the different accounts in the  system. A user may belong to many accounts in OSDU e.g. a OSDU user may belong to both the OSDU account and a customer's account. 

When using the Storage Service APIs, specify the active account as the ``Data-Partition-Id``. The correct values can be obtained from CFS services. In our Development environment you can choose between ``osdu``, ``customer`` and ``common``;


### Creating data groups
Please refer to [Entitlements Service](https://osdu.pages.opengroup.org/platform/security-and-compliance/entitlements/) to learn how to create data groups (the ones which starts with ``data.``) and assign users to them. For data access authorization purposes in this example, let's assume the groups ``data.default.viewers@common.[osdu.opengroup.org]`` and ``data.default.owners@common.[osdu.opengroup.org]`` were previously created via [Entitlements Service](https://osdu.pages.opengroup.org/platform/security-and-compliance/entitlements/).


### Creating the schema
The schema creation is done via the [schema service](https://osdu.pages.opengroup.org/platform/system/schema-service/).

The schema is basically composed by a list of path/kinds pairs where the record fields are related to their data type. For more information about the supported schema data types, please refer to the [Schema service documentation](https://osdu.pages.opengroup.org/platform/system/schema-service/).

### Creating the legal tag
Please refer to [Legal Service](https://osdu.pages.opengroup.org/platform/security-and-compliance/legal/) for legal tag creation. For this example, let's assume a legal tag called ``osdu-well-legal`` is created already.


### Creating records
After the legal tag creation and schema definition, the records of the kind ``common:welldb:wellbore:1.0.0`` can be created. They need to follow the same structure and fields' naming convention as defined in the schema. A sample record would be something as follows:

<details><summary>curl</summary>

```
{
  "kind": "common:welldb:wellbore:1.0.0",
  "acl": {
    "viewers": ['data.default.viewers@common.[osdu.opengroup.org]'],
    "owners": ['data.default.owners@common.[osdu.opengroup.org]']
  },
  "legal": {
    "legaltags": ['common-sample-legaltag'],
    "otherRelevantDataCountries": ["FR","US","CA"]
  },
  "data": {
    "name": "well1",
    "company": "slb",
    "drillingYear": 1983,
    "depth": 1208.84,
    "location": {
      "latitude": 29.7512026,
      "longitude": -95.4812934
    }
  }
}
```
</details>


### Ingesting records
Having the record structure defined, the OSDU developer must use the ``PUT /api/storage/v2/records'`` API to ingest his/her records, as follows:

<details><summary>curl</summary>

```
curl --request PUT \
  --url '/api/storage/v2/records' \
  --header 'accept: application/json' \
  --header 'authorization: Bearer <JWT>' \
  --header 'content-type: application/json' \
  --header 'Data-Partition-Id: common' \
  --data '[
  {
    "kind": "common:welldb:wellbore:1.0.0",
    "acl": {
      "viewers": ['data.default.viewers@common.[osdu.opengroup.org]'],
      "owners": ['data.default.owners@common.[osdu.opengroup.org]']
    },
    "legal": {
      "legaltags": ['common-sample-legaltag'],
      "otherRelevantDataCountries": ["FR","US","CA"]
    },
    "data": {
      "name": "well1",
      "company": "slb",
      "drillingYear": 1983,
      "depth": 1208.84,
      "location": {
        "latitude": 29.7512026,
        "longitude": -95.4812934
      }
    },
  {
    "kind": "common:welldb:wellbore:1.0.0",
    "acl": {
      "viewers": ['data.default.viewers@common.[osdu.opengroup.org]'],
      "owners": ['data.default.owners@common.[osdu.opengroup.org]']
    },
    "legal": {
      "legaltags": ['common-sample-legaltag'],
      "otherRelevantDataCountries": ["IN","BR","CA"]
    },
    "data": {
      "name": "well12312",
      "company": "shell",
      "drillingYear": 2001,
      "depth": 208.84,
      "location": {
        "latitude": 49.7512026,
        "longitude": -65.4812934
      }
    }
  },
   ...]'
```
</details>
