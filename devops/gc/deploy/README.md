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

| Name                     | Description                                             | Type    | Default | Required |
|--------------------------|---------------------------------------------------------|---------|---------|----------|
| **global.domain**        | your domain for the external endpoint, ex `example.com` | string  | -       | yes      |
| **global.limitsEnabled** | whether CPU and memory limits are enabled               | boolean | `true`  | yes      |
| **global.logLevel**      | severity of logging level                               | string  | `ERROR` | yes      |
| **global.tier**            | tier defines the number of replicas for the service to ensure the service HA           | string  | ""      | no       |
| **global.autoscalingMode**     | enables horizontal pod autoscaling on cluster spot nodes; values are `none`, `cpu`, `requests`      | boolean | true    | yes      |

### Configmap variables

| Name                                | Description                                                                                  | Type   | Default                 | Required                                        |
|-------------------------------------|----------------------------------------------------------------------------------------------|--------|-------------------------|-------------------------------------------------|
| **data.logLevel**                   | logging severity level for this service only                                                 | string | -                       | yes, only if differs from the `global.logLevel` |
| **data.defaultDataCountry**         | Data storage region                                                                          | string | `US`                    | yes                                             |
| **data.storageServiceAccountEmail** | Storage service account email, used during OQM events processing                             | string | `storage@service.local` | yes                                             |
| **data.entitlementsHost**           | Entitlements service host address                                                            | string | `http://entitlements`   | yes                                             |
| **data.partitionHost**              | Partition service host address                                                               | string | `http://partition`      | yes                                             |
| **data.crsConverterHost**           | CRS Converter service host address                                                           | string | `http://crs-conversion` | yes                                             |
| **data.legalHost**                  | Legal service host address                                                                   | string | `http://legal`          | yes                                             |
| **data.opaEndpoint**                | OPA host address                                                                             | string | `http://opa`            | yes                                             |
| **data.redisStorageHost**           | The host for redis instance. If empty (by default), helm installs an internal redis instance | string | -                       | yes                                             |
| **data.redisStoragePort**           | The port for redis instance                                                                  | digit  | `6379`                  | yes                                             |

### Deployment variables

| Name                        | Description                        | Type   | Default        | Required                               |
|-----------------------------|------------------------------------|--------|----------------|----------------------------------------|
| **data.requestsCpu**        | amount of requested CPU            | string | `10m`          | yes                                    |
| **data.requestsMemory**     | amount of requested memory         | string | `650Mi`        | yes                                    |
| **data.limitsCpu**          | CPU limit                          | string | `1`            | only if `global.limitsEnabled` is true |
| **data.limitsMemory**       | memory limit                       | string | `3G`           | only if `global.limitsEnabled` is true |
| **data.image**              | path to the image in a registry    | string | -              | yes                                    |
| **data.imagePullPolicy**    | when to pull the image             | string | `IfNotPresent` | yes                                    |
| **data.serviceAccountName** | name of kubernetes service account | string | `storage`      | yes                                    |
| **data.redisImage**         | service image                      | string | `redis:7`      | yes                                    |
**data.affinityLabelsSpot** | labels with possible values, used to correctly setup the node affinities for spot deployment | object | cloud.google.com/gke-provisioning: [spot] | only if global.autoscaling is true
**data.affinityLabelsStandard** | labels with possible values, used to correctly setup the node affinities for standard deployment | object | cloud.google.com/gke-provisioning: [standard] | only if global.autoscaling is true

### Configuration variables

| Name                            | Description                                                           | Type    | Default                | Required |
|---------------------------------|-----------------------------------------------------------------------|---------|------------------------|----------|
| **conf.appName**                | Service name                                                          | string  | `storage`              | yes      |
| **conf.storageRedisSecretName** | secret for redis that contains redis password with REDIS_PASSWORD key | string  | `storage-redis-secret` | yes      |
| **conf.replicas**               | Number of replicas                                                    | integer | `3`                    | yes      |

### Istio variables

| Name                       | Description                       | Type   | Default | Required |
|----------------------------|-----------------------------------|--------|---------|----------|
| **istio.proxyCPU**         | CPU request for Envoy sidecars    | string | `10m`   | yes      |
| **istio.proxyCPULimit**    | CPU limit for Envoy sidecars      | string | `200m`  | yes      |
| **istio.proxyMemory**      | memory request for Envoy sidecars | string | `100Mi` | yes      |
| **istio.proxyMemoryLimit** | memory limit for Envoy sidecars   | string | `256Mi` | yes      |

### Horizontal Pod Autoscaling (HPA) variables (works only if tier=PROD and autoscaling=true)

| Name                                                 | Description                                                                   | Type    | Default          | Required                                                                          |
|------------------------------------------------------|-------------------------------------------------------------------------------|---------|------------------|-----------------------------------------------------------------------------------|
| **hpa.minReplicas**                                  | minimum number of replicas                                                    | integer | `1`              | used only if `global.autoscalingMode` is not `none` and `global.tier` is "" (nil) |
| **hpa.maxReplicas**                                  | maximum number of replicas                                                    | integer | `6`              | used only if `global.autoscalingMode` is not `none` and `global.tier` is "" (nil) |
| **CPU based scaling**                                | **Enabled when `global.autoscalingMode` is cpu**                              |         |                  |     |
| **hpa.cpu.utilization**                              | the maximum number of new replicas to create (in percents from current state) | integer | `200`            | yes |
| **hpa.cpu.scaleUpStabilizationWindowSeconds**        | time to start implementing the scale up when it is triggered                  | integer | `30`             | yes |
| **hpa.cpu.scaleUpValue**                             | the maximum number of new replicas to create (in percents from current state) | integer | `200`            | yes |
| **hpa.cpu.scaleUpPeriod**                            | pause for every new scale up decision                                         | integer | `15`             | yes |
| **hpa.cpu.scaleDownStabilizationWindowSeconds**      | time to start implementing the scale down when it is triggered                | integer | `150`            | yes |
| **hpa.cpu.scaleDownValue**                           | the maximum number of replicas to destroy (in percents from current state)    | integer | `100`            | yes |
| **hpa.cpu.scaleDownPeriod**                          | pause for every new scale down decision                                       | integer | `15`             | yes |
| **REQUESTS based scaling**                           |  **Enabled when `global.autoscalingMode` is requests**                        |         |                  | **Requests based autoscaling uses Prometheus metrics. Prometheus should be installed in your cluster!**    |
| **hpa.requests.targetType**                          | type of measurements: AverageValue or Value                                   | string  | `"AverageValue"` | yes |
| **hpa.requests.targetValue**                         | threshold value to trigger the scaling up                                     | integer | `40`             | yes |
| **hpa.requests.scaleUpStabilizationWindowSeconds**   | time to start implementing the scale up when it is triggered                  | integer | `10`             | yes |
| **hpa.requests.scaleUpValue**                        | the maximum number of new replicas to create (in percents from current state) | integer | `50`             | yes |
| **hpa.requests.scaleUpPeriod**                       | pause for every new scale up decision                                         | integer | `15`             | yes |
| **hpa.requests.scaleDownStabilizationWindowSeconds** | time to start implementing the scale down when it is triggered                | integer | `60`             | yes |
| **hpa.requests.scaleDownValue**                      | the maximum number of replicas to destroy (in percents from current state)    | integer | `25`             | yes |
| **hpa.requests.scaleDownPeriod**                     | pause for every new scale down decision                                       | integer | `60`             | yes |

### Limits variables

| Name                     | Description                                     | Type    | Default | Required                                       |
|--------------------------|-------------------------------------------------|---------|---------|------------------------------------------------|
| **limits.maxTokens**     | maximum number of requests per fillInterval     | integer | `30`    | only if `global.autoscalingMode` is `requests` |
| **limits.tokensPerFill** | number of new tokens allowed every fillInterval | integer | `30`    | only if `global.autoscalingMode` is `requests` |
| **limits.fillInterval**  | time interval                                   | string  | `"1s"`  | only if `global.autoscalingMode` is `requests` |

### Autoscaling

By default, autoscaling configured for deployments targeting spot nodes. Pods will attempt to schedule on nodes with specific labels indicating they are spot instances. To adjust how pods are scheduled, you can update the data.affinityLabelsSpot for your spot deployments and data.affinityLabelsStandard for your standard deployments in your values.yaml file
Example:

```yml
data:
  affinityLabelsSpot:
    mylabel:
      - value1
      - test
    newLabel:
      - newValue
  affinityLabelsStandard:
    standardLabel:
      - labelValue

```

Each label, along with its values, will be translated into a separate `- matchExpressions` block within the `nodeAffinity` section of your deployment. This configuration operates with OR logic, meaning pods will be scheduled on any node that possesses at least one of the specified labels with one of its defined values.

The chart uses the global.autoscaling parameter in your `values.yaml` to control how autoscaling behaves. This parameter accepts three possible string values:

- **cpu** (default): Autoscaling is enabled and is based on CPU utilization. This is the default setting.
- **requests**: Autoscaling is enabled and is based on resource requests (custom metrics). To enable this, you must also set your global.tier to PROD. **NOTE**: Prometheus should be installed in your cluster, custom metrics used for this type of autoscaling.
- **none**: Autoscaling is entirely disabled for the application. Setting `global.autoscaling` to **none** also prevents the creation of the spot deployment.

### Methodology for Parameter Calculation variables: **hpa.requests.targetValue**, **limits.maxTokens** and **limits.tokensPerFill**

The parameters **hpa.requests.targetValue**, **limits.maxTokens** and **limits.tokensPerFill** were determined through empirical testing during load testing. These tests were conducted using the N2D machine series, which can run on either AMD EPYC Milan or AMD EPYC Rome processors. The values were fine-tuned to ensure optimal performance under typical workloads.

### Recommendations for New Instance Types

When changing the instance type to a newer generation, such as the C3D series, it is essential to conduct new load testing. This ensures the parameters are recalibrated to match the performance characteristics of the new processor architecture, optimizing resource utilization and maintaining application stability.

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
