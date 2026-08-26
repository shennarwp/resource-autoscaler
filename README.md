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

## Profiles

| Profile | Description |
|---------|-------------|
| `mock` | Default. Synthetic sine-wave data (peak 07:00-18:00, off-peak low). Zero cloud cost. |
| `azure` | Real Azure Monitor integration. Requires `AZURE_SUBSCRIPTION_ID`, `AZURE_TENANT_ID`. |

## Project Structure

```
.
├── src/main/java/com/resourceautoscaler/
│   ├── config/           # CORS, Jackson config
│   ├── controller/       # REST endpoints
│   ├── dto/              # Request/response DTOs
│   ├── model/            # Domain records
│   ├── repository/       # MetricsRepository + MockMetricsRepository
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
