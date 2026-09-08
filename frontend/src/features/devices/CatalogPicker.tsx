import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { IconCheck, IconChevronDown, IconSearch, IconX } from '@tabler/icons-react';
import type { CatalogItem } from '@/types/stage-g';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export function CatalogPicker({
  label,
  items,
  value,
  onChange,
  disabled,
}: {
  label: string;
  items: CatalogItem[];
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const selected = items.find((item) => item.id === value);
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('pt-BR');
    return term
      ? items.filter((item) => `${item.name} ${item.code}`.toLowerCase().includes(term))
      : items;
  }, [items, search]);

  useEffect(() => {
    if (!open) return;
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        trigger.current?.focus();
      }
    };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [open]);

  return (
    <div className="catalog-picker">
      <span className="picker-label" id={`${id}-label`}>
        {label}
      </span>
      <button
        ref={trigger}
        type="button"
        className="picker-trigger"
        aria-labelledby={`${id}-label ${id}-value`}
        aria-haspopup="listbox"
        aria-expanded={open}
        disabled={disabled}
        onClick={() => setOpen((current) => !current)}
      >
        <span id={`${id}-value`}>{selected?.name ?? `Selecionar ${label.toLowerCase()}`}</span>
        <IconChevronDown size={18} aria-hidden />
      </button>
      {open && (
        <>
          <button
            className="picker-overlay"
            type="button"
            aria-label={`Fechar seletor de ${label.toLowerCase()}`}
            onClick={() => setOpen(false)}
          />
          <div
            className="picker-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby={`${id}-title`}
          >
            <div className="picker-heading">
              <strong id={`${id}-title`}>{label}</strong>
              <Button
                variant="ghost"
                size="icon"
                onClick={() => setOpen(false)}
                aria-label="Fechar"
              >
                <IconX size={19} aria-hidden />
              </Button>
            </div>
            <div className="picker-search">
              <IconSearch size={17} aria-hidden />
              <Input
                autoFocus
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={`Buscar ${label.toLowerCase()}`}
                aria-label={`Buscar ${label.toLowerCase()}`}
              />
            </div>
            <div className="picker-options" role="listbox" aria-labelledby={`${id}-label`}>
              {filtered.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  role="option"
                  aria-selected={item.id === value}
                  onClick={() => {
                    onChange(item.id);
                    setOpen(false);
                    setSearch('');
                    trigger.current?.focus();
                  }}
                >
                  <span>
                    <strong>{item.name}</strong>
                    <small>{item.code}</small>
                  </span>
                  {item.id === value && <IconCheck size={18} aria-hidden />}
                </button>
              ))}
              {filtered.length === 0 && (
                <p className="picker-empty">Nenhum resultado encontrado.</p>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
