# App Service Demo Apps

Two minimal Node.js apps for testing the resource autoscaler against **Azure App Service** metrics.

- **busy-app** — burns CPU on every request (simulates a busy workload)
- **idle-app** — returns immediately (simulates an idle resource)
- **traffic.sh** — generates HTTP load against the deployed busy-app

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

From the `azure-demo` folder:

```bash
cd app-service

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

Hit the busy app to generate CPU load (default 1000 requests). From the `azure-demo` folder:

```bash
export TOKEN=your_token_here
./app-service/traffic.sh
./app-service/traffic.sh 5000  # custom count
```

Repeat every few hours for a day to create a peak/off-peak pattern in Azure Monitor.

## Endpoints

- Busy: https://autoscaler-busy.azurewebsites.net
- Idle: https://autoscaler-idle.azurewebsites.net