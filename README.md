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
| `GET` | `/api/v1/metrics/{resourceId}/peak-config` | Peak/off-peak schedule and utilization targets |
| `GET` | `/api/v1/recommendations/{resourceId}` | Scaling recommendations |
| `POST` | `/api/v1/recommendations/generate` | Generate KEDA YAML / Terraform HCL |
| `GET` | `/api/v1/costs/analysis` | Full cost analysis with savings breakdown |
| `POST` | `/api/v1/metrics/{resourceId}/snapshot?start={instant}&end={instant}` | Export a metrics snapshot to the configured snapshot store |

### OpenAPI / Swagger UI

The API spec is auto-generated on every startup from controllers and models.

| Path | Description |
|------|-------------|
| `/v3/api-docs` | OpenAPI 3.0 JSON spec |
| `/swagger-ui.html` | Interactive Swagger UI |

## How the Application Works

The application follows a repository-service-controller flow:

1. **Metrics are collected.** The backend receives a resource ID and time range from the metrics or recommendations API. `MetricsCollectionService` asks the active `MetricsRepository` for CPU, memory, and request metrics, merges the data points, and calculates aggregate statistics. The mock profile uses generated data or a downloaded snapshot replay. Azure and Insights profiles query Azure Monitor data through their repository implementations.
2. **Peak and off-peak periods are classified.** Each metric timestamp is interpreted in UTC. The resource's `PeakHoursConfig` defines the peak start/end times, peak days, utilization targets, and cooldown. A point is classified as peak only when both its ISO day-of-week and time fall inside the configured window. Windows that cross midnight are supported; all other points are off-peak.
3. **Recommendations are evaluated.** `AnalysisService` requires samples in both buckets, a peak utilization above the configured peak target, and off-peak utilization below its target. It estimates off-peak schedule savings using the configured weekly time fraction, calculates a bounded confidence estimate from sample coverage and utilization separation, and includes the current resource configuration when available.
4. **Costs are estimated.** `CostEstimateService` supplies a monthly baseline. Kubernetes estimates use discovered node CPU capacity and node count when available; otherwise the resource-type fallback estimate is used. `CostOptimizationService` applies recommendation savings with non-negative cost guardrails and returns a resource-by-resource and aggregate analysis.
5. **Deployment code is generated.** `POST /api/v1/recommendations/generate` re-runs the analysis with optional schedule overrides. Kubernetes recommendations produce a KEDA `ScaledObject` YAML document. Azure App Service recommendations produce an Azure Monitor autoscale Terraform setting targeting an App Service Plan. Other supported resource types use the generic Terraform autoscale template.

The generated configuration is an implementation starting point: review resource names, namespaces, Azure resource references, capacity limits, target thresholds, and schedule assumptions before applying it to production.

## Profiles

| Profile | Description |
|---------|-------------|
| `mock` | Default. Local metrics source with snapshot replay or synthetic fallback data. No Azure access or cloud cost. |
| `azure` | Real Azure Monitor integration (e.g. Azure App Service). Requires service principal credentials (see below). |
| `insights` | Real cluster metrics from Azure Monitor Container Insights (Log Analytics). Requires service principal credentials + Log Analytics Reader on the workspace (see below). |

### Mock Profile

The mock profile runs without Azure credentials and exercises the same metrics,
classification, recommendation, cost-analysis, and code-generation pipeline as
the cloud-backed profiles.

By default, it monitors the `nginx-busy` Kubernetes resource:

```yaml
app:
  mock:
    kubernetes-id: nginx-busy
```

Its peak schedule is configured in UTC:

- Peak: Monday-Friday, `05:00-16:00`
- Peak CPU target: `50%`
- Off-peak CPU target: `18%`
- Default scaling cooldown: `15` minutes

When `data/metrics/<resource-id>.json` exists, the repository loads that
snapshot at startup and replays it for requested time ranges. Replay windows
follow the current wall-clock time mapped onto the snapshot day, tile shorter
snapshots across longer ranges, downsample long ranges, render weekends as idle,
and add a small `7-15%` CPU baseline to low-CPU weekday samples so charts do
not appear as misleading flat zeroes.

If no snapshot is available, the repository generates local sine-wave CPU,
memory, and request metrics. The generated workload is higher during weekday
daytime hours and lower outside that window. The mock repository also supplies
sample current Kubernetes configuration, including replicas, CPU/memory
requests and limits, node count, and node capacity.

Snapshots can be exported through:

```text
POST /api/v1/metrics/{resourceId}/snapshot?start={instant}&end={instant}
```

The exported snapshot is written to the configured `app.snapshot.dir`
(default: `data/metrics`) and is replayed by the mock profile after restart.

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
│   └── service/          # Metrics collection, analysis, costs, recommendations, code generation
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

Key backend components:

| Component | Responsibility |
|-----------|----------------|
| `MetricsController` | Exposes resource metrics and peak configuration endpoints |
| `RecommendationsController` | Evaluates recommendations and generates KEDA/Terraform output |
| `CostController` | Returns aggregate cost optimization analysis |
| `SnapshotController` | Uploads, downloads, and deletes mock metrics snapshots |
| `MetricsCollectionService` | Collects data and assigns peak/off-peak buckets |
| `AnalysisService` | Computes recommendation eligibility, savings, confidence, and rationale |
| `CostEstimateService` | Estimates monthly resource costs |
| `CostOptimizationService` | Builds the cost analysis across monitored resources |
| `CodeGenerationService` | Renders KEDA YAML and Terraform HCL |
