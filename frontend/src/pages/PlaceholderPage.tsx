import { IconLayersSubtract } from '@tabler/icons-react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/empty-state';
import { PageHeader } from '@/components/ui/page-header';
export function PlaceholderPage({ title, description }: { title: string; description: string }) {
  return (
    <>
      <PageHeader
        eyebrow="SEU ESPAÇO"
        title={title}
        description={description}
        action={<Badge>Em breve</Badge>}
      />
      <Card>
        <EmptyState
          icon={<IconLayersSubtract size={32} stroke={1.2} />}
          title="O próximo passo está chegando."
          description="Este espaço está reservado. As funcionalidades serão disponibilizadas em uma próxima etapa."
        />
      </Card>
    </>
  );
}
