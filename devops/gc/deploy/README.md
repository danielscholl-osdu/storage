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

### Global variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**global.domain** | your domain for the external endpoint, ex `example.com` | string | - | yes
**global.onPremEnabled** | whether on-prem is enabled | boolean | false | yes

### Configmap variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**data.logLevel** | logging level | string | `ERROR` | yes
**data.springProfilesActive** | active spring profile | string | `gcp` | yes
**data.defaultDataCountry** | Data storage region | string | `US` | yes
**data.storageServiceAccountEmail** | Storage service account email, used during OQM events processing | string | `storage@service.local` | yes
**data.entitlementsHost** | Entitlements service host address | string | `http://entitlements` | yes
**data.partitionHost** | Partition service host address | string | `http://partition` | yes
**data.crsConverterHost** | CRS Converter service host address | string | `http://crs-conversion` | yes
**data.legalHost** | Legal service host address | string | `http://legal` | yes
**data.opaEnabled** | whether OPA is enabled | boolean | false | yes
**data.opaEndpoint** | OPA host address | string | `http://opa` | yes
**data.storageHost** | Storage service host address | string | `http://storage` | only if `conf.bootstrapEnabled` is true
**data.defaultLegalTag** | Name of the previously created legal tag (without partition part) | string | `default-data-tag` | only if `conf.bootstrapEnabled` is true
**data.dataPartitionId** | Data partition id | string | - | only if `conf.bootstrapEnabled` is true
**data.redisStorageHost** | The host for redis instance. If empty (by default), helm installs an internal redis instance | string | - | yes
**data.redisStoragePort** | The port for redis instance | digit | 6379 | yes

### Deployment variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**data.requestsCpu** | amount of requested CPU | string | `10m` | yes
**data.requestsMemory** | amount of requested memory| string | `650Mi` | yes
**data.limitsCpu** | CPU limit | string | `1` | yes
**data.limitsMemory** | memory limit | string | `3G` | yes
**data.image** | path to the image in a registry | string | - | yes
**data.imagePullPolicy** | when to pull the image | string | `IfNotPresent` | yes
**data.serviceAccountName** | name of kubernetes service account | string | `storage` | yes
**data.bootstrapImage** | path to the bootstrap image in a registry | string | - | only if `conf.bootstrapEnabled` is true
**data.bootstrapServiceAccountName** | name of kubernetes service account that will be used for bootstrap | string | - | only if `conf.bootstrapEnabled` is true
**data.redisImage** | service image | string | `redis:7` | yes

### Configuration variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**conf.appName** | Service name | string | `storage` | yes
**conf.keycloakSecretName** | secret for keycloak | string | `storage-keycloak-secret` | yes
**conf.minioSecretName** | secret for minio | string | `storage-minio-secret` | yes
**conf.postgresSecretName** | secret for postgres | string | `storage-postgres-secret` | yes
**conf.rabbitmqSecretName** | secret for rabbitmq | string | `rabbitmq-secret` | yes
**conf.storageRedisSecretName** | secret for redis that contains redis password with REDIS_PASSWORD key | string | `storage-redis-secret` | yes
**conf.bootstrapSecretName** | secret for bootstrap to access openid provider | string | `datafier-secret` | only if `conf.bootstrapEnabled` is true
**conf.replicas** | Number of replicas | integer | 3 | yes
**conf.bootstrapEnabled** | whether storage bootstrap is enabled | boolean | false | yes

### Istio variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**istio.proxyCPU** | CPU request for Envoy sidecars | string | 10m | yes
**istio.proxyCPULimit** | CPU limit for Envoy sidecars | string | 200m | yes
**istio.proxyMemory** | memory request for Envoy sidecars | string | 100Mi | yes
**istio.proxyMemoryLimit** | memory limit for Envoy sidecars | string | 256Mi | yes
**istio.bootstrapProxyCPU** | CPU request for Envoy sidecars | string | 10m | yes
**istio.bootstrapProxyCPULimit** | CPU limit for Envoy sidecars | string | 100m | yes

## Install the Helm chart

Run this command from within this directory:

```console
helm install gc-storage-deploy .
```

## Uninstall the Helm chart

To uninstall the helm deployment:

```console
helm uninstall gc-storage-deploy
```

> Do not forget to delete all k8s secrets and PVCs accociated with the Service.

[Move-to-Top](#deploy-helm-chart)
