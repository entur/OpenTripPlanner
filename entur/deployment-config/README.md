# otp-deployment-config


## Apigee

### Deploy proxy to dev

```bash
apigeetool deployproxy -V -o entur -e dev -n journey-planner-v3 -d apigee/journey-planner-v3 -u $APIGEEUSER -p $APIGEEPASSWORD
```

### Upgrade staging to dev revision

To get dev revision:

```bash
apigeetool listdeployments -V -u $APIGEEUSER -p $APIGEEPASSWORD  -o entur -n journey-planner-v3 -j | jq '.deployments[] | select(.environment |contains("dev")) |.revision'
```

To deploy dev revision to staging:

```bash
revision=$(apigeetool listdeployments -u $APIGEEUSER -p $APIGEEPASSWORD  -o entur -n journey-planner-v3 -j | jq '.deployments[] | select(.environment |contains("dev")) |.revision') && \
apigeetool deployExistingRevision -V -u $APIGEEUSER -p $APIGEEPASSWORD -o entur -e stage  -n journey-planner-v3 -r $revision
```

### Upgrade production to staging revision

To deploy staging revision to prod:

```bash
revision=$(apigeetool listdeployments -u $APIGEEUSER -p $APIGEEPASSWORD  -o entur -n journey-planner-v3 -j | jq '.deployments[] | select(.environment |contains("stage")) |.revision') && \
apigeetool deployExistingRevision -V -u $APIGEEUSER -p $APIGEEPASSWORD -o entur -e prod  -n journey-planner-v3 -r $revision
```

## Test config

You can test the config variable substitution locally by running:
```bash
cd helm/otp2
helm template . --values=env/values-kub-ent-prd.yaml --set horizontalPodAutoscaler.enabled=false  > test.yaml
```

# Zero-downtime deployment and graceful shutdown 
Rolling out new versions of OTP or scaling down the deployment without impacting OTP clients requires a zero-downtime configuration:
- new requests should not be sent to pods that are shutting down,
- pods that are shutting down should not stop before they have serviced all in-flight requests.

## Pre-stop hook
When a pod is removed, the Kubernetes API sends simultaneously:
- a SIGTERM signal to the pod so that the pod initiates a graceful shutdown.
- a notification to the Kubernetes service to remove the pod IP from its IP table. In turns, Traefik checks the IP table and stops sending traffic to the removed pod.

However, these two events are processed concurrently and there is no guarantee that one is performed before the other.  
If the pod is stopped too early, while Traefik continues to send requests to its IP address, then the client will receive an HTTP error (most likely `503 Service Unavailable`)  
To make sure that a pod does not initiate its graceful shutdown before it is deregistered by Traefik, it is necessary to define a pre-stop hook (`preStop`) on both containers (OTP proxy and OTP) with a sleep period.  
The SIGTERM will be sent to the pod only after the sleep period has completed.  
This sleep period should be long enough to allow Traefik to deregister the pod. We use a sleep period of 15 seconds (various sources report that a value of 5-10 seconds is sufficient). 


## Termination grace period
Once OTP and OTP Proxy have received a SIGTERM, they initiate a graceful shutdown: The remaining in-flight requests are processed, and then the containers stop.  
The termination grace period (`terminationGracePeriodSeconds`) for each container should be long enough to prevent Kubernetes from stopping the pod before the remaining requests are processed.  
The termination grace period starts when the pre-stop hook is invoked, and it should cover the maximum duration of an OTP request, which is defined by the OTP configuration parameter `apiProcessingTimeout`:  
```terminationGracePeriodSeconds > pre-stop sleep period + apiProcessingTimeout```  
There is no performance benefit in optimizing (that is: reducing) the termination grace period, and it can be configured with a good margin. We use a value of around 60s.

