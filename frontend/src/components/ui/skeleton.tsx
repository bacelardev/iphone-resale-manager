import type { CSSProperties, ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

export function Skeleton({
  className,
  width,
}: {
  className?: string;
  width?: CSSProperties['width'];
}) {
  return (
    <span
      aria-hidden
      className={cn('skeleton', className)}
      style={width === undefined ? undefined : { width }}
    />
  );
}

/** Placeholder for one value inside a card that stays mounted while the value loads. */
export function ValueSkeleton() {
  return <Skeleton className="skeleton-value" />;
}

/** Announces background work on content that stays on screen, such as a page being replaced. */
export function RefreshStatus({ active, label }: { active: boolean; label: string }) {
  return (
    <p className="visually-hidden" role="status">
      {active ? label : ''}
    </p>
  );
}

/**
 * Page position between the paging buttons. While the next page loads, a visible "Atualizando…"
 * mark replaces it in the same grid cell, so the buttons never shift and the listed content keeps
 * its full contrast. The mark stays out of the accessibility tree: `RefreshStatus` announces it.
 */
export function PageStatus({ busy, children }: { busy: boolean; children: ReactNode }) {
  return (
    <span className="page-status" data-busy={busy || undefined}>
      <span className="page-status-position">{children}</span>
      <span className="page-status-busy" aria-hidden>
        <span className="spinner page-status-spinner" />
        Atualizando…
      </span>
    </span>
  );
}

/** Names the loading state for assistive technology; the placeholder shapes stay hidden. */
function LoadingRegion({
  label,
  className,
  children,
}: {
  label: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div role="status" className={className}>
      <span className="visually-hidden">{label}</span>
      <div aria-hidden>{children}</div>
    </div>
  );
}

export function DeviceGridSkeleton({ label, count = 4 }: { label: string; count?: number }) {
  return (
    <LoadingRegion label={label}>
      <div className="device-grid">
        {Array.from({ length: count }, (_, index) => (
          <div className="device-card device-card-skeleton" key={index}>
            <div className="device-cover">
              <Skeleton className="skeleton-fill" />
            </div>
            <div className="device-card-body">
              <Skeleton className="skeleton-flush" width="40%" />
              <Skeleton className="skeleton-title" width="70%" />
              <Skeleton width="55%" />
              <div className="skeleton-pair">
                <Skeleton className="skeleton-flush" />
                <Skeleton className="skeleton-flush" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </LoadingRegion>
  );
}

export function ListSkeleton({
  label,
  rows = 3,
  divided = false,
  className,
}: {
  label: string;
  rows?: number;
  divided?: boolean;
  className?: string;
}) {
  return (
    <LoadingRegion label={label} className={className}>
      <ul className={cn('skeleton-list', divided && 'skeleton-list-divided')}>
        {Array.from({ length: rows }, (_, index) => (
          <li className="skeleton-row" key={index}>
            <span className="skeleton-row-main">
              <Skeleton className="skeleton-flush" width="60%" />
              <Skeleton width="35%" />
            </span>
            <Skeleton className="skeleton-pill" />
          </li>
        ))}
      </ul>
    </LoadingRegion>
  );
}

/** Stand-in for a detail or form page: heading followed by two content cards. */
export function PageSkeleton({ label }: { label: string }) {
  return (
    <LoadingRegion label={label}>
      <Skeleton className="skeleton-flush" width="18%" />
      <Skeleton className="skeleton-heading" width="45%" />
      <Skeleton width="32%" />
      <div className="page-skeleton-cards">
        {[0, 1].map((card) => (
          <div className="card page-skeleton-card" key={card}>
            <Skeleton className="skeleton-flush" width="30%" />
            <Skeleton className="skeleton-title" width="55%" />
            <Skeleton width="80%" />
            <Skeleton width="65%" />
          </div>
        ))}
      </div>
    </LoadingRegion>
  );
}
