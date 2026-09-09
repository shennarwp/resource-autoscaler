package com.resourceautoscaler.repository;

import com.azure.core.util.Context;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.monitor.query.MetricsQueryClient;
import com.azure.monitor.query.MetricsQueryClientBuilder;
import com.azure.monitor.query.models.AggregationType;
import com.azure.monitor.query.models.MetricResult;
import com.azure.monitor.query.models.MetricValue;
import com.azure.monitor.query.models.MetricsQueryOptions;
import com.azure.monitor.query.models.MetricsQueryResult;
import com.azure.monitor.query.models.QueryTimeInterval;
import com.azure.monitor.query.models.TimeSeriesElement;
import com.resourceautoscaler.model.CurrentConfig;
import com.resourceautoscaler.model.MetricPoint;
import com.resourceautoscaler.model.PeakHoursConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** Reads Azure Monitor metrics for Azure resource profiles. */
@Repository
@Profile("azure")
public class AzureMetricsRepository implements MetricsRepository {

    @Value("${app.azure.tenant-id}")
    private String tenantId;

    @Value("${app.azure.client-id}")
    private String clientId;

    @Value("${app.azure.client-secret}")
    private String clientSecret;

    @Value("${app.azure.subscription-id}")
    private String subscriptionId;

    @Value("${app.azure.resource-group}")
    private String resourceGroup;

    private MetricsQueryClient metricsClient;

    /** Creates the Azure Monitor client from the configured service principal. */
    @PostConstruct
    public void init() {
        var credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        metricsClient = new MetricsQueryClientBuilder()
                .credential(credential)
                .buildClient();
    }

    /** Loads and converts the Azure CPU metric to a percentage. */
    @Override
    public List<MetricPoint> getCpuUtilization(String resourceId, Duration timeRange) {
        return queryMetric(resourceId, timeRange, "CpuTime");
    }

    /** Loads memory samples and converts bytes to megabytes. */
    @Override
    public List<MetricPoint> getMemoryUtilization(String resourceId, Duration timeRange) {
        return queryMetric(resourceId, timeRange, "MemoryWorkingSet", value -> toMb(value));
    }

    /** Loads Azure request totals for the requested window. */
    @Override
    public List<MetricPoint> getActiveRequestCount(String resourceId, Duration timeRange) {
        return queryMetric(resourceId, timeRange, "Requests");
    }

    /** Queries the three metric streams and joins them by timestamp. */
    @Override
    public List<MetricPoint> getAllMetrics(String resourceId, Duration timeRange) {
        List<MetricPoint> cpu = getCpuUtilization(resourceId, timeRange);
        List<MetricPoint> mem = getMemoryUtilization(resourceId, timeRange);
        List<MetricPoint> req = getActiveRequestCount(resourceId, timeRange);
        return mergeByTimestamp(resourceId, getResourceType(resourceId), cpu, mem, req);
    }

    /** Merges independently sampled streams, defaulting missing values to zero. */
    static List<MetricPoint> mergeByTimestamp(
            String resourceId, String resourceType,
            List<MetricPoint> cpu, List<MetricPoint> mem, List<MetricPoint> req
    ) {
        java.util.Map<Instant, double[]> byTimestamp = new java.util.TreeMap<>();
        for (MetricPoint p : cpu) {
            byTimestamp.computeIfAbsent(p.timestamp(), k -> new double[3])[0] = p.cpuUtilization();
        }
        for (MetricPoint p : mem) {
            byTimestamp.computeIfAbsent(p.timestamp(), k -> new double[3])[1] = p.memoryUtilization();
        }
        for (MetricPoint p : req) {
            byTimestamp.computeIfAbsent(p.timestamp(), k -> new double[3])[2] = p.activeRequestCount();
        }

        List<MetricPoint> merged = new ArrayList<>();
        for (java.util.Map.Entry<Instant, double[]> entry : byTimestamp.entrySet()) {
            merged.add(new MetricPoint(
                entry.getKey(),
                entry.getValue()[0],
                entry.getValue()[1],
                (int) Math.round(entry.getValue()[2]),
                resourceId,
                resourceType
            ));
        }
        return merged;
    }

    /** Returns the representative resources exposed by the Azure profile. */
    @Override
    public List<String> getMonitoredResourceIds() {
        return List.of(
            "autoscaler-busy",
            "autoscaler-idle"
        );
    }

    /** Uses the common default schedule until resource-specific configuration exists. */
    @Override
    public PeakHoursConfig getPeakHoursConfig(String resourceId) {
        return PeakHoursConfig.defaults();
    }

    /** Azure Monitor does not currently expose capacity discovery for this adapter. */
    @Override
    public CurrentConfig getCurrentConfig(String resourceId) {
        return CurrentConfig.unknown(resourceId);
    }

    /** Queries one named metric and applies its unit conversion. */
    private List<MetricPoint> queryMetric(String resourceId, Duration timeRange, String metricName) {
        DoubleUnaryOperator cpuTransform = metricName.equals("CpuTime") ? v -> v / 3600.0 * 100.0 : v -> v;
        return queryMetric(resourceId, timeRange, metricName, cpuTransform);
    }

    /** Executes a metric query over a UTC interval and maps returned time series values. */
    private List<MetricPoint> queryMetric(
            String resourceId, Duration timeRange, String metricName,
            DoubleUnaryOperator transform
    ) {
        String resourceIdFull = buildResourceId(resourceId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        QueryTimeInterval timeInterval = new QueryTimeInterval(
                now.minus(timeRange),
                now
        );

        MetricsQueryOptions options = new MetricsQueryOptions()
                .setMetricNamespace("Microsoft.Web/sites")
                .setGranularity(Duration.ofHours(1))
                .setAggregations(List.of(AggregationType.TOTAL))
                .setTimeInterval(timeInterval);

        MetricsQueryResult result = metricsClient.queryResourceWithResponse(
                resourceIdFull,
                List.of(metricName),
                options,
                Context.NONE
        ).getValue();

        List<MetricPoint> points = new ArrayList<>();
        if (result != null) {
            for (MetricResult metric : result.getMetrics()) {
                for (TimeSeriesElement element : metric.getTimeSeries()) {
                    for (MetricValue value : element.getValues()) {
                        if (value.getTotal() != null) {
                            Instant timestamp = value.getTimeStamp().toInstant();
                            double total = value.getTotal();
                            double transformed = transform.applyAsDouble(total);
                            points.add(new MetricPoint(
                                    timestamp, transformed, transformed, (int) transformed,
                                    resourceId, getResourceType(resourceId)
                            ));
                        }
                    }
                }
            }
        }
        return points;
    }

    /** Builds the fully qualified Azure resource ID from configured subscription data. */
    private String buildResourceId(String resourceId) {
        return String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Web/sites/%s",
            subscriptionId, resourceGroup, resourceId
        );
    }

    /** Converts bytes to the megabyte unit used by the API model. */
    private static double toMb(double bytes) {
        return bytes / (1024 * 1024);
    }

    /** Infers the API resource type from the repository's stable ID prefixes. */
    private String getResourceType(String resourceId) {
        if (resourceId.startsWith("aks")) return "AKS_CLUSTER";
        if (resourceId.startsWith("vm")) return "AZURE_VM";
        if (resourceId.startsWith("app")) return "APP_SERVICE";
        if (resourceId.startsWith("func")) return "AZURE_FUNCTION";
        if (resourceId.startsWith("autoscaler")) return "APP_SERVICE";
        return "UNKNOWN";
    }
}
