<!--- Configmap -->

# Configmap helm chart

## Introduction

This chart bootstraps a configmap deployment on a [Kubernetes](https://kubernetes.io) cluster using [Helm](https://helm.sh) package manager.

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

### Common variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**logLevel** | logging level | string | INFO | yes
**springProfilesActive** | active spring profile | string | gcp | yes
**defaultDataCountry** | Data storage region | string | US | yes
**storageServiceAccountEmail** | Storage service account email, used during OQM events processing | string | storage@service.local | yes

### Google Cloud variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**entitlementsHost** | entitlements service host address | string | `http://entitlements` | yes
**partitionHost** | partition service host address | string | `http://partition` | yes
**crsConverterHost** | CRS Converter service host address | string | `http://crs-conversion` | yes
**legalHost** | Legal service host address | string | `http://legal` | yes
**redisGroupHost** | Redis host for groups | string | `redis-group-master` | yes
**redisStorageHost** | Redis host for storage | string | `redis-storage-master` | yes
**googleAudiences** | your Google Cloud client ID | string | - | yes

> googleAudiences: If you are connected to Google Cloud console with `gcloud auth application-default login --no-browser` from your terminal, you can get your client_id using the command:

```console
cat ~/.config/gcloud/application_default_credentials.json | grep client_id
```

### Bootstrap variables

This variables can be omitted in case **conf.bootstrapEnabled** is set to `false`.

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**storageHost** | Storage service host address | string | `http://storage` | yes
**defaultLegalTag** | Name of the previously created legal tag (without partition part) | string | `default-data-tag` | yes
**dataPartitionId** | Data partition id | string | `redis-storage-master` | yes

### Config variables

| Name | Description | Type | Default |Required |
|------|-------------|------|---------|---------|
**appName** | name of the app | string | storage | yes
**configmap** | configmap name | string | storage-config | yes
**onPremEnabled** | whether on-prem is enabled | boolean | false | yes
**bootstrapEnabled** | whether to enable storage bootstrap (requires previously created legal tag) | boolean | false | yes

### Install the helm chart

Run this command from within this directory:

```bash
helm install gcp-storage-configmap .
```

## Uninstalling the Chart

To uninstall the helm deployment:

```bash
helm uninstall gcp-storage-configmap
```

[Move-to-Top](#configmap-helm-chart)
