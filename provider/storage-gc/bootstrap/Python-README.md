# Environmental Variables

## Common variables
```bash
CLOUD_PROVIDER="anthos|gcp"
MANIFESTS_DIR="</path/to/manifests>"
STORAGE_URL="<https|http>://<storage_host>/api/storage/v2"
DATA_PARTITION_ID="<string>"
ACL_OWNERS='["<owner_group>"]'
ACL_VIEWERS='["<viewer_group>"]'
LEGALTAGS='["<valid_legaltag>"]'
```

## Optional common variables
```bash
THREAD_NUMBER=<integer> # the number of simultaneous connections to Storage; default is 3
BATCH_SIZE=<integer> # the size of Record batch; default is 250
DATA_BRANCH="<string>"  # branch or tag data is got from; default 'master'
```

## Anthos Secrets
```bash
KEYCLOAK_AUTH_URL="<https|http>://<keycloak_host>/auth/realms/<realm>/protocol/openid-connect/token"
KEYCLOAK_CLIENT_ID="client_id"
KEYCLOAK_CLIENT_SECRET="client_secret"
```

## GCP Secrets (if there are **no** default credentails in the environment)
```bash
SA_FILE_PATH=gs://path/to/sa/file
```

# How to run the script

```bash
# after you set all the variables
python3.8 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python bootstrap_data.py
```
