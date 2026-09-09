import { useEffect, useId, useMemo, useState } from 'react';
import { IconCheck, IconChevronDown, IconSearch } from '@tabler/icons-react';
import type { CatalogItem } from '@/types/stage-g';
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog';
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
  const [activeIndex, setActiveIndex] = useState(0);
  const id = useId();
  const selected = items.find((item) => item.id === value);
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('pt-BR');
    return term
      ? items.filter((item) =>
          `${item.name} ${item.code}`.toLocaleLowerCase('pt-BR').includes(term),
        )
      : items;
  }, [items, search]);
  useEffect(() => {
    if (!open) return;
    const selectedIndex = filtered.findIndex((item) => item.id === value);
    setActiveIndex(selectedIndex >= 0 ? selectedIndex : 0);
  }, [filtered, open, value]);
  function choose(item: CatalogItem | undefined) {
    if (!item) return;
    onChange(item.id);
    setOpen(false);
  }
  return (
    <div className="catalog-picker">
      <span className="picker-label" id={`${id}-label`}>
        {label}
      </span>
      <Dialog
        open={open}
        onOpenChange={(nextOpen) => {
          setOpen(nextOpen);
          if (!nextOpen) setSearch('');
        }}
      >
        <DialogTrigger>
          <button
            type="button"
            className="picker-trigger"
            aria-labelledby={`${id}-label ${id}-value`}
            aria-haspopup="dialog"
            aria-expanded={open}
            disabled={disabled}
          >
            <span id={`${id}-value`}>{selected?.name ?? `Selecionar ${label.toLowerCase()}`}</span>
            <IconChevronDown size={18} aria-hidden />
          </button>
        </DialogTrigger>
        <DialogContent
          title={label}
          description={`Pesquise e selecione ${label.toLowerCase()} no catálogo.`}
          placement="bottom-sheet"
          className="picker-dialog"
        >
          <div className="picker-search">
            <IconSearch size={17} aria-hidden />
            <Input
              autoFocus
              role="combobox"
              aria-label={`Buscar ${label.toLowerCase()}`}
              aria-controls={`${id}-options`}
              aria-expanded="true"
              aria-autocomplete="list"
              aria-activedescendant={
                filtered[activeIndex] ? `${id}-option-${filtered[activeIndex].id}` : undefined
              }
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setActiveIndex(0);
              }}
              onKeyDown={(event) => {
                if (event.key === 'ArrowDown') {
                  event.preventDefault();
                  setActiveIndex((current) => Math.min(current + 1, filtered.length - 1));
                } else if (event.key === 'ArrowUp') {
                  event.preventDefault();
                  setActiveIndex((current) => Math.max(current - 1, 0));
                } else if (event.key === 'Home') {
                  event.preventDefault();
                  setActiveIndex(0);
                } else if (event.key === 'End') {
                  event.preventDefault();
                  setActiveIndex(Math.max(filtered.length - 1, 0));
                } else if (event.key === 'Enter') {
                  event.preventDefault();
                  choose(filtered[activeIndex]);
                }
              }}
              placeholder={`Buscar ${label.toLowerCase()}`}
            />
          </div>
          <div
            className="picker-options"
            id={`${id}-options`}
            role="listbox"
            aria-labelledby={`${id}-label`}
          >
            {filtered.map((item, index) => (
              <button
                key={item.id}
                id={`${id}-option-${item.id}`}
                type="button"
                role="option"
                tabIndex={-1}
                aria-selected={item.id === value}
                data-active={index === activeIndex}
                onMouseMove={() => setActiveIndex(index)}
                onClick={() => choose(item)}
              >
                <span>
                  <strong>{item.name}</strong>
                  <small>{item.code}</small>
                </span>
                {item.id === value && <IconCheck size={18} aria-hidden />}
              </button>
            ))}
            {filtered.length === 0 && <p className="picker-empty">Nenhum resultado encontrado.</p>}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
