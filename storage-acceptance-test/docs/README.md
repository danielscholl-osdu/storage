### Running E2E Tests

You will need to have the following environment variables defined.

| name                      | value                                          | description                                                                                                                      | sensitive? | source                                                       |
|---------------------------|------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------------------------|
| `GROUP_ID`                | ex`opendes-gc.projects.com`                    | OSDU R2 to run tests under                                                                                                       | no         | -                                                            |
| `LEGAL_URL`               | ex`http://localhost:8080/api/legal/v1/`        | Legal API endpoint                                                                                                               | no         | -                                                            |
| `STORAGE_URL`             | ex`http://localhost:8080/api/storage/v2/`      | Endpoint of storage service                                                                                                      | no         | -                                                            |
| `TENANT_NAME`             | ex `opendes`                                   | OSDU tenant used for testing                                                                                                     | no         | --                                                           |
| `ENTITLEMENTS_URL`        | ex`http://localhost:8080/api/entitlements/v2/` | Endpoint of entitlements service                                                                                                 | no         | -                                                            |

Authentication can be provided as OIDC config:

| name                                           | value                                   | description                         | sensitive?                                        | source |
|------------------------------------------------|-----------------------------------------|-------------------------------------|---------------------------------------------------|--------|
| `DATA_ROOT_OPENID_PROVIDER_CLIENT_ID`          | `********`                              | DATA_ROOT_TESTER Client Id          | yes                                               | -      |
| `DATA_ROOT_OPENID_PROVIDER_CLIENT_SECRET`      | `********`                              | DATA_ROOT_TESTER Client secret      | yes                                               | -      |
| `TEST_NO_ACCESS_OPENID_PROVIDER_CLIENT_ID`     | `********`                              | NO_DATA_ACCESS_TESTER Client Id     | yes                                               | -      |
| `TEST_NO_ACCESS_OPENID_PROVIDER_CLIENT_SECRET` | `********`                              | NO_DATA_ACCESS_TESTER Client secret | Client secret for `$NO_ACCESS_INTEGRATION_TESTER` | -      |
| `TEST_OPENID_PROVIDER_CLIENT_ID`               | `********`                              | INTEGRATION_TESTER Client Id        | yes                                               | -      |
| `TEST_OPENID_PROVIDER_CLIENT_SECRET`           | `********`                              | INTEGRATION_TESTER Client secret    | Client secret for `$INTEGRATION_TESTER`           | -      |
| `TEST_OPENID_PROVIDER_URL`                     | `https://keycloak.com/auth/realms/osdu` | OpenID provider url                 | yes                                               | --     |

Or tokens can be used directly from env variables:

| name                       | value      | description                 | sensitive? | source |
|----------------------------|------------|-----------------------------|------------|--------|
| `INTEGRATION_TESTER_TOKEN` | `********` | INTEGRATION_TESTER Token    | yes        | -      |
| `NO_DATA_ACCESS_TOKEN`     | `********` | NO_DATA_ACCESS_TESTER Token | yes        | -      |
| `DATA_ROOT_TOKEN`          | `********` | DATA_ROOT_TESTER Token      | yes        | -      |


Feature testing is controlled with the following environment variables:

| name                      | value             | description                                                               |
|---------------------------|-------------------|---------------------------------------------------------------------------|
| `TEST_REPLAY_ENABLED`     | `true` OR `false` | Controls Replay API tests.                                                |
| `COLLABORATION_ENABLED`   | `true` OR `false` | Controls collaboration feature tests.                                     |
| `OPA_INTEGRATION_ENABLED` | `true` OR `false` | Used to adjust assertions if integration with OPA\Policy enabled\disabled |



**Entitlements configuration for integration accounts**

| INTEGRATION_TESTER         | NO_DATA_ACCESS_TESTER     | DATA_ROOT_TESTER          |
|----------------------------|---------------------------|---------------------------|
| users                      | users                     | users                     |
| service.entitlements.user  | service.entitlements.user | users.data.root           |
| service.entitlements.admin | service.storage.admin     | service.entitlements.user |
| service.storage.admin      |                           | service.storage.viewer    |
| service.storage.creator    |                           |                           |
| service.storage.viewer     |                           |                           |
| service.legal.admin        |                           |                           |
| service.legal.editor       |                           |                           |
| data.test1                 |                           |                           |
| data.integration.test      |                           |                           |

Execute following command to build code and run all the integration tests:

 ```bash
 # Note: this assumes that the environment variables for integration tests as outlined
 #       above are already exported in your environment.
 # build + install integration test core
 $ (cd storage-acceptance-test && mvn clean test)
 ```

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
