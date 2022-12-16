<!--- Deploy -->

# Deploy helm chart

## Introduction

This chart bootstraps a deployment on a [Kubernetes](https://kubernetes.io) cluster using [Helm](https://helm.sh) package manager.

## Prerequisites

The code was tested on **Kubernetes cluster** (v1.21.11) with **Istio** (1.12.6)
> It is possible to use other versions, but it hasn't been tested

### Operation system

The code works in Debian-based Linux (Debian 10 and Ubuntu 20.04) and Windows WSL 2. Also, it works but is not guaranteed in Google Cloud Shell. All other operating systems, including macOS, are not verified and supported.

### Packages

Packages are only needed for installation from a local computer.

- **HELM** (version: v3.7.1 or higher) [helm](https://helm.sh/docs/intro/install/)
- **Kubectl** (version: v1.21.0 or higher) [kubectl](https://kubernetes.io/docs/tasks/tools/#kubectl)

## Installation

First you need to set variables in **values.yaml** file using any code editor. Some of the values are prefilled, but you need to specify some values as well. You can find more information about them below.

### Configmap variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**logLevel** | logging level | string | `ERROR` | yes
**springProfilesActive** | active spring profile | string | `gcp` | yes
**defaultDataCountry** | Data storage region | string | `US` | yes
**storageServiceAccountEmail** | Storage service account email, used during OQM events processing | string | `storage@service.local` | yes
**entitlementsHost** | Entitlements service host address | string | `http://entitlements` | yes
**partitionHost** | Partition service host address | string | `http://partition` | yes
**crsConverterHost** | CRS Converter service host address | string | `http://crs-conversion` | yes
**legalHost** | Legal service host address | string | `http://legal` | yes
**redisGroupHost** | Redis host for groups | string | `redis-group-master` | yes
**redisStorageHost** | Redis host for storage | string | `redis-storage-master` | yes
**googleAudiences** | Client ID of Google Cloud Credentials, ex `123-abc123.apps.googleusercontent.com` | string | - | yes
**opaEnabled** | whether OPA is enabled | boolean | true | yes
**opaEndpoint** | OPA host address | string | `http://opa` | yes
**storageHost** | Storage service host address | string | `http://storage` | only if `conf.bootstrapEnabled` is true
**defaultLegalTag** | Name of the previously created legal tag (without partition part) | string | `default-data-tag` | only if `conf.bootstrapEnabled` is true
**dataPartitionId** | Data partition id | string | - | only if `conf.bootstrapEnabled` is true

### Deployment variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**requestsCpu** | amount of requested CPU | string | `0.25` | yes
**requestsMemory** | amount of requested memory| string | `1024M` | yes
**limitsCpu** | CPU limit | string | `1` | yes
**limitsMemory** | memory limit | string | `3G` | yes
**image** | path to the image in a registry | string | - | yes
**imagePullPolicy** | when to pull the image | string | `IfNotPresent` | yes
**serviceAccountName** | name of kubernetes service account | string | `storage` | yes
**bootstrapImage** | path to the bootstrap image in a registry | string | - | only if `conf.bootstrapEnabled` is true
**bootstrapServiceAccountName** | name of kubernetes service account that will be used for bootstrap | string | - | only if `conf.bootstrapEnabled` is true

### Configuration variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**appName** | Service name | string | `storage` | yes
**keycloakSecretName** | secret for keycloak | string | `storage-keycloak-secret` | yes
**minioSecretName** | secret for minio | string | `storage-minio-secret` | yes
**postgresSecretName** | secret for postgres | string | `storage-postgres-secret` | yes
**rabbitmqSecretName** | secret for rabbitmq | string | `rabbitmq-secret` | yes
**bootstrapSecretName** | secret for bootstrap to access openid provider | string | `datafier-secret` | only if `conf.bootstrapEnabled` is true
**replicas** | Number of replicas | integer | 3 | yes
**onPremEnabled** | whether on-prem is enabled | boolean | false | yes
**bootstrapEnabled** | whether storage bootstrap is enabled | boolean | false | yes
**domain** | your domain, ex `example.com` | string | - | yes

## Install the Helm chart

Run this command from within this directory:

```console
helm install gcp-storage-deploy .
```

## Uninstall the Helm chart

To uninstall the helm deployment:

```console
helm uninstall gcp-storage-deploy
```

> Do not forget to delete all k8s secrets and PVCs accociated with the Service.

[Move-to-Top](#deploy-helm-chart)
