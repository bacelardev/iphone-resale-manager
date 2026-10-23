import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { render, screen } from '@testing-library/react';
import { AnimatedValue } from './animated-value';

describe('AnimatedValue', () => {
  it('renders the first value without motion and replays motion only when the value changes', () => {
    const { rerender } = render(<AnimatedValue value="R$ 10,00" />);
    const element = screen.getByText('R$ 10,00');
    expect(element).not.toHaveAttribute('data-motion');

    rerender(<AnimatedValue value="R$ 10,00" />);
    expect(element).not.toHaveAttribute('data-motion');

    rerender(<AnimatedValue value="R$ 12,50" />);
    expect(screen.getByText('R$ 12,50')).toBe(element);
    expect(element).toHaveAttribute('data-motion', 'a');

    rerender(<AnimatedValue value="R$ 9,00" />);
    expect(screen.getByText('R$ 9,00')).toBe(element);
    expect(element).toHaveAttribute('data-motion', 'b');
  });

  it('moves the value without fading it, so its contrast never drops during the update', () => {
    // jsdom applies no stylesheet; the keyframes are checked where they are defined. Vitest runs
    // from frontend/, the project root of every test script.
    const css = readFileSync(resolve(process.cwd(), 'src/styles/globals.css'), 'utf8');
    for (const name of ['value-update-a', 'value-update-b']) {
      const keyframes = new RegExp(`@keyframes ${name}\\s*\\{([\\s\\S]*?)\\n\\}`).exec(css)?.[1];
      expect(keyframes).toMatch(/translate:\s*0 4px/);
      expect(keyframes).not.toMatch(/opacity/);
    }
  });
});
