export function Spinner({ label = 'Carregando' }: { label?: string }) {
  return <span role="status" className="spinner" aria-label={label} />;
}
