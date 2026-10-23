import { render, screen } from '@testing-library/react';
import {
  DeviceGridSkeleton,
  ListSkeleton,
  PageSkeleton,
  RefreshStatus,
  ValueSkeleton,
} from './skeleton';

describe('loading skeletons', () => {
  it.each([
    ['grid', <DeviceGridSkeleton key="grid" label="Carregando aparelhos…" />],
    ['list', <ListSkeleton key="list" label="Carregando histórico…" />],
    ['page', <PageSkeleton key="page" label="Carregando venda…" />],
  ])('%s names the loading state once and hides the placeholder shapes', (_, element) => {
    const { container } = render(element);
    const status = screen.getByRole('status');
    expect(status).toHaveTextContent(/^Carregando/);
    expect(container.querySelectorAll('.skeleton').length).toBeGreaterThan(0);
    container.querySelectorAll('.skeleton').forEach((shape) => {
      expect(shape.closest('[aria-hidden="true"]')).not.toBeNull();
    });
  });

  it('gives each list row an explicit flexible main area beside a separate pill', () => {
    const { container } = render(<ListSkeleton label="Carregando histórico…" rows={2} />);
    const rows = container.querySelectorAll('.skeleton-row');
    expect(rows).toHaveLength(2);
    rows.forEach((row) => {
      const [main, pill, ...rest] = Array.from(row.children);
      expect(rest).toHaveLength(0);
      expect(main).toHaveClass('skeleton-row-main');
      expect(main!.querySelectorAll('.skeleton')).toHaveLength(2);
      // The pill is not the flexible area: it keeps its own fixed-size class.
      expect(pill).toHaveClass('skeleton', 'skeleton-pill');
      expect(pill).not.toHaveClass('skeleton-row-main');
    });
  });

  it('keeps value placeholders out of the accessibility tree', () => {
    const { container } = render(<ValueSkeleton />);
    expect(container.firstChild).toHaveAttribute('aria-hidden', 'true');
  });

  it('announces a refresh only while it is active', () => {
    const { rerender } = render(<RefreshStatus active={false} label="Atualizando usuários…" />);
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
    rerender(<RefreshStatus active label="Atualizando usuários…" />);
    expect(screen.getByRole('status')).toHaveTextContent('Atualizando usuários…');
  });
});
