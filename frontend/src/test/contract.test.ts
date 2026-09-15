/**
 * Contract tests verifying that frontend TypeScript types match backend JSON shapes.
 * These tests ensure frontend DTOs stay in sync with backend records.
 *
 * Run with: npm run test
 */
import { describe, it, expectTypeOf } from 'vitest';
import type {
  MetricPoint,
  AggregatedStats,
  MetricsResponse,
  PeakHoursConfig,
  ScalingRecommendation,
  RecommendationResponse,
  CostAnalysis,
  ResourceCostBreakdown,
  CostSummary,
} from '../types/api';

describe('Frontend-Backend contract tests', () => {
  it('MetricPoint matches backend MetricPoint record', () => {
    const point: MetricPoint = {
      timestamp: '2026-09-15T12:00:00Z',
      cpuUtilization: 45.2,
      memoryUtilization: 62.8,
      activeRequestCount: 100,
      resourceId: 'nginx-busy',
      resourceType: 'AKS_DEPLOYMENT',
    };
    expectTypeOf(point.cpuUtilization).toEqualTypeOf<number>();
    expectTypeOf(point.activeRequestCount).toEqualTypeOf<number>();
    expectTypeOf(point.timestamp).toEqualTypeOf<string>();
  });

  it('AggregatedStats matches backend fields', () => {
    const stats: AggregatedStats = {
      avgCpuUtilization: 35.5,
      maxCpuUtilization: 78.2,
      avgMemoryUtilization: 55.1,
      peakHourUtilization: 62.0,
      offPeakHourUtilization: 18.3,
    };
    expectTypeOf(stats.avgCpuUtilization).toEqualTypeOf<number>();
    expectTypeOf(stats.maxCpuUtilization).toEqualTypeOf<number>();
    expectTypeOf(stats.offPeakHourUtilization).toEqualTypeOf<number>();
  });

  it('MetricsResponse matches backend DTO', () => {
    const response: MetricsResponse = {
      resourceId: 'nginx-busy',
      resourceType: 'AKS_DEPLOYMENT',
      resourceName: 'nginx-busy',
      dataPoints: [],
      stats: {
        avgCpuUtilization: 0,
        maxCpuUtilization: 0,
        avgMemoryUtilization: 0,
        peakHourUtilization: 0,
        offPeakHourUtilization: 0,
      },
    };
    expectTypeOf(response.resourceId).toEqualTypeOf<string>();
    expectTypeOf(response.dataPoints).toEqualTypeOf<MetricPoint[]>();
  });

  it('PeakHoursConfig matches backend PeakHoursConfig record', () => {
    const config: PeakHoursConfig = {
      peakStart: '07:00',
      peakEnd: '18:00',
      peakDaysOfWeek: [1, 2, 3, 4, 5],
      peakTargetUtilization: 65,
      offPeakTargetUtilization: 10,
      scalingCooldownMinutes: 30,
    };
    expectTypeOf(config.peakDaysOfWeek).toEqualTypeOf<number[]>();
    expectTypeOf(config.scalingCooldownMinutes).toEqualTypeOf<number>();
  });

  it('ScalingRecommendation matches backend ScalingRecommendation record', () => {
    const rec: ScalingRecommendation = {
      resourceId: 'nginx-busy',
      resourceName: 'nginx-busy',
      resourceType: 'AKS_DEPLOYMENT',
      recommendationType: 'SCHEDULE_BASED_SCALING',
      currentConfiguration: 'replicas: 3',
      recommendedConfiguration: 'replicas: 1',
      peakSchedule: '07:00-18:00 Mon-Fri',
      offPeakSchedule: '18:00-07:00',
      peakStart: '07:00',
      peakEnd: '18:00',
      estimatedMonthlySavingsUsd: 1200,
      estimatedSavingsPercentage: 40,
      confidenceScore: 0.85,
      generatedAt: '2026-09-15T12:00:00Z',
      rationale: 'Off-peak utilization consistently below threshold.',
    };
    expectTypeOf(rec.estimatedMonthlySavingsUsd).toEqualTypeOf<number>();
    expectTypeOf(rec.confidenceScore).toEqualTypeOf<number>();
    expectTypeOf(rec.resourceType).toEqualTypeOf<import('../types/api').ResourceType>();
  });

  it('RecommendationResponse wraps ScalingRecommendation with generated code', () => {
    const res: RecommendationResponse = {
      recommendation: {} as ScalingRecommendation,
      kedaYaml: 'apiVersion: keda.sh/v1alpha1',
      terraformHcl: 'resource "azurerm_app_service" "example" {}',
    };
    expectTypeOf(res.kedaYaml).toEqualTypeOf<string | undefined>();
    expectTypeOf(res.terraformHcl).toEqualTypeOf<string | undefined>();
  });

  it('ResourceCostBreakdown matches backend DTO', () => {
    const r: ResourceCostBreakdown = {
      resourceId: 'nginx-busy',
      resourceName: 'nginx-busy',
      resourceType: 'AKS_DEPLOYMENT',
      currentMonthlyCostUsd: 2400,
      optimizedMonthlyCostUsd: 1200,
      potentialSavingsUsd: 1200,
      savingsPercentage: 50,
      currentPeakCpuPercent: 65,
      currentOffPeakCpuPercent: 10,
      appliedOptimizations: [],
    };
    expectTypeOf(r.potentialSavingsUsd).toEqualTypeOf<number>();
    expectTypeOf(r.appliedOptimizations).toEqualTypeOf<string[]>();
  });

  it('CostSummary matches backend CostSummary record', () => {
    const s: CostSummary = {
      totalCurrentCostUsd: 4800,
      totalOptimizedCostUsd: 2400,
      totalPotentialSavingsUsd: 2400,
      overallSavingsPercentage: 50,
      totalResourcesAnalyzed: 4,
      resourcesWithOptimizations: 3,
      estimatedAnnualSavingsUsd: '28800',
    };
    expectTypeOf(s.estimatedAnnualSavingsUsd).toEqualTypeOf<string>();
    expectTypeOf(s.totalResourcesAnalyzed).toEqualTypeOf<number>();
  });

  it('CostAnalysis wraps resources and summary', () => {
    const a: CostAnalysis = {
      analysisPeriod: 'Last 30 days',
      generatedAt: '2026-09-15T12:00:00Z',
      resources: [],
      summary: {} as CostSummary,
    };
    expectTypeOf(a.resources).toEqualTypeOf<ResourceCostBreakdown[]>();
    expectTypeOf(a.summary).toEqualTypeOf<CostSummary>();
  });
});
