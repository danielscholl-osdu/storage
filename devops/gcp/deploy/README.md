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

Before installing deploy Helm chart you need to install [configmap Helm chart](../configmap).
First you need to set variables in **values.yaml** file using any code editor. Some of the values are prefilled, but you need to specify some values as well. You can find more information about them below.

### Common variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**requestsCpu** | amount of requested CPU | string | 0.25 | yes
**requestsMemory** | amount of requested memory| string | 2048M | yes
**limitsCpu** | CPU limit | string | 1 | yes
**limitsMemory** | memory limit | string | 3G | yes
**image** | service image | string | - | yes
**imagePullPolicy** | when to pull image | string | IfNotPresent | yes
**serviceAccountName** | name of your service account | string | storage | yes
**bootstrapImage** | bootstrap image | string | - | yes
**bootstrapServiceAccountName** | service account that will be used for bootstrap | string | - | yes

### Config variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|

**appName** | name of the app | string | `storage` | yes
**configmap** | configmap to be used | string | `storage-config` | yes
**keycloakSecretName** | secret for keycloak | string | `storage-keycloak-secret` | yes
**minioSecretName** | secret for minio | string | `storage-minio-secret` | yes
**postgresSecretName** | secret for postgres | string | `storage-postgres-secret` | yes
**rabbitmqSecretName** | secret for rabbitmq | string | `rabbitmq-secret` | yes
**bootstrapSecretName** | secret for bootstrap to access opendi provider | string | `datafier-secret` | yes
**replicas** | Number of replicas | integer | 3 | yes
**onPremEnabled** | whether on-prem is enabled | boolean | false | yes
**bootstrapEnabled** | whether to enable storage bootstrap (should be enabled also for config chart) | boolean | false | yes
**domain** | your domain | string | - | yes

### Install the helm chart

Run this command from within this directory:

```console
helm install gcp-storage-deploy .
```

## Uninstalling the Chart

To uninstall the helm deployment:

```console
helm uninstall gcp-storage-deploy
```

[Move-to-Top](#deploy-helm-chart)
