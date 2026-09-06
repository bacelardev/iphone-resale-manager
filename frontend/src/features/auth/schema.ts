import { z } from 'zod';
export const loginFormSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, 'Informe seu usuário (mínimo de 3 caracteres).')
    .max(50, 'Use até 50 caracteres.')
    .regex(/^[A-Za-z0-9._-]+$/, 'Use letras, números, ponto, hífen ou sublinhado.'),
  password: z
    .string()
    .min(12, 'A senha deve ter no mínimo 12 caracteres.')
    .max(128, 'A senha deve ter no máximo 128 caracteres.')
    .refine((value) => value.trim().length > 0, 'Informe sua senha.'),
});
