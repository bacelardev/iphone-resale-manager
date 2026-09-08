import { IconAlertCircle } from '@tabler/icons-react';
import { ApiRequestError, errorMessage } from '@/lib/api/errors';
import { Button } from './button';
export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div role="alert" className="error-state">
      <IconAlertCircle size={20} aria-hidden />
      <div>
        <p>{errorMessage(error)}</p>
        {error instanceof ApiRequestError && (
          <p className="request-id">Referência: {error.detail.requestId}</p>
        )}
        {retry && (
          <Button variant="secondary" onClick={retry}>
            Tentar novamente
          </Button>
        )}
      </div>
    </div>
  );
}
