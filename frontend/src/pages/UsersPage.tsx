import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { IconPlus, IconUser } from '@tabler/icons-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Dialog, DialogContent } from '@/components/ui/dialog';
import { ErrorState } from '@/components/ui/error-state';
import { FormField } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { PageHeader } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { useAuth } from '@/features/auth/context';
import { createUser, listUsers, setUserActive, updateUser } from '@/features/users/api';
import type { AppUser } from '@/types/stage-k';

export function UsersPage() {
  const client = useQueryClient();
  const { user: currentUser } = useAuth();
  const [search, setSearch] = useState('');
  const [active, setActive] = useState<'' | 'true' | 'false'>('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AppUser | 'create' | null>(null);
  const users = useQuery({
    queryKey: ['users', search, active, page],
    queryFn: () =>
      listUsers({
        search: search || undefined,
        active: active === '' ? undefined : active === 'true',
        page,
      }),
  });
  const toggle = useMutation({
    mutationFn: ({ target, value }: { target: AppUser; value: boolean }) =>
      setUserActive(target, value),
    onSuccess: async () => client.invalidateQueries({ queryKey: ['users'] }),
  });

  return (
    <div className="users-page">
      <PageHeader
        eyebrow="CONFIGURAÇÕES"
        title="Usuários"
        description="Acesso à operação sem expor senhas ou credenciais."
        action={
          <Button onClick={() => setEditing('create')}>
            <IconPlus size={18} aria-hidden /> Novo usuário
          </Button>
        }
      />
      <Card className="stage-k-filters">
        <div className="stage-k-filter-grid users-filter-grid">
          <FormField id="users-search" label="Buscar">
            <Input
              id="users-search"
              type="search"
              placeholder="Nome ou usuário"
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
            />
          </FormField>
          <FormField id="users-active" label="Situação">
            <Select
              id="users-active"
              value={active}
              onChange={(event) => {
                setActive(event.target.value as '' | 'true' | 'false');
                setPage(0);
              }}
            >
              <option value="">Todos</option>
              <option value="true">Ativos</option>
              <option value="false">Inativos</option>
            </Select>
          </FormField>
        </div>
      </Card>

      {(users.error || toggle.error) && (
        <ErrorState error={users.error ?? toggle.error} retry={() => void users.refetch()} />
      )}
      {users.isLoading && <p className="loading-copy">Carregando usuários…</p>}
      {users.data?.content.length === 0 && (
        <Card className="stage-k-empty">
          <IconUser size={28} aria-hidden />
          <strong>Nenhum usuário encontrado</strong>
          <p>Ajuste os filtros ou cadastre uma nova pessoa.</p>
        </Card>
      )}
      {!!users.data?.content.length && (
        <Card className="stage-k-list-card">
          <ul className="stage-k-list user-list" aria-live="polite">
            {users.data.content.map((item) => (
              <li key={item.id}>
                <div className="user-list-row">
                  <span>
                    <strong>{item.name}</strong>
                    <small>
                      @{item.username} · {item.role}
                    </small>
                  </span>
                  <span className={`user-state ${item.active ? 'active' : ''}`}>
                    {item.active ? 'Ativo' : 'Inativo'}
                  </span>
                  <div className="user-row-actions">
                    <Button variant="secondary" onClick={() => setEditing(item)}>
                      Editar
                    </Button>
                    <Button
                      variant="ghost"
                      disabled={toggle.isPending || (item.active && item.id === currentUser?.id)}
                      title={
                        item.id === currentUser?.id
                          ? 'Você não pode desativar a própria conta.'
                          : undefined
                      }
                      onClick={() => toggle.mutate({ target: item, value: !item.active })}
                    >
                      {item.active ? 'Desativar' : 'Ativar'}
                    </Button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
          <nav className="stage-k-pagination" aria-label="Paginação dos usuários">
            <Button
              variant="secondary"
              disabled={users.data.first}
              onClick={() => setPage(page - 1)}
            >
              Anterior
            </Button>
            <span>
              Página {users.data.page + 1} de {users.data.totalPages}
            </span>
            <Button
              variant="secondary"
              disabled={users.data.last}
              onClick={() => setPage(page + 1)}
            >
              Próxima
            </Button>
          </nav>
        </Card>
      )}

      <UserDialog
        value={editing}
        currentUserId={currentUser?.id ?? ''}
        onClose={() => setEditing(null)}
        onSaved={async () => {
          setEditing(null);
          await client.invalidateQueries({ queryKey: ['users'] });
        }}
      />
    </div>
  );
}

function UserDialog(props: {
  value: AppUser | 'create' | null;
  currentUserId: string;
  onClose(): void;
  onSaved(): Promise<void>;
}) {
  const existing = props.value && props.value !== 'create' ? props.value : null;
  return (
    <Dialog open={props.value !== null} onOpenChange={(open) => !open && props.onClose()}>
      <DialogContent
        title={existing ? 'Editar usuário' : 'Novo usuário'}
        description="O papel SOCIO é único nesta versão e não pode ser alterado."
      >
        {props.value && (
          <UserForm
            key={existing?.id ?? 'create'}
            value={existing}
            allowPassword={!existing || existing.id === props.currentUserId}
            onCancel={props.onClose}
            onSaved={props.onSaved}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function UserForm(props: {
  value: AppUser | null;
  allowPassword: boolean;
  onCancel(): void;
  onSaved(): Promise<void>;
}) {
  const [name, setName] = useState(props.value?.name ?? '');
  const [username, setUsername] = useState(props.value?.username ?? '');
  const [password, setPassword] = useState('');
  const save = useMutation({
    mutationFn: () =>
      props.value
        ? updateUser(props.value.id, {
            expectedVersion: props.value.version,
            name,
            username,
            newPassword: password || undefined,
          })
        : createUser({ name, username, password }),
    onSuccess: props.onSaved,
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    save.mutate();
  }

  return (
    <form className="stage-k-form" onSubmit={submit}>
      <FormField id="user-name" label="Nome">
        <Input
          id="user-name"
          required
          maxLength={120}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </FormField>
      <FormField id="user-username" label="Usuário">
        <Input
          id="user-username"
          required
          minLength={3}
          maxLength={50}
          pattern="[A-Za-z0-9._-]+"
          autoCapitalize="none"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
        />
      </FormField>
      {props.allowPassword && (
        <FormField
          id="user-password"
          label={props.value ? 'Nova senha (opcional)' : 'Senha inicial'}
        >
          <Input
            id="user-password"
            type="password"
            required={!props.value}
            minLength={12}
            maxLength={128}
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </FormField>
      )}
      {save.error && <ErrorState error={save.error} />}
      <div className="stage-k-form-actions">
        <Button type="button" variant="ghost" onClick={props.onCancel}>
          Cancelar
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Salvando…' : 'Salvar'}
        </Button>
      </div>
    </form>
  );
}
