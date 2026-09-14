export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <div className="brand">
      <img src="/favicon.svg" width="36" height="36" alt="" />
      <div>
        <span>
          Delarte Control<span className="brand-dot">.</span>
        </span>
        {!compact && <small>Seu espaço de gestão</small>}
      </div>
    </div>
  );
}
