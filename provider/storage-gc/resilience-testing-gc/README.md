### Environment variables

These tests are specific to Google Cloud OSDU implementation and test platform resilience in case of data corruption or
any other environment issues.

| name                             | default value                                     | description                   | sensitive? |
|----------------------------------|---------------------------------------------------|-------------------------------|------------|
| `PROJECT_ID`                     | nice-etching-277309                               | Google project id             | no         |
| `OSDU_HOST`                      | https://community.gcp.gnrg-osdu.projects.epam.com | OSDU host                     | no         |
| `TENANT`                         | osdu                                              | Tenant id                     | no         |
| `NAMESPACE`                      | osdu                                              | Google Datastore namespace id | no         |
| `KIND`                           | StorageRecord                                     | Google Datastore kind id      | no         |
| `DATABASE_ID`                    |                                                   | Google Datastore database id  | no         |
| `ENTITLEMENTS_DOMAIN`            | group                                             | Entitlements domain           | no         |
| `LEGAL_TAG`                      | osdu-demo-legaltag                                | existing legal tag            | no         |
| `GOOGLE_APPLICATION_CREDENTIALS` | /path/to/file                                     | Path to GSA key file          | yes        |
