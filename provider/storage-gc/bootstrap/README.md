# Storage bootstrap

## Description

Bootstrap for storage service uploads sample data to storage service via requests. 
It requires previously created legal tag.
To enable storage bootstrap in helm chars - respective configuration parameters should be enabled in provided values.
Logic for bootstrap is implemented in respective (Python scripts)[./Python-README.md].
Bash part is responsible for providing required environment variables to Python script.
For successful bootstrap several variables should also be provided to Bash script. 
List of required bash variables can be found below.

## Environmental Variables

### Common variables

| Name | Description | Example |
|------|-------------|---------|
**DATA_PARTITION_ID** | data partition id | `osdu`
**MANIFESTS_DIR** | directory containing storage records to upload | `./manifests`
**STORAGE_HOST** | Storage service host | `http://storage`
**DEFAULT_LEGAL_TAG** | Previously created legal tag without `<data_partition_id>` part | `default-legal-tag`

### Anthos Variables

| Name | Description | Example |
|------|-------------|---------|
**OPENID_PROVIDER_URL** | url to access openid provider | `http://keycloak/realms/osdu`
**OPENID_PROVIDER_CLIENT_ID** | client id access openid provider | `keycloak`
**OPENID_PROVIDER_CLIENT_SECRET** | client secret to access openid provider | `p@ssw0rd`

### Hardcoded Variables

Bash script also fills some default environment variables values, that will be later used in Python script.

| Name | Description | Value |
|------|-------------|---------|
**CLOUD_PROVIDER** | provider type | `gcp|anthos`
**ACL_OWNERS** | acl owners group name | `["data.default.owners@${DATA_PARTITION_ID}.group"]`
**ACL_VIEWERS** | acl owners group name | `["data.default.viewers@${DATA_PARTITION_ID}.group"]`

