import { loginFormSchema } from './schema';
describe('login form boundaries', () => {
  it.each([12, 127, 128])('accepts %i characters', (length) => {
    expect(
      loginFormSchema.safeParse({ username: 'socio', password: 'x'.repeat(length) }).success,
    ).toBe(true);
  });
  it.each([11, 129])('rejects %i characters', (length) => {
    expect(
      loginFormSchema.safeParse({ username: 'socio', password: 'x'.repeat(length) }).success,
    ).toBe(false);
  });
  it('preserves unicode and the entire password without trimming', () => {
    const password = ' á漢ç€'.repeat(20);
    expect(loginFormSchema.parse({ username: ' SOCIO ', password })).toEqual({
      username: 'SOCIO',
      password,
    });
  });
});
