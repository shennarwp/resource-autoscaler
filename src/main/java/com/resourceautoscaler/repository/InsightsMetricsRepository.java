package com.resourceautoscaler.repository;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.monitor.query.LogsQueryClient;
import com.azure.monitor.query.LogsQueryClientBuilder;
import com.azure.monitor.query.models.LogsQueryResult;
import com.azure.monitor.query.models.LogsQueryResultStatus;
import com.azure.monitor.query.models.LogsTableRow;
import com.azure.monitor.query.models.QueryTimeInterval;
import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads cluster metrics from Azure Monitor Container Insights (Log Analytics).
 * <p>
 * The AMA-based Container Insights agent writes per-container performance counters to the
 * {@code Perf} table ({@code ObjectName} = {@code K8SContainer}):
 * <ul>
 *   <li>{@code cpuUsageNanoCores}/{@code cpuLimitNanoCores}  - CPU usage/limit as a rate in nanoCores</li>
 *   <li>{@code memoryWorkingSetBytes}/{@code memoryLimitBytes} - working-set memory in bytes</li>
 * </ul>
 * Each row's {@code InstanceName} ends with {@code <podUid>/<containerName>}, so records are joined
 * to {@code KubePodInventory} (pod UID -> pod name) to scope metrics to a single deployment.
 */
@Repository
@Profile("insights")
public class InsightsMetricsRepository implements MetricsRepository {

    private static final Logger log = LoggerFactory.getLogger(InsightsMetricsRepository.class);

    private static final String POD_NAMESPACE = "default";
    private static final Duration CONFIG_LOOKBACK = Duration.ofDays(1);
    private static final String COUNTERS =
        "('cpuUsageNanoCores','cpuLimitNanoCores','memoryWorkingSetBytes','memoryLimitBytes')";

    @Value("${app.azure.tenant-id}")
    private String tenantId;

    @Value("${app.azure.client-id}")
    private String clientId;

    @Value("${app.azure.client-secret}")
    private String clientSecret;

    @Value("${app.insights.workspace-id}")
    private String workspaceId;

    private LogsQueryClient logsClient;

    @PostConstruct
    public void init() {
        var credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        logsClient = new LogsQueryClientBuilder()
                .credential(credential)
                .buildClient();

        log.info("Initialized Container Insights (Log Analytics) repository for workspace {}", workspaceId);
    }

    @Override
    public List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange) {
        return toPoints(resourceId, queryCombined(resourceId, timeRange), true);
    }

    @Override
    public List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange) {
        return toPoints(resourceId, queryCombined(resourceId, timeRange), false);
    }

    @Override
    public List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange) {
        return List.of();
    }

    @Override
    public List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange) {
        return toPoints(resourceId, queryCombined(resourceId, timeRange), null);
    }

    @Override
    public List<String> getMonitoredResourceIds() {
        return List.of("nginx-busy", "nginx-idle");
    }

    @Override
    public PeakHoursConfig getPeakHoursConfig(String resourceId) {
        return PeakHoursConfig.defaults();
    }

    @Override
    public CurrentConfig getCurrentConfig(String resourceId) {
        String range = timespan(CONFIG_LOOKBACK);
        String query = buildConfigQuery(resourceId, range, POD_NAMESPACE);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        QueryTimeInterval interval = new QueryTimeInterval(now.minus(CONFIG_LOOKBACK), now);

        try {
            LogsQueryResult result = logsClient.queryWorkspace(workspaceId, query, interval);
            if (result == null
                    || result.getQueryResultStatus() == LogsQueryResultStatus.FAILURE
                    || result.getTable() == null
                    || result.getTable().getRows().isEmpty()) {
                log.warn("Container Insights config query for {} found no deployment row", resourceId);
                return CurrentConfig.unknown(resourceId);
            }

            LogsTableRow row = result.getTable().getRows().getFirst();
            return currentConfigFromValues(
                resourceId,
                column(row, "spec"),
                column(row, "avail"),
                column(row, "cpuReq"),
                column(row, "cpuLim"),
                column(row, "memReq"),
                column(row, "memLim"),
                column(row, "nodeCount"),
                column(row, "cpuCores")
            );
        } catch (RuntimeException e) {
            log.error("Container Insights config query failed for resource {}", resourceId, e);
            return CurrentConfig.unknown(resourceId);
        }
    }

    @Override
    public Optional<List<MetricPoint>> downloadRawMetrics(String resourceId, Instant start, Instant end) {
        OffsetDateTime from = start.atOffset(ZoneOffset.UTC);
        OffsetDateTime to = end.atOffset(ZoneOffset.UTC);
        String query = buildExportQuery(resourceId, start.toString(), end.toString(), 60L, POD_NAMESPACE);
        return Optional.of(toPoints(resourceId, runQuery(query, from, to), null));
    }

    /**
     * Discovers the deployment's current configuration from Container Insights:
     * desired/available replicas (KubeDeployment metric), per-container resource
     * requests and limits (K8SContainer counters) and total cluster CPU capacity
     * (K8SNode counters). Returns a single row; a {@code leftouter} join keeps it
     * even where the container/node series are missing for a fresh deployment.
     */
    static String buildConfigQuery(String resourceId, String range, String namespace) {
        return """
            let start = ago(%s);
            let deployments = InsightsMetrics
            | where TimeGenerated > start
            | where Name == 'kube_deployment_status_replicas_ready'
            | extend tags = todynamic(Tags)
            | where tostring(tags.k8sNamespace) == '%s'
            | where tostring(tags.deployment) == '%s'
            | summarize arg_max(TimeGenerated, tags) by _key = 1
            | extend spec = toint(tags.spec_replicas), avail = toint(tags.status_replicas_available)
            | project spec, avail, _key;
            let pods = KubePodInventory
            | where TimeGenerated > start
            | where Namespace == '%s'
            | where Name startswith '%s-'
            | project PodUid = tostring(PodUid), _key = 1;
            let containers = Perf
            | where TimeGenerated > start
            | where ObjectName == 'K8SContainer'
            | where CounterName in ('cpuRequestNanoCores','cpuLimitNanoCores','memoryRequestBytes','memoryLimitBytes')
            | extend PodUid = tostring(split(InstanceName,'/')[-2])
            | join kind=inner (pods) on PodUid
            | summarize
                cpuReq = max(case(CounterName=='cpuRequestNanoCores', CounterValue, 0.0)),
                cpuLim = max(case(CounterName=='cpuLimitNanoCores', CounterValue, 0.0)),
                memReq = max(case(CounterName=='memoryRequestBytes', CounterValue, 0.0)),
                memLim = max(case(CounterName=='memoryLimitBytes', CounterValue, 0.0))
              by _key = 1;
            let nodes = Perf
            | where TimeGenerated > start
            | where ObjectName == 'K8SNode'
            | where CounterName == 'cpuCapacityNanoCores'
            | summarize c = arg_max(TimeGenerated, CounterValue) by Computer
            | summarize nodeCount = count(), cpuCores = sum(c) by _key = 1;
            deployments
            | join kind=leftouter (containers) on _key
            | join kind=leftouter (nodes) on _key
            | project spec, avail, cpuReq, cpuLim, memReq, memLim, nodeCount, cpuCores
            """.formatted(range, namespace, resourceId, namespace, resourceId);
    }

    /**
     * Downloads full-resolution (60s) samples for an explicit [start, end] window,
     * using {@code between (datetime(..) .. datetime(..))} so the returned points
     * are exactly the requested range rather than "last N" before now.
     */
    static String buildExportQuery(String resourceId, String startIso, String endIso, long stepSeconds, String namespace) {
        return """
            let pod = KubePodInventory
            | where TimeGenerated between (datetime(%s) .. datetime(%s))
            | where Namespace == '%s'
            | where Name startswith '%s-'
            | project PodUid = tostring(PodUid);
            Perf
            | where ObjectName == 'K8SContainer'
            | where CounterName in %s
            | where TimeGenerated between (datetime(%s) .. datetime(%s))
            | extend PodUid = tostring(split(InstanceName,'/')[-2])
            | join kind=inner (pod) on PodUid
            | summarize
                usage  = sum(case(CounterName=='cpuUsageNanoCores', CounterValue, 0.0)),
                lim    = sum(case(CounterName=='cpuLimitNanoCores', CounterValue, 0.0)),
                memUse = sum(case(CounterName=='memoryWorkingSetBytes', CounterValue, 0.0)),
                memLim = sum(case(CounterName=='memoryLimitBytes', CounterValue, 0.0))
              by bin(TimeGenerated, %ds)
            | where lim > 0 and memLim > 0
            | extend cpuPct = iif(usage > lim, 100.0, usage*100.0/lim), memPct = iif(memUse > memLim, 100.0, memUse*100.0/memLim)
            | project TimeGenerated, cpuPct = round(cpuPct, 2), memPct = round(memPct, 2)
            """.formatted(startIso, endIso, namespace, resourceId, COUNTERS, startIso, endIso, stepSeconds);
    }

    static CurrentConfig currentConfigFromValues(
            String resourceId,
            Double spec, Double avail, Double cpuReq, Double cpuLim,
            Double memReq, Double memLim, Double nodeCount, Double cpuCores
    ) {
        if (spec == null) {
            return CurrentConfig.unknown(resourceId);
        }
        double requestCores = cpuReq != null ? cpuReq / 1e9 : 0.0;
        double limitCores = cpuLim != null ? cpuLim / 1e9 : 0.0;
        double requestGiB = memReq != null ? memReq / (1024.0 * 1024.0 * 1024.0) : 0.0;
        double limitGiB = memLim != null ? memLim / (1024.0 * 1024.0 * 1024.0) : 0.0;
        return new CurrentConfig(
            resourceId,
            (int) Math.round(spec),
            avail != null ? (int) Math.round(avail) : 0,
            requestCores,
            limitCores,
            requestGiB,
            limitGiB,
            nodeCount != null ? (int) Math.round(nodeCount) : 0,
            cpuCores != null ? cpuCores / 1e9 : 0.0,
            true
        );
    }

    private static Double column(LogsTableRow row, String column) {
        return row.getColumnValue(column).map(c -> c.getValueAsDouble()).orElse(null);
    }

    private record Row(Instant timestamp, double cpuPct, double memPct) {}

    private List<MetricPoint> toPoints(String resourceId, List<Row> rows, Boolean cpuOnly) {
        List<MetricPoint> points = new ArrayList<>();
        for (Row row : rows) {
            double cpu = cpuOnly == null || cpuOnly ? row.cpuPct : 0;
            double mem = cpuOnly == null || !cpuOnly ? row.memPct : 0;
            points.add(new MetricPoint(
                    row.timestamp, cpu, mem, 0,
                    resourceId, resourceType(resourceId)
            ));
        }
        return points;
    }

    private List<Row> runQuery(String query, OffsetDateTime from, OffsetDateTime to) {
        QueryTimeInterval interval = new QueryTimeInterval(from, to);
        try {
            LogsQueryResult result = logsClient.queryWorkspace(workspaceId, query, interval);
            if (result == null
                    || result.getQueryResultStatus() == LogsQueryResultStatus.FAILURE
                    || result.getTable() == null) {
                log.warn("Container Insights query failed: {}",
                        result != null ? result.getError() : "no result");
                return List.of();
            }

            List<Row> rows = new ArrayList<>();
            for (LogsTableRow r : result.getTable().getRows()) {
                String ts = r.getColumnValue("TimeGenerated")
                        .map(c -> c.getValueAsString()).orElse(null);
                Double cpu = r.getColumnValue("cpuPct")
                        .map(c -> c.getValueAsDouble()).orElse(0.0);
                Double mem = r.getColumnValue("memPct")
                        .map(c -> c.getValueAsDouble()).orElse(0.0);
                if (ts != null) {
                    rows.add(new Row(OffsetDateTime.parse(ts).toInstant(),
                            cpu == null ? 0 : cpu, mem == null ? 0 : mem));
                }
            }
            rows.sort(java.util.Comparator.comparing(Row::timestamp));
            return rows;
        } catch (RuntimeException e) {
            log.error("Container Insights query failed for resource {}", query, e);
            return List.of();
        }
    }

    private List<Row> queryCombined(String resourceId, Duration timeRange) {
        String range = timespan(timeRange.plus(Duration.ofMinutes(5)));
        long step = stepSeconds(timeRange);

        String query = buildQuery(resourceId, range, step);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return runQuery(query, now.minus(timeRange).minus(Duration.ofMinutes(5)), now);
    }

    private String buildQuery(String resourceId, String range, long step) {
        return """
            let pod = KubePodInventory
            | where TimeGenerated > ago(%s)
            | where Namespace == '%s'
            | where Name startswith '%s-'
            | project PodUid = tostring(PodUid);
            Perf
            | where ObjectName == 'K8SContainer'
            | where CounterName in %s
            | where TimeGenerated > ago(%s)
            | extend PodUid = tostring(split(InstanceName,'/')[-2])
            | join kind=inner (pod) on PodUid
            | summarize
                usage  = sum(case(CounterName=='cpuUsageNanoCores', CounterValue, 0.0)),
                lim    = sum(case(CounterName=='cpuLimitNanoCores', CounterValue, 0.0)),
                memUse = sum(case(CounterName=='memoryWorkingSetBytes', CounterValue, 0.0)),
                memLim = sum(case(CounterName=='memoryLimitBytes', CounterValue, 0.0))
              by bin(TimeGenerated, %ds)
            | where lim > 0 and memLim > 0
            | extend cpuPct = iif(usage > lim, 100.0, usage*100.0/lim), memPct = iif(memUse > memLim, 100.0, memUse*100.0/memLim)
            | project TimeGenerated, cpuPct = round(cpuPct, 2), memPct = round(memPct, 2)
            """.formatted(range, POD_NAMESPACE, resourceId, COUNTERS, range, step);
    }

    private static String timespan(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds >= 3600) {
            return (seconds + 3599) / 3600 + "h";
        }
        return Math.max(1, (seconds + 59) / 60) + "m";
    }

    private static long stepSeconds(Duration timeRange) {
        long seconds = timeRange.getSeconds();
        if (seconds < 3600) return 60;
        if (seconds < 3 * 86400) return 300;
        return 3600;
    }

    private String resourceType(String resourceId) {
        if (resourceId.startsWith("nginx")) return "K8S_CLUSTER";
        return "UNKNOWN";
    }
}