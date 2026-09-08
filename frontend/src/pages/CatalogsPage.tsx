import { useState, type FormEvent } from 'react';
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
  const toggle = useMutation({
    mutationFn: () => setCatalogActive(kind, item, !item.active),
    onSuccess: async () => invalidate(),
  });
  return (
    <li>
      <span>
        <strong>{item.name}</strong>
        <small>{item.code}</small>
      </span>
      <Badge positive={item.active}>{item.active ? 'Ativo' : 'Inativo'}</Badge>
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
    </li>
  );
}
