import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { IconFilter, IconHistory } from '@tabler/icons-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { ListSkeleton, PageStatus, RefreshStatus } from '@/components/ui/skeleton';
import { getAuditEvent, listAuditEvents } from '@/features/audit/api';
import { keepPreviousPage, withoutLastSegment } from '@/lib/query/placeholder';
import { useRetainedValue } from '@/lib/utils/use-retained-value';
import {
  auditActions,
  auditedEntityTypes,
  type AuditAction,
  type AuditedEntityType,
} from '@/types/stage-k';

const dateTime = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
  timeZone: 'America/Bahia',
});

export function HistoryPage() {
  const [entityType, setEntityType] = useState<AuditedEntityType | ''>('');
  const [action, setAction] = useState<AuditAction | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const eventsKey = ['audit', entityType, action, from, to, page] as const;
  const events = useQuery({
    queryKey: eventsKey,
    queryFn: () =>
      listAuditEvents({
        entityType: entityType || undefined,
        action: action || undefined,
        from: from || undefined,
        to: to || undefined,
        page,
      }),
    placeholderData: keepPreviousPage(eventsKey, withoutLastSegment),
  });
  // Keeps the last event in the drawer while it animates out after being closed.
  const shownId = useRetainedValue(selectedId);
  const detail = useQuery({
    queryKey: ['audit-detail', shownId],
    queryFn: () => getAuditEvent(shownId!),
    enabled: shownId != null,
  });
  // While the previous page is on screen its limits may be stale, so paging waits for the current
  // page. The target derives from the page displayed, never from the pending `page` state.
  const paging = events.isPlaceholderData;
  function goToPage(offset: number) {
    const current = events.data;
    if (paging || !current) return;
    const target = current.page + offset;
    if (target < 0 || target >= current.totalPages) return;
    setPage(target);
  }

  return (
    <div className="history-page">
      <PageHeader
        eyebrow="AUDITORIA"
        title="Histórico"
        description="Cada alteração importante, com autor, momento e contexto."
      />
      <Card className="stage-k-filters">
        <div className="stage-k-filter-title">
          <IconFilter size={18} aria-hidden /> <strong>Filtrar eventos</strong>
        </div>
        <div className="stage-k-filter-grid">
          <FormField id="history-entity" label="Área">
            <Select
              id="history-entity"
              value={entityType}
              onChange={(event) => {
                setEntityType(event.target.value as AuditedEntityType | '');
                setPage(0);
              }}
            >
              <option value="">Todas</option>
              {auditedEntityTypes.map((value) => (
                <option key={value} value={value}>
                  {value.replaceAll('_', ' ')}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="history-action" label="Ação">
            <Select
              id="history-action"
              value={action}
              onChange={(event) => {
                setAction(event.target.value as AuditAction | '');
                setPage(0);
              }}
            >
              <option value="">Todas</option>
              {auditActions.map((value) => (
                <option key={value} value={value}>
                  {value.replaceAll('_', ' ')}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField id="history-from" label="De">
            <Input
              id="history-from"
              type="date"
              value={from}
              onChange={(event) => {
                setFrom(event.target.value);
                setPage(0);
              }}
            />
          </FormField>
          <FormField id="history-to" label="Até">
            <Input
              id="history-to"
              type="date"
              min={from}
              value={to}
              onChange={(event) => {
                setTo(event.target.value);
                setPage(0);
              }}
            />
          </FormField>
        </div>
      </Card>

      {events.error && <ErrorState error={events.error} retry={() => void events.refetch()} />}
      {events.isLoading && (
        <Card className="stage-k-list-card">
          <ListSkeleton label="Carregando histórico…" rows={4} />
        </Card>
      )}
      <RefreshStatus active={events.isPlaceholderData} label="Atualizando histórico…" />
      {events.data && events.data.content.length === 0 && (
        <Card className="stage-k-empty">
          <IconHistory size={28} aria-hidden />
          <strong>Nenhum evento encontrado</strong>
          <p>Ajuste os filtros ou aguarde novas operações.</p>
        </Card>
      )}
      {!!events.data?.content.length && (
        <Card className="stage-k-list-card">
          <ul
            className="stage-k-list"
            aria-live="polite"
            aria-busy={events.isPlaceholderData}
          >
            {events.data.content.map((event) => (
              <li key={event.id}>
                <button type="button" onClick={() => setSelectedId(event.id)}>
                  <span>
                    <strong>{event.summary}</strong>
                    <small>
                      {event.actorUser?.name ?? 'Sistema'} ·{' '}
                      {dateTime.format(new Date(event.occurredAt))}
                    </small>
                  </span>
                  <span className="stage-k-event-meta">
                    {event.action.replaceAll('_', ' ')}
                    <small>{event.entityReference ?? event.entityType.replaceAll('_', ' ')}</small>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <Pagination
            page={events.data.page}
            totalPages={events.data.totalPages}
            first={events.data.first}
            last={events.data.last}
            busy={paging}
            onStep={goToPage}
          />
        </Card>
      )}

      <Dialog open={selectedId !== null} onOpenChange={(open) => !open && setSelectedId(null)}>
        <DialogContent
          title="Detalhes do evento"
          description="Registro somente leitura preservado pela auditoria."
          placement="drawer"
        >
          {detail.isLoading && (
            <ListSkeleton
              label="Carregando detalhes…"
              rows={3}
              divided
              className="skeleton-spaced"
            />
          )}
          {detail.error && <ErrorState error={detail.error} retry={() => void detail.refetch()} />}
          {detail.data && (
            <dl className="stage-k-detail-list">
              <div>
                <dt>Quando</dt>
                <dd>{dateTime.format(new Date(detail.data.occurredAt))}</dd>
              </div>
              <div>
                <dt>Autor</dt>
                <dd>{detail.data.actorUser?.name ?? 'Sistema'}</dd>
              </div>
              <div>
                <dt>Ação</dt>
                <dd>{detail.data.action.replaceAll('_', ' ')}</dd>
              </div>
              <div>
                <dt>Entidade</dt>
                <dd>{detail.data.entityType.replaceAll('_', ' ')}</dd>
              </div>
              <div>
                <dt>Referência</dt>
                <dd>{detail.data.entityReference ?? '—'}</dd>
              </div>
              <div>
                <dt>Resumo</dt>
                <dd>{detail.data.summary}</dd>
              </div>
              <div className="stage-k-detail-wide">
                <dt>Alterações</dt>
                <dd>
                  <pre>{JSON.stringify(detail.data.changes, null, 2)}</pre>
                </dd>
              </div>
              <div className="stage-k-detail-wide">
                <dt>Request ID</dt>
                <dd>{detail.data.requestId ?? '—'}</dd>
              </div>
            </dl>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Pagination(props: {
  page: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  busy: boolean;
  onStep(offset: number): void;
}) {
  return (
    <nav className="stage-k-pagination" aria-label="Paginação do histórico">
      <Button
        variant="secondary"
        disabled={props.first}
        aria-disabled={props.busy || undefined}
        onClick={() => props.onStep(-1)}
      >
        Anterior
      </Button>
      <PageStatus busy={props.busy}>
        Página {props.totalPages === 0 ? 0 : props.page + 1} de {props.totalPages}
      </PageStatus>
      <Button
        variant="secondary"
        disabled={props.last}
        aria-disabled={props.busy || undefined}
        onClick={() => props.onStep(1)}
      >
        Próxima
      </Button>
    </nav>
  );
}
