interface SkeletonProps {
  width?: string;
  height?: string;
  borderRadius?: string;
  className?: string;
}

export default function Skeleton({
  width = '100%',
  height = '1rem',
  borderRadius = '6px',
  className = '',
}: SkeletonProps) {
  return (
    <div
      className={`skeleton ${className}`}
      style={{ width, height, borderRadius }}
    />
  );
}

export function SkeletonCard({ lines = 3 }: { lines?: number }) {
  return (
    <div className="skeleton-card card">
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton
          key={i}
          height={i === 0 ? '1rem' : '0.75rem'}
          width={i === lines - 1 ? '60%' : '100%'}
        />
      ))}
    </div>
  );
}

export function SkeletonChart() {
  return (
    <div className="skeleton-chart">
      <Skeleton height="1.25rem" width="40%" />
      <Skeleton height="300px" borderRadius="8px" />
    </div>
  );
}

export function SkeletonStats() {
  return (
    <div className="stats-row">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="stat-box">
          <Skeleton height="0.75rem" width="60%" />
          <Skeleton height="1.5rem" width="40%" />
        </div>
      ))}
    </div>
  );
}
