import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { IconPalette, IconPlus, IconToggleLeft, IconToggleRight } from '@tabler/icons-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import {
  createColor,
  createModel,
  listColors,
  listModels,
  setCatalogActive,
  updateCatalog,
} from '@/features/devices/api';
import type { CatalogItem } from '@/types/stage-g';

export function CatalogsPage() {
  const client = useQueryClient();
  const models = useQuery({ queryKey: ['models', 'all'], queryFn: () => listModels() });
  const colors = useQuery({ queryKey: ['colors', 'all'], queryFn: () => listColors() });
  return (
    <div className="catalogs-page">
      <PageHeader
        eyebrow="CONFIGURAÇÕES"
        title="Catálogos de aparelhos"
        description="Modelos e cores reais usados no cadastro, sem exclusão de histórico."
      />
      <div className="catalog-layout">
        <CatalogSection
          title="Modelos"
          description="A ordem controla como os modelos aparecem no seletor."
          items={models.data?.content ?? []}
          loading={models.isLoading}
          error={models.error}
          fields="model"
          create={(input) => createModel({ ...input, displayOrder: input.displayOrder ?? 0 })}
          invalidate={() => client.invalidateQueries({ queryKey: ['models'] })}
        />
        <CatalogSection
          title="Cores"
          description="Acabamentos disponíveis para os aparelhos."
          items={colors.data?.content ?? []}
          loading={colors.isLoading}
          error={colors.error}
          fields="color"
          create={createColor}
          invalidate={() => client.invalidateQueries({ queryKey: ['colors'] })}
        />
      </div>
    </div>
  );
}
function CatalogSection({
  title,
  description,
  items,
  loading,
  error,
  fields,
  create,
  invalidate,
}: {
  title: string;
  description: string;
  items: CatalogItem[];
  loading: boolean;
  error: unknown;
  fields: 'model' | 'color';
  create: (input: { code: string; name: string; displayOrder?: number }) => Promise<CatalogItem>;
  invalidate: () => Promise<unknown>;
}) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [order, setOrder] = useState('0');
  const creation = useMutation({
    mutationFn: () =>
      fields === 'model'
        ? create({ code, name, displayOrder: Number(order) })
        : create({ code, name }),
    onSuccess: async () => {
      setCode('');
      setName('');
      setOrder('0');
      await invalidate();
    },
  });
  function submit(event: FormEvent) {
    event.preventDefault();
    creation.mutate();
  }
  return (
    <Card className="catalog-section">
      <div className="catalog-heading">
        <span className="catalog-icon">
          <IconPalette size={21} aria-hidden />
        </span>
        <div>
          <h2>{title}</h2>
          <p>{description}</p>
        </div>
      </div>
      <form className="catalog-form" onSubmit={submit}>
        <FormField id={`${fields}-code`} label="Código">
          <Input
            id={`${fields}-code`}
            value={code}
            onChange={(event) => setCode(event.target.value.toUpperCase())}
            maxLength={60}
            placeholder="EXEMPLO_CODIGO"
            required
          />
        </FormField>
        <FormField id={`${fields}-name`} label="Nome">
          <Input
            id={`${fields}-name`}
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={fields === 'model' ? 100 : 80}
            required
          />
        </FormField>
        {fields === 'model' && (
          <FormField id="model-order" label="Ordem">
            <Input
              id="model-order"
              type="number"
              min="0"
              value={order}
              onChange={(event) => setOrder(event.target.value)}
              required
            />
          </FormField>
        )}
        <Button type="submit" disabled={creation.isPending}>
          <IconPlus size={18} aria-hidden /> Adicionar
        </Button>
      </form>
      {creation.error && <ErrorState error={creation.error} />}
      {error != null && <ErrorState error={error} />}
      {loading && <p className="loading-copy">Carregando catálogo…</p>}
      <ul className="catalog-list">
        {items.map((item) => (
          <CatalogRow
            key={item.id}
            kind={fields === 'model' ? 'models' : 'colors'}
            item={item}
            invalidate={invalidate}
          />
        ))}
      </ul>
    </Card>
  );
}
function CatalogRow({
  kind,
  item,
  invalidate,
}: {
  kind: 'models' | 'colors';
  item: CatalogItem;
  invalidate: () => Promise<unknown>;
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(item.name);
  const [order, setOrder] = useState(String(item.displayOrder ?? 0));
  useEffect(() => {
    setName(item.name);
    setOrder(String(item.displayOrder ?? 0));
  }, [item]);
  const update = useMutation({
    mutationFn: () =>
      updateCatalog(kind, item, {
        name,
        ...(kind === 'models' ? { displayOrder: Number(order) } : {}),
      }),
    onSuccess: async () => {
      setEditing(false);
      await invalidate();
    },
  });
  const toggle = useMutation({
    mutationFn: () => setCatalogActive(kind, item, !item.active),
    onSuccess: async () => invalidate(),
  });
  return (
    <li>
      {editing ? (
        <form
          className="catalog-edit-form"
          onSubmit={(event) => {
            event.preventDefault();
            update.mutate();
          }}
        >
          <label>
            <span>Nome de {kind === 'models' ? 'modelo' : 'cor'}</span>
            <Input
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={kind === 'models' ? 100 : 80}
              required
            />
          </label>
          {kind === 'models' && (
            <label>
              <span>Ordem do modelo</span>
              <Input
                type="number"
                min="0"
                value={order}
                onChange={(event) => setOrder(event.target.value)}
                required
              />
            </label>
          )}
          <div className="catalog-edit-actions">
            <Button type="submit" disabled={update.isPending}>
              Salvar
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setEditing(false);
                setName(item.name);
                setOrder(String(item.displayOrder ?? 0));
              }}
            >
              Cancelar
            </Button>
          </div>
          {update.error && <ErrorState error={update.error} />}
        </form>
      ) : (
        <>
          <span>
            <strong>{item.name}</strong>
            <small>
              {item.code}
              {kind === 'models' ? ` · ordem ${item.displayOrder ?? 0}` : ''}
            </small>
          </span>
          <Badge positive={item.active}>{item.active ? 'Ativo' : 'Inativo'}</Badge>
          <Button
            variant="ghost"
            onClick={() => setEditing(true)}
            aria-label={`Editar ${item.name}`}
          >
            Editar
          </Button>
          <Button
            variant="ghost"
            size="icon"
            disabled={toggle.isPending}
            onClick={() => toggle.mutate()}
            aria-label={`${item.active ? 'Desativar' : 'Ativar'} ${item.name}`}
          >
            {item.active ? (
              <IconToggleRight size={22} aria-hidden />
            ) : (
              <IconToggleLeft size={22} aria-hidden />
            )}
          </Button>
        </>
      )}
    </li>
  );
}
