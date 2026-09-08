import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CatalogPicker } from './CatalogPicker';

const items = [
  {
    id: '11111111-1111-4111-8111-111111111111',
    code: 'IPHONE_15',
    name: 'iPhone 15',
    active: true,
    displayOrder: 1,
    createdAt: '2026-09-08T12:00:00Z',
    updatedAt: '2026-09-08T12:00:00Z',
    version: 0,
  },
  {
    id: '22222222-2222-4222-8222-222222222222',
    code: 'IPHONE_15_PRO',
    name: 'iPhone 15 Pro',
    active: true,
    displayOrder: 2,
    createdAt: '2026-09-08T12:00:00Z',
    updatedAt: '2026-09-08T12:00:00Z',
    version: 0,
  },
];

describe('CatalogPicker', () => {
  it('searches the real catalog and returns the selected identifier', async () => {
    const actor = userEvent.setup();
    const onChange = vi.fn();
    render(<CatalogPicker label="Modelo" items={items} value="" onChange={onChange} />);
    await actor.click(screen.getByRole('button', { name: /Selecionar modelo/i }));
    await actor.type(screen.getByRole('textbox', { name: 'Buscar modelo' }), 'Pro');
    expect(screen.queryByRole('option', { name: /iPhone 15 IPHONE_15$/ })).not.toBeInTheDocument();
    await actor.click(screen.getByRole('option', { name: /iPhone 15 Pro/ }));
    expect(onChange).toHaveBeenCalledWith(items[1]?.id);
  });

  it('closes with Escape and restores focus to the trigger', async () => {
    const actor = userEvent.setup();
    render(<CatalogPicker label="Modelo" items={items} value="" onChange={vi.fn()} />);
    const trigger = screen.getByRole('button', { name: /Selecionar modelo/i });
    await actor.click(trigger);
    await actor.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});
