import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  IconArrowRight,
  IconEye,
  IconEyeOff,
  IconLock,
  IconShieldCheck,
} from '@tabler/icons-react';
import { Brand } from '@/components/Brand';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { FormField } from '@/components/ui/form-field';
import { ErrorState } from '@/components/ui/error-state';
import { Spinner } from '@/components/ui/spinner';
import { useAuth } from '@/features/auth/context';
import { loginFormSchema } from '@/features/auth/schema';
import { SessionStorageError } from '@/lib/auth/token-store';
import type { LoginRequest } from '@/types/auth';

export function LoginPage() {
  const { login, notice } = useAuth();
  const [showPassword, setShowPassword] = useState(false);
  const [failure, setFailure] = useState<unknown>(null);
  const {
    register,
    handleSubmit,
    resetField,
    formState: { errors, isSubmitting },
  } = useForm<LoginRequest>({
    resolver: zodResolver(loginFormSchema),
    defaultValues: { username: '', password: '' },
  });
  const submit = handleSubmit(async (input) => {
    setFailure(null);
    try {
      await login(input);
      resetField('password');
    } catch (error) {
      setFailure(error);
      resetField('password');
    }
  });
  return (
    <main className="login-page">
      <section className="login-access" aria-label="Acesso ao sistema">
        <div className="login-brand">
          <Brand />
        </div>
        <div className="login-form-wrap">
          <div className="login-lock">
            <IconLock size={24} stroke={1.5} aria-hidden />
          </div>
          <p className="eyebrow">BEM-VINDO DE VOLTA</p>
          <h2>Entre no seu espaço.</h2>
          <p className="login-description">Use suas credenciais para continuar.</p>
          {notice && (
            <div className="session-notice" role="status">
              {notice}
            </div>
          )}
          <form onSubmit={submit} noValidate aria-label="Entrar no sistema">
            <FormField id="username" label="Usuário" error={errors.username?.message}>
              <Input
                id="username"
                autoComplete="username"
                autoCapitalize="none"
                spellCheck={false}
                placeholder="Seu usuário"
                aria-invalid={!!errors.username}
                aria-describedby={errors.username ? 'username-error' : undefined}
                {...register('username')}
              />
            </FormField>
            <FormField id="password" label="Senha" error={errors.password?.message}>
              <div className="password-input">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Sua senha"
                  aria-invalid={!!errors.password}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                  {...register('password')}
                />
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                  aria-pressed={showPassword}
                >
                  {showPassword ? (
                    <IconEyeOff size={19} aria-hidden />
                  ) : (
                    <IconEye size={19} aria-hidden />
                  )}
                </Button>
              </div>
            </FormField>
            {failure instanceof SessionStorageError ? (
              <p role="alert" className="field-error">
                {failure.message}
              </p>
            ) : failure ? (
              <ErrorState error={failure} />
            ) : null}
            <Button type="submit" className="login-submit" disabled={isSubmitting}>
              {isSubmitting ? (
                <>
                  <Spinner label="Entrando" /> Entrando…
                </>
              ) : (
                <>
                  Entrar <IconArrowRight size={18} aria-hidden />
                </>
              )}
            </Button>
          </form>
          <div className="login-security">
            <IconShieldCheck size={17} aria-hidden />
            <span>Acesso exclusivo aos sócios.</span>
          </div>
        </div>
        <p className="access-footer">
          iPhone Resale <span>·</span> Gestão com propósito.
        </p>
      </section>
    </main>
  );
}
