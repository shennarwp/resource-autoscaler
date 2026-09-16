# FIXME — Remaining Recommendations

Items from the project analysis that have not been implemented yet.

## Completed

| # | Recommendation | Notes |
|---|----------------|-------|
| 15 | **CI/CD Pipeline** | GitHub Actions workflow added (`.github/workflows/ci.yml`) for frontend lint/build/test and backend Maven tests on PR/push. Container deploy still pending. |
| 19 | **Input Validation** | `@NotBlank`/`@Size`/`@Min`/`@Max`/`@PositiveOrZero` on `RecommendationRequest`; `@Validated` + `@Pattern` + `@DecimalMin`/`@DecimalMax` on `MetricsController`, `RecommendationsController`, and `SnapshotController`; `GlobalExceptionHandler` now maps `MethodArgumentNotValidException`, `ConstraintViolationException`, and `MethodArgumentTypeMismatchException` to 400 responses; snapshot `start` must precede `end`. |
| 10 | **Frontend Error States & Request Cancellation** | `AbortController` added to all `useApi` hooks; `useCostAnalysis` gains `reload`; Dashboard shows resource-list loading/error states plus a Refresh button; `ResourceDetailPage` surfaces metrics and recommendation fetch errors; clipboard failures in `GenerateCodePage` are surfaced instead of silently swallowed. |
| 13 | **Accessibility** | Skip-to-content link with `main` landmark; `:focus-visible` focus indicators; `visually-hidden` text for charts, tables, and live status; time-range buttons use `aria-pressed` + descriptive labels; generated-code tabs follow the `tablist`/`tab`/`tabpanel` pattern with arrow-key navigation and `aria-live` copy feedback; cost tables use caption + `scope`. |

## High Impact

| # | Recommendation | Description | Effort |
|---|----------------|-------------|--------|
| 1 | **Azure Pricing API Integration** | Replace hardcoded cost estimates ($40/core/month) with the [Azure Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-price) for accurate, SKU-level pricing. | High |
| 2 | **Recommendation History & Tracking** | Add a database (PostgreSQL/H2) to persist recommendation history, track which ones were applied, and measure actual savings over time. | High |
| 3 | **Real-time Streaming via SSE** | Add `text/event-stream` endpoints for live metric updates instead of polling. | Medium |
| 4 | **Rightsizing Logic** | The `RIGHTSIZING` and `SHUTDOWN_OFF_HOURS` recommendation types are defined but never generated. Implement actual rightsizing analysis (e.g., suggest reducing CPU requests when consistently underutilized). | Medium |
| 5 | **Authentication & RBAC** | Add Spring Security with JWT or Azure AD B2C. The API is currently wide open. | High |

## Quality & Reliability

| # | Recommendation | Description | Effort |
|---|----------------|-------------|--------|
| 7 | **API Retry/Backoff** | Add Spring Retry with exponential backoff for Azure Monitor API calls (`azure-monitor-query`). | Medium |
| 9 | **Integration Tests** | Boot the full Spring context with `@SpringBootTest` and test the end-to-end controller -> service -> repository flow. | Medium |
| 20 | **Duplicated Dashboard API Logic** | `DashboardPage` calls `metricsApi.getMonitoredResources()` directly via `useEffect` instead of using a shared hook like the other data fetches. | Low |

## UX Improvements

| # | Recommendation | Description | Effort |
|---|----------------|-------------|--------|
| 14 | **Cost Trend Charts** | Show month-over-month cost trends instead of just current snapshots. | Medium |
| 18 | **PWA / Service Worker** | Enable offline support and installability for field engineers. | Medium |

## DevOps & Ops

| # | Recommendation | Description | Effort |
|---|----------------|-------------|--------|
| 16 | **Kubernetes Deployment** | Create Helm charts or Kustomize manifests for deploying the autoscaler itself to AKS. | High |
| 17 | **Structured Logging + Observability** | Add OpenTelemetry for distributed tracing and structured JSON logs. | High |