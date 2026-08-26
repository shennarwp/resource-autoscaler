export interface MetricPoint {
  timestamp: string;
  cpuUtilization: number;
  memoryUtilization: number;
  activeRequestCount: number;
  resourceId: string;
  resourceType: string;
}

export interface AggregatedStats {
  avgCpuUtilization: number;
  maxCpuUtilization: number;
  avgMemoryUtilization: number;
  peakHourUtilization: number;
  offPeakHourUtilization: number;
}

export interface MetricsResponse {
  resourceId: string;
  resourceType: string;
  resourceName: string;
  dataPoints: MetricPoint[];
  stats: AggregatedStats;
}

export interface ScalingRecommendation {
  resourceId: string;
  resourceName: string;
  resourceType: ResourceType;
  recommendationType: RecommendationType;
  currentConfiguration: string;
  recommendedConfiguration: string;
  peakSchedule: string;
  offPeakSchedule: string;
  peakStart: string;
  peakEnd: string;
  estimatedMonthlySavingsUsd: number;
  estimatedSavingsPercentage: number;
  confidenceScore: number;
  generatedAt: string;
  rationale: string;
}

export interface RecommendationResponse {
  recommendation: ScalingRecommendation;
  kedaYaml: string;
  terraformHcl: string;
}

export interface CostAnalysis {
  analysisPeriod: string;
  generatedAt: string;
  resources: ResourceCostBreakdown[];
  summary: CostSummary;
}

export interface ResourceCostBreakdown {
  resourceId: string;
  resourceName: string;
  resourceType: string;
  currentMonthlyCostUsd: number;
  optimizedMonthlyCostUsd: number;
  potentialSavingsUsd: number;
  savingsPercentage: number;
  currentPeakCpuPercent: number;
  currentOffPeakCpuPercent: number;
  appliedOptimizations: string[];
}

export interface CostSummary {
  totalCurrentCostUsd: number;
  totalOptimizedCostUsd: number;
  totalPotentialSavingsUsd: number;
  overallSavingsPercentage: number;
  totalResourcesAnalyzed: number;
  resourcesWithOptimizations: number;
  estimatedAnnualSavingsUsd: string;
}

export type ResourceType =
  | 'AZURE_VM'
  | 'AKS_DEPLOYMENT'
  | 'AZURE_APP_SERVICE'
  | 'AZURE_FUNCTION';

export type RecommendationType =
  | 'SCHEDULE_BASED_SCALING'
  | 'RIGHTSIZING'
  | 'SHUTDOWN_OFF_HOURS'
  | 'KEDA_SCALED_OBJECT'
  | 'TERRAFORM_AUTOSCALE';
