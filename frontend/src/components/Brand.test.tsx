import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Brand } from './Brand';

describe('Delarte Control branding', () => {
  it('renders the official product name', () => {
    render(<Brand />);
    expect(screen.getByText(/Delarte Control/)).toBeInTheDocument();
  });
});
