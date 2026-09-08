import type { ReactNode } from 'react';
export function EmptyState({
  title,
  description,
  icon,
}: {
  title: string;
  description: string;
  icon?: ReactNode;
}) {
  return (
    <div className="empty-state">
      {icon && (
        <span className="empty-icon" aria-hidden>
          {icon}
        </span>
      )}
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}
