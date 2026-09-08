import { render, screen } from '@testing-library/react';
import { BottomSheet, BottomSheetContent } from './bottom-sheet';
import { Drawer, DrawerContent } from './drawer';
import { Select } from './select';

describe('official visual foundation components', () => {
  it('provides a labeled, styled select without inventing catalog data', () => {
    render(
      <label>
        Modelo
        <Select defaultValue="">
          <option value="">Selecione o modelo</option>
        </Select>
      </label>,
    );

    expect(screen.getByRole('combobox', { name: 'Modelo' })).toHaveClass('select');
    expect(screen.getAllByRole('option')).toHaveLength(1);
  });

  it('distinguishes drawer and bottom-sheet placements behind the internal boundary', () => {
    const { rerender } = render(
      <Drawer open>
        <DrawerContent title="Navegação" description="Escolha uma área.">
          <a href="/dashboard">Dashboard</a>
        </DrawerContent>
      </Drawer>,
    );

    expect(screen.getByRole('dialog', { name: 'Navegação' })).toHaveAttribute(
      'data-placement',
      'drawer',
    );

    rerender(
      <BottomSheet open>
        <BottomSheetContent title="Seleção" description="Escolha uma opção.">
          <button type="button">Confirmar</button>
        </BottomSheetContent>
      </BottomSheet>,
    );

    expect(screen.getByRole('dialog', { name: 'Seleção' })).toHaveAttribute(
      'data-placement',
      'bottom-sheet',
    );
  });
});
