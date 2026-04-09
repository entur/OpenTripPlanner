## Introduction

This chart will setup the `otp2` app, and it's dependencies in a given namespace.

Note: We do not deploy app manually we use Harness to deploy
## Installing the Chart

### Print template

To see the template output before deploying:
```bash
$ helm template DIRNAME --name RELEASENAME  --values VALUES.YAML --namespace NAMESPACE
```

For example:
```bash
$ helm template helm/OpenTripPlanner/ --name otp  --values helm/OpenTripPlanner/dev-c2-values.yaml --namespace dev
```

### Install chart

To install the chart with the release name `my-release`:

```bash
$ helm install DIRNAME --name RELEASENAME --namespace NAMESPACE
```
Watch the deployment with this command

```bash
$ kubectl get pods -n NAMESPACE
```

## Uninstalling the Chart (WARNING)

To completely remove `my-release`:

```bash
$ helm delete <release-name> --purge
```

## Updating a chart
Run helm upgrade:

```bash
$ helm upgrade --install RELEASENAME DIRNAME --namespace NAMESPACE --values VALUES.YAML
```

for example:
```bash
$ helm upgrade --install otp helm/OpenTripPlanner --namespace dev --values helm/OpenTripPlanner/dev-c2-values.yaml
```

## Helm rollback
Check available revision:
```bash
$ helm history REPONAME
```

Rollback to a revision:
```bash
$ helm rollback REPONAME REVISIONNUMBER
```
