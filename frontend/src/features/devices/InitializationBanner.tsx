import { useQuery } from '@tanstack/react-query';
import { IconClock } from '@tabler/icons-react';
import { getInitialization } from './api';

const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' });

export function InitializationBanner() {
  const initialization = useQuery({
    queryKey: ['business-initialization'],
    queryFn: getInitialization,
  });
  if (initialization.data?.status !== 'PREPARING' || !initialization.data.cutoffAt) return null;
  return (
    <div className="initialization-banner" role="status">
      <IconClock size={17} aria-hidden />
      <span>
        <strong>Configuração inicial em andamento</strong>
        Data de corte: {date.format(new Date(initialization.data.cutoffAt))}
      </span>
    </div>
  );
}
