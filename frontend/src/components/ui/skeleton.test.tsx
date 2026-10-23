import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { render, screen } from '@testing-library/react';
import {
  DeviceGridSkeleton,
  ListSkeleton,
  PageSkeleton,
  PageStatus,
  RefreshStatus,
  ValueSkeleton,
} from './skeleton';

/** Flat `selector { declarations }` pairs of the global stylesheet, including those inside @media. */
function cssRules() {
  // jsdom applies no stylesheet; the rules are checked where they are defined. Vitest runs from
  // frontend/, the project root of every test script.
  const css = readFileSync(resolve(process.cwd(), 'src/styles/globals.css'), 'utf8');
  return Array.from(css.matchAll(/([^{}]+)\{([^{}]*)\}/g), ([, selector, body]) => ({
    selector: selector!.replace(/\/\*[\s\S]*?\*\//g, '').trim(),
    body: body!,
  }));
}
const dimming = /(^|[;\s])(opacity|filter|color)\s*:/;

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

  it('swaps the page position for a visible refresh mark that is not announced twice', () => {
    const { container, rerender } = render(<PageStatus busy={false}>Página 1 de 3</PageStatus>);
    const status = container.querySelector('.page-status');
    expect(status).not.toHaveAttribute('data-busy');
    expect(screen.getByText('Página 1 de 3')).toHaveClass('page-status-position');
    // The mark is always rendered, so both labels reserve the same space and nothing shifts.
    const mark = screen.getByText('Atualizando…');
    expect(mark).toHaveAttribute('aria-hidden', 'true');
    expect(mark.querySelector('.spinner')).not.toBeNull();

    rerender(<PageStatus busy>Página 1 de 3</PageStatus>);
    expect(status).toHaveAttribute('data-busy');
    expect(screen.getByText('Atualizando…')).toBe(mark);
  });

  it('never fades or recolours content while its next page loads', () => {
    const rules = cssRules();
    const busy = rules.filter((rule) => /aria-busy/.test(rule.selector));
    busy.forEach((rule) => expect(rule.body).not.toMatch(dimming));
    const indicator = rules.filter((rule) => rule.selector.includes('.page-status'));
    expect(indicator.length).toBeGreaterThan(0);
    indicator.forEach((rule) => expect(rule.body).not.toMatch(/(^|[;\s])(opacity|filter)\s*:/));
  });
});
