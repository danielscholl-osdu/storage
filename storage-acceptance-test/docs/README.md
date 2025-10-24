### Running E2E Tests

You will need to have the following environment variables defined.

| name                  | value                                          | description                                    | sensitive? | source |
|-----------------------|------------------------------------------------|------------------------------------------------|------------|--------|
| `ENTITLEMENTS_DOMAIN` | ex`opendes-gc.projects.com`                    | OSDU R2 entitlements domain to run tests under | no         | -      |
| `LEGAL_URL`           | ex`http://localhost:8080/api/legal/v1/`        | Legal API endpoint                             | no         | -      |
| `STORAGE_URL`         | ex`http://localhost:8080/api/storage/v2/`      | Endpoint of storage service                    | no         | -      |
| `TENANT_NAME`         | ex `opendes`                                   | OSDU tenant used for testing                   | no         | --     |
| `ENTITLEMENTS_URL`    | ex`http://localhost:8080/api/entitlements/v2/` | Endpoint of entitlements service               | no         | -      |

Authentication can be provided as OIDC config:

| name                                            | value                                   | description                   | sensitive? | source |
|-------------------------------------------------|-----------------------------------------|-------------------------------|------------|--------|
| `ROOT_USER_OPENID_PROVIDER_CLIENT_ID`           | `********`                              | ROOT_USER Client Id           | yes        | -      |
| `ROOT_USER_OPENID_PROVIDER_CLIENT_SECRET`       | `********`                              | ROOT_USER Client secret       | yes        | -      |
| `NO_ACCESS_USER_OPENID_PROVIDER_CLIENT_ID`      | `********`                              | NO_ACCESS_USER Client Id      | yes        | -      |
| `NO_ACCESS_USER_OPENID_PROVIDER_CLIENT_SECRET`  | `********`                              | NO_ACCESS_USER Client secret  | yes        | -      |
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_ID`     | `********`                              | PRIVILEGED_USER Client Id     | yes        | -      |
| `PRIVILEGED_USER_OPENID_PROVIDER_CLIENT_SECRET` | `********`                              | PRIVILEGED_USER Client secret | yes        | -      |
| `TEST_OPENID_PROVIDER_URL`                      | `https://keycloak.com/auth/realms/osdu` | OpenID provider url           | yes        | -      |

Or tokens can be used directly from env variables:

| name                    | value      | description           | sensitive? | source |
|-------------------------|------------|-----------------------|------------|--------|
| `PRIVILEGED_USER_TOKEN` | `********` | PRIVILEGED_USER Token | yes        | -      |
| `NO_ACCESS_USER_TOKEN`  | `********` | NO_ACCESS_USER Token  | yes        | -      |
| `ROOT_USER_TOKEN`       | `********` | ROOT_USER Token       | yes        | -      |


Feature testing is controlled with the following environment variables:

| name                         | value             | description                                                                    |
|------------------------------|-------------------|--------------------------------------------------------------------------------|
| `TEST_REPLAY_ENABLED`        | `true` OR `false` | Controls Replay API tests.                                                     |
| `COLLABORATION_ENABLED`      | `true` OR `false` | Controls collaboration feature tests.                                          |
| `OPA_INTEGRATION_ENABLED`    | `true` OR `false` | Used to adjust assertions if integration with OPA\Policy enabled\disabled      |
| `EXPOSE_FEATUREFLAG_ENABLED` | `true` OR `false` | Feature flag exposure in /info endpoint. Default to true when missing. Must match the actual value used by the storage service. It cannot be changed dynamically at testing time.               |



**Entitlements configuration for integration accounts**

| PRIVILEGED_USER            | NO_ACCESS_USER            | ROOT_USER                 |
|----------------------------|---------------------------|---------------------------|
| users                      | users                     | users                     |
| service.entitlements.user  | service.entitlements.user | users.data.root           |
| service.entitlements.admin | service.storage.admin     | service.entitlements.user |
| service.storage.admin      |                           | service.storage.viewer    |
| service.storage.creator    |                           | service.storage.admin     |
| service.storage.viewer     |                           | service.legal.viewer      |
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
