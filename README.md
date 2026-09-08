# Resource Autoscaler

Cloud Cost & FinOps Optimization Platform — monitors infrastructure resource utilization, detects over-provisioned systems during off-peak hours, and generates schedule-based autoscaling configurations.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 25, Spring Boot 4.1, Maven |
| Frontend | React 19, TypeScript 6, Vite 8, Recharts |
| Cloud | Azure Monitor, AKS, Terraform |
| Infra | Docker, Docker Compose |

## Prerequisites

- Java 25+
- Maven 3.9+ (or use `./mvnw`)
- Node.js 24+ / npm 12+
- Docker & Docker Compose (optional)

## Getting Started

### Backend

```bash
# Run with mock profile (default)
./mvnw spring-boot:run

# Or build and run
./mvnw clean package -DskipTests
java -jar target/resource-autoscaler-0.1.0-SNAPSHOT.jar
```

API starts at `http://localhost:8080`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Dashboard at `http://localhost:5173` (proxies `/api` to backend).

### Docker

```bash
docker compose up --build
```

Frontend at `http://localhost:80`, backend at `http://localhost:8080`.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/metrics` | List monitored resources |
| `GET` | `/api/v1/metrics/{resourceId}?days=30` | Resource metrics + aggregated stats |
| `GET` | `/api/v1/recommendations/{resourceId}` | Scaling recommendations |
| `POST` | `/api/v1/recommendations/generate` | Generate KEDA YAML / Terraform HCL |
| `GET` | `/api/v1/costs/analysis` | Full cost analysis with savings breakdown |

### OpenAPI / Swagger UI

The API spec is auto-generated on every startup from controllers and models.

| Path | Description |
|------|-------------|
| `/v3/api-docs` | OpenAPI 3.0 JSON spec |
| `/swagger-ui.html` | Interactive Swagger UI |

## Profiles

| Profile | Description |
|---------|-------------|
| `mock` | Default. Synthetic sine-wave data (peak 07:00-18:00, off-peak low). Zero cloud cost. |
| `azure` | Real Azure Monitor integration (e.g. Azure App Service). Requires service principal credentials (see below). |
| `insights` | Real cluster metrics from Azure Monitor Container Insights (Log Analytics). Requires service principal credentials + Log Analytics Reader on the workspace (see below). |

### Azure Profile Setup

1. Create a service principal:

```bash
az ad sp create-for-rbac --name resource-autoscaler --role "Monitoring Reader" --scopes /subscriptions/{subscription-id}
```

2. Set environment variables:

```bash
export AZURE_SUBSCRIPTION_ID=your_subscription_id
export AZURE_TENANT_ID=your_tenant_id
export AZURE_CLIENT_ID=your_client_id
export AZURE_CLIENT_SECRET=your_client_secret
export AZURE_RESOURCE_GROUP=autoscaler-demo
```

3. Run with azure profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=azure
```

### Insights Profile Setup

Monitors Kubernetes deployments on an Azure Arc cluster via Container Insights. CPU/memory are read from the `Perf` (`K8SContainer`) table and joined to `KubePodInventory` to scope metrics to each deployment (`nginx-busy`, `nginx-idle`).

```bash
export AZURE_LOG_ANALYTICS_WORKSPACE_ID=your_log_analytics_workspace_id
```

The service principal needs the **Log Analytics Reader** role on the workspace:

```bash
az role assignment create \
  --assignee $AZURE_CLIENT_ID \
  --role "Log Analytics Reader" \
  --scope /subscriptions/{subscription-id}/resourceGroups/{rg}/providers/Microsoft.OperationalInsights/workspaces/{workspace-id}
```

Run:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=insights
```

## Project Structure

```
.
├── src/main/java/com/resourceautoscaler/
│   ├── config/           # CORS, Jackson, OpenAPI config
│   ├── controller/       # REST endpoints
│   ├── dto/              # Request/response DTOs
│   ├── model/            # Domain records
│   ├── repository/       # MetricsRepository + MockMetricsRepository + AzureMetricsRepository
│   └── service/          # Analysis, cost optimization, code generation
├── src/main/resources/
│   ├── application.yml
│   ├── application-mock.yml
│   └── application-azure.yml
├── frontend/
│   └── src/
│       ├── pages/        # Dashboard, ResourceDetail, CostAnalysis, GenerateCode
│       ├── services/     # Axios API client
│       ├── hooks/        # React data-fetching hooks
│       └── types/        # TypeScript interfaces
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```
