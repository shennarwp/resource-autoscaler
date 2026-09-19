# FIXME — Remaining Recommendations

This list contains work that is still outstanding after the latest project review. Completed recommendations have been removed.

## High impact

| Recommendation | Description | Effort |
|---|---|---|
| Azure Retail Pricing | Replace fallback estimates with SKU-, region-, currency-, and billing-aware Azure Retail Prices API data. | High |
| Recommendation history | Persist generated, accepted, rejected, applied, and rolled-back recommendations and compare projected vs actual savings. | High |
| Shutdown/off-hours automation | Generate safe `SHUTDOWN_OFF_HOURS` recommendations with minimum availability, exception calendars, and rollback controls. | Medium |
| Live metrics | Add SSE streaming after the polling path is stable and authenticated. | Medium |

## Reliability and security

| Recommendation | Description | Effort |
|---|---|---|
| Distributed rate limiting | Replace the in-process limiter with Redis/API Gateway enforcement for multi-instance deployments. | Medium |
| Azure resilience | Move retry/backoff into a reusable resilience policy with request timeouts, 429-aware delays, circuit breaking, and metrics. | Medium |
| End-to-end cloud tests | Add a provider contract test suite using recorded Azure responses or an Azure test subscription. | Medium |
| Durable snapshots | Store snapshots in Blob Storage or a database and write files atomically with retention and checksum validation. | Medium |
| Deployment manifests | Add Helm/Kustomize manifests, secret references, probes, resource limits, and network policies for AKS. | High |
| Observability | Add structured JSON logs, OpenTelemetry traces, Azure query metrics, cache metrics, and recommendation decision metrics. | High |

## Product and UX

| Recommendation | Description | Effort |
|---|---|---|
| Cost trends | Persist monthly cost history and show month-over-month and realized-savings charts. | Medium |
| PWA/offline mode | Support installability and cached read-only dashboards for field engineers. | Medium |
| Approval workflow | Require review and explicit approval before generated scaling configuration can be applied. | Medium |
| Multi-region/time-zone schedules | Store schedule time zones per resource and account for holidays and exception calendars. | Medium |
| Pricing confidence | Show the pricing source, timestamp, fallback status, and confidence beside every savings estimate. | Low |

## Remaining engineering cleanup

| Recommendation | Description | Effort |
|---|---|---|
| Shared frontend data layer | Add a shared query/cache hook for monitored resources and consistent refresh behavior across pages. | Low |
| API error documentation | Extend OpenAPI error responses to every operation and publish example 400/401/403/429/500 payloads. | Low |
| Frontend test coverage | Add tests for recommendation filters, lazy route loading, percentile evidence, and rate-limit/error states. | Low |
