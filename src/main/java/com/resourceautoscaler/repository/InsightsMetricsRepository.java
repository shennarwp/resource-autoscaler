package com.resourceautoscaler.repository;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.monitor.query.LogsQueryClient;
import com.azure.monitor.query.LogsQueryClientBuilder;
import com.azure.monitor.query.models.*;
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
 *   <li>{@code cpuUsageNanoCores}/{@code cpuLimitNanoCores} - CPU usage/limit as a rate in nanoCores</li>
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

    /** Creates the Log Analytics client used for Container Insights queries. */
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

    /** Queries Container Insights CPU counters and returns percentage samples. */
    @Override
    public List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange) {
        return toPoints(resourceId, queryCombined(resourceId, timeRange), true);
    }

    /** Queries Container Insights memory counters and returns percentage samples. */
    @Override
    public List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange) {
        return toPoints(resourceId, queryCombined(resourceId, timeRange), false);
    }

    /** Container Insights adapter has no active-request counter. */
    @Override
    public List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange) {
        return List.of();
    }

    /** Returns the combined CPU and memory series from one Kusto query. */
    @Override
    public List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange) {
        return toPoints(resourceId, queryCombined(resourceId, timeRange), null);
    }

    /** Returns the deployments currently exposed by the insights profile. */
    @Override
    public List<String> getMonitoredResourceIds() {
        return List.of("nginx-busy", "nginx-idle");
    }

    /** Uses the shared default peak schedule for the insights deployment. */
    @Override
    public PeakHoursConfig getPeakHoursConfig(String resourceId) {
        return PeakHoursConfig.defaults();
    }

    /** Discovers replica and pod resource values from Log Analytics. */
    @Override
    public CurrentConfig getCurrentConfig(String resourceId) {
        validateResourceId(resourceId);
        String range = timespan(CONFIG_LOOKBACK);
        String query = buildConfigQuery(resourceId, range, POD_NAMESPACE);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        QueryTimeInterval interval = new QueryTimeInterval(now.minus(CONFIG_LOOKBACK), now);

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
    }

    /** Downloads one-minute samples for an exact UTC interval for snapshot export. */
    @Override
    public Optional<List<MetricPoint>> downloadRawMetrics(String resourceId, Instant start, Instant end) {
        validateResourceId(resourceId);
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Snapshot start must be before end");
        }
        OffsetDateTime from = start.atOffset(ZoneOffset.UTC);
        OffsetDateTime to = end.atOffset(ZoneOffset.UTC);
        String query = buildExportQuery(resourceId, start.toString(), end.toString(), 60L, POD_NAMESPACE);
        return Optional.of(toPoints(resourceId, runQuery(query, from, to), null));
    }

    /**
     * Discovers the deployment's current configuration from Container Insights:
     * desired/available replicas (KubeDeployment metric), per-container resource
     * requests and limits (K8SContainer counters). Node capacity is intentionally
     * excluded because it is cluster-wide and cannot be attributed safely to one
     * deployment.
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
            | summarize arg_max(TimeGenerated, CounterValue) by PodUid, CounterName
            | summarize
                cpuReq = sum(case(CounterName=='cpuRequestNanoCores', CounterValue, 0.0)),
                cpuLim = sum(case(CounterName=='cpuLimitNanoCores', CounterValue, 0.0)),
                memReq = sum(case(CounterName=='memoryRequestBytes', CounterValue, 0.0)),
                memLim = sum(case(CounterName=='memoryLimitBytes', CounterValue, 0.0))
              by _key = 1;
            let nodes = Perf
            | where TimeGenerated > start
            | where ObjectName == 'K8SNode'
            | where CounterName == 'cpuCapacityNanoCores'
            | summarize arg_max(TimeGenerated, CounterValue) by Computer
            | summarize nodeCount = count(), cpuCores = sum(CounterValue) by _key = 1;
            deployments
            | join kind=leftouter (containers) on _key
            | project spec, avail, cpuReq, cpuLim, memReq, memLim
            """.formatted(range, namespace, resourceId, namespace, resourceId);
    }

    /**
     * Downloads full-resolution (60s) samples for an explicit [start, end] window,
     * using {@code between (datetime(..) .. datetime(..))} so the returned points
     * are exactly the requested range rather than "last N" before now.
     */
    static String buildExportQuery(String resourceId, String startIso, String endIso, long stepSeconds, String namespace) {
        validateResourceId(resourceId);
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

    /** Converts nullable Log Analytics columns from nano-units and bytes to API units. */
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

    /** Reads a nullable numeric column without making missing series fatal. */
    private static Double column(LogsTableRow row, String column) {
        return row.getColumnValue(column).map(LogsTableCell::getValueAsDouble).orElse(null);
    }

    /** Internal normalized row produced by the Kusto result mapper. */
    private record Row(Instant timestamp, double cpuPct, double memPct) {}

    /** Converts query rows into API points, selecting CPU, memory, or both fields. */
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

    /** Executes a Kusto query and sorts valid rows by timestamp. */
    private List<Row> runQuery(String query, OffsetDateTime from, OffsetDateTime to) {
        QueryTimeInterval interval = new QueryTimeInterval(from, to);
        try {
            LogsQueryResult result = logsClient.queryWorkspace(workspaceId, query, interval);
            if (result == null
                    || result.getQueryResultStatus() == LogsQueryResultStatus.FAILURE
                    || result.getTable() == null) {
                log.warn("Container Insights query failed: {}",
                        result != null ? result.getError() : "no result");
                throw new IllegalStateException("Container Insights query failed");
            }

            List<Row> rows = new ArrayList<>();
            for (LogsTableRow r : result.getTable().getRows()) {
                String ts = r.getColumnValue("TimeGenerated")
                        .map(LogsTableCell::getValueAsString).orElse(null);
                double cpu = r.getColumnValue("cpuPct")
                        .map(LogsTableCell::getValueAsDouble).orElse(0.0);
                double mem = r.getColumnValue("memPct")
                        .map(LogsTableCell::getValueAsDouble).orElse(0.0);
                if (ts != null) {
                    rows.add(new Row(OffsetDateTime.parse(ts).toInstant(),
                            cpu, mem));
                }
            }
            rows.sort(java.util.Comparator.comparing(Row::timestamp));
            return rows;
        } catch (RuntimeException e) {
            log.error("Container Insights query failed for resource {}", query, e);
            throw e;
        }
    }

    /** Builds a rolling query window with a range-dependent bucket size. */
    private List<Row> queryCombined(String resourceId, Duration timeRange) {
        String range = timespan(timeRange.plus(Duration.ofMinutes(5)));
        long step = stepSeconds(timeRange);

        String query = buildQuery(resourceId, range, step);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return runQuery(query, now.minus(timeRange).minus(Duration.ofMinutes(5)), now);
    }

    /** Builds the rolling Container Insights CPU and memory query. */
    private String buildQuery(String resourceId, String range, long step) {
        validateResourceId(resourceId);
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

    /** Maps a duration to the compact Kusto timespan syntax. */
    private static String timespan(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds >= 3600) {
            return (seconds + 3599) / 3600 + "h";
        }
        return Math.max(1, (seconds + 59) / 60) + "m";
    }

    /** Chooses one-minute, five-minute, or hourly aggregation for the range. */
    private static long stepSeconds(Duration timeRange) {
        long seconds = timeRange.getSeconds();
        if (seconds < 3600) return 60;
        if (seconds < 3 * 86400) return 300;
        return 3600;
    }

    /** Infers the public resource type from the Container Insights resource ID. */
    private String resourceType(String resourceId) {
        if (resourceId.startsWith("nginx")) return "K8S_CLUSTER";
        return "UNKNOWN";
    }

    private static void validateResourceId(String resourceId) {
        if (resourceId == null || !resourceId.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Invalid resource ID");
        }
    }
}
