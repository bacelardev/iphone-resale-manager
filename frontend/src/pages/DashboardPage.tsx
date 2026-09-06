import { Link } from 'react-router-dom';
import {
  IconArrowUpRight,
  IconDeviceMobile,
  IconWallet,
  IconHistory,
  IconArrowRight,
  IconLayoutGrid,
} from '@tabler/icons-react';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { PageHeader } from '@/components/ui/page-header';
import { useAuth } from '@/features/auth/context';

const modules = [
  {
    to: '/devices',
    icon: IconDeviceMobile,
    title: 'Seus aparelhos',
    text: 'Cada iPhone, do início ao próximo dono.',
    tag: 'APARELHOS',
  },
  {
    to: '/financial',
    icon: IconWallet,
    title: 'Sua visão financeira',
    text: 'Mais clareza para cada decisão.',
    tag: 'FINANCEIRO',
  },
  {
    to: '/history',
    icon: IconHistory,
    title: 'Cada movimento',
    text: 'O caminho da sua operação, registrado.',
    tag: 'HISTÓRICO',
  },
];
export function DashboardPage() {
  const { user } = useAuth();
  return (
    <div className="dashboard-page">
      <PageHeader
        eyebrow="SEU PAINEL"
        title={`Olá, ${user?.name.split(' ')[0] ?? 'sócio'}.`}
        description="Tudo começa com uma visão mais clara."
        action={
          <Badge positive>
            <span className="status-dot" /> Acesso confirmado
          </Badge>
        }
      />
      <Card className="welcome-card">
        <div className="welcome-copy">
          <span className="eyebrow">UM NOVO ESPAÇO PARA SUA OPERAÇÃO</span>
          <h2>
            Organize o presente.
            <br />
            <span>Prepare o próximo passo.</span>
          </h2>
          <p>
            Seu espaço está pronto. Em breve, aparelhos, finanças e histórico estarão conectados
            aqui.
          </p>
          <div className="welcome-status">
            <span className="status-dot" /> Você está conectado como sócio
          </div>
        </div>
        <div className="welcome-art" aria-hidden>
          <div className="art-frame">
            <span className="art-top">VISÃO. CONTROLE. EVOLUÇÃO.</span>
            <IconLayoutGrid size={72} stroke={0.8} />
            <span className="art-bottom">
              iR<span>↗</span>
            </span>
          </div>
        </div>
      </Card>
      <div className="section-heading">
        <h2>Explore seu espaço</h2>
        <span>Construído para evoluir com você</span>
      </div>
      <div className="module-grid">
        {modules.map(({ to, icon: Icon, title, text, tag }) => (
          <Link key={to} to={to} className="module-card">
            <div className="module-top">
              <span className="module-icon">
                <Icon size={23} stroke={1.5} aria-hidden />
              </span>
              <IconArrowUpRight size={20} stroke={1.5} aria-hidden />
            </div>
            <p className="eyebrow">{tag}</p>
            <h3>{title}</h3>
            <p>{text}</p>
            <div className="module-bottom">
              <Badge>Em breve</Badge>
              <IconArrowRight size={18} aria-hidden />
            </div>
          </Link>
        ))}
      </div>
      <div className="dashboard-note">
        <span className="note-line" />
        <p>Uma operação bem cuidada começa nos detalhes.</p>
        <span className="note-line" />
      </div>
    </div>
  );
}
