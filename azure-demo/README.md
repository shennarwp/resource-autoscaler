# Azure Demo Apps

Two minimal Node.js apps for testing the resource autoscaler with real Azure metrics.

- **busy-app** — burns CPU on every request (simulates a busy workload)
- **idle-app** — returns immediately (simulates an idle resource)

## Prerequisites

- [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli) installed
- Active Azure subscription with Free tier App Service quota available

## Setup

### 1. Login to Azure

```bash
az login
```

### 2. Create Resource Group

```bash
az group create --name autoscaler-demo --location eastus
```

### 3. Create App Service Plan (Free Tier)

```bash
az appservice plan create --name demo-plan --resource-group autoscaler-demo --sku FREE
```

> If Free tier quota is exhausted in your region, try `--location northeurope` or request a quota increase under Subscriptions > Usage + quotas.

### 4. Create Web Apps

```bash
az webapp create --name autoscaler-busy --resource-group autoscaler-demo --plan demo-plan --runtime "NODE:24-lts"

az webapp create --name autoscaler-idle --resource-group autoscaler-demo --plan demo-plan --runtime "NODE:24-lts"
```

### 5. Set Bearer Token

```bash
TOKEN=$(openssl rand -hex 16)

az webapp config appsettings set --name autoscaler-busy --resource-group autoscaler-demo --settings BEARER_TOKEN=$TOKEN
az webapp config appsettings set --name autoscaler-idle --resource-group autoscaler-demo --settings BEARER_TOKEN=$TOKEN

echo "Save this token: $TOKEN"
```

### 6. Deploy Apps

```bash
cd azure-demo

# Deploy busy-app
cd busy-app && zip -r ../busy-app.zip . && cd ..
az webapp deploy --resource-group autoscaler-demo --name autoscaler-busy --src-path busy-app.zip --type zip

# Deploy idle-app
cd idle-app && zip -r ../idle-app.zip . && cd ..
az webapp deploy --resource-group autoscaler-demo --name autoscaler-idle --src-path idle-app.zip --type zip
```

### 7. Verify Deployment

```bash
curl -H "Authorization: Bearer $TOKEN" https://autoscaler-busy.azurewebsites.net
curl -H "Authorization: Bearer $TOKEN" https://autoscaler-idle.azurewebsites.net
```

### 8. Create Service Principal for Autoscaler

```bash
az ad sp create-for-rbac --name resource-autoscaler --role "Monitoring Reader" --scopes /subscriptions/$(az account show --query id -o tsv)
```

Save the output — you'll need it to run the autoscaler with the `azure` profile.

## Generate Traffic

Hit the busy app to generate CPU load (default 1000 requests):

```bash
export TOKEN=your_token_here
./traffic.sh
./traffic.sh 5000  # custom count
```

Repeat every few hours for a day to create a peak/off-peak pattern in Azure Monitor.

## Kubernetes (Arc-connected K3s)

Deploy nginx servers on the Arc-connected K3s cluster (`gpd-micropc-k3s`) — a "busy" one and an "idle" one, like the App Services above:

```bash
export KUBECONFIG=~/.kube/config-gpd
kubectl apply -f k8s/nginx-busy.yaml
```

This creates two deployments + services:
- `nginx-busy` — nginx served on port 80 (generate traffic to make it busy)
- `nginx-idle` — nginx, left idle

Generate traffic to nginx-busy (run from the `azure-demo` folder):

```bash
# Needs cluster access — run from the azure-demo folder
export KUBECONFIG=~/.kube/config-gpd
./k8s/traffic-k8s.sh
```

`traffic-k8s.sh` takes two args: duration in seconds and concurrent connections, e.g. `./k8s/traffic-k8s.sh 600 10` for 10 minutes of load at 10 concurrent connections.

### Sustained load during peak hours

`traffic-scheduler.sh` runs load blocks automatically during the app's peak window (Mon–Fri **07:00–18:00 UTC**, matching the backend's peak-hour stats) and sleeps outside it. Run from the `azure-demo` folder, and leave it running in the background:

```bash
export KUBECONFIG=~/.kube/config-gpd
nohup ./k8s/traffic-scheduler.sh > /tmp/traffic-scheduler.log 2>&1 &
```

- Watch progress: `tail -f /tmp/traffic-scheduler.log`
- Stop it (kills all scheduler instances): `pkill -f traffic-scheduler.sh`
- Use your local timezone instead of UTC: `SCHEDULE_TZ=local nohup ./k8s/traffic-scheduler.sh > /tmp/traffic-scheduler.log 2>&1 &`
- Auto-start on boot via crontab: `@reboot /path/to/azure-demo/k8s/traffic-scheduler.sh >> /tmp/traffic-scheduler.log 2>&1`

Overridable env vars: `SCHEDULE_TZ` (`utc`|`local`), `SCHEDULE_FROM` (default 7), `SCHEDULE_TO` (default 18, exclusive), `RUN_DURATION` (default 600s per load block), `IDLE_SECONDS` (default 300s gap between blocks).

## Endpoints

- Busy: https://autoscaler-busy.azurewebsites.net
- Idle: https://autoscaler-idle.azurewebsites.net
- nginx inside cluster: `http://nginx-busy` / `http://nginx-idle` (cluster-local)

## Next Steps

Once metrics are collected (wait 1-2 days), the resource-autoscaler can query Azure Monitor and generate real scaling recommendations for these apps.
