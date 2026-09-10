# Azure Demo Apps

Demo workloads for testing the resource autoscaler with real Azure metrics, split by target platform:

- **`app-service/`** — two Node.js apps (`busy-app`, `idle-app`) deployed to Azure App Service, plus a traffic generator (`traffic.sh`). See [app-service/README.md](app-service/README.md).
- **`k8s/`** — nginx "busy"/"idle" workloads on the Arc-connected K3s cluster, plus load generators (`traffic-k8s.sh`, `traffic-scheduler.sh`). See [k8s/README.md](k8s/README.md).

Once metrics are collected (wait 1-2 days), the resource-autoscaler can query Azure Monitor and generate real scaling recommendations for these apps.