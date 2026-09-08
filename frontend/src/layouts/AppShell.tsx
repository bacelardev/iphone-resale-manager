import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { IconMenu2, IconLogout, IconChevronRight, IconLock } from '@tabler/icons-react';
import { Brand } from '@/components/Brand';
import { Button } from '@/components/ui/button';
import { Drawer, DrawerContent, DrawerTrigger } from '@/components/ui/drawer';
import { useAuth } from '@/features/auth/context';
import { navigation } from '@/routes/navigation';
import { InitializationBanner } from '@/features/devices/InitializationBanner';

function Navigation({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav aria-label="Navegação principal">
      {navigation.map(({ to, label, icon: Icon, ready }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) => `nav-link ${isActive ? 'nav-active' : ''}`}
          onClick={onNavigate}
        >
          <Icon size={20} stroke={1.6} aria-hidden />
          <span>{label}</span>
          {!ready && <span className="nav-upcoming" aria-label="Em breve" />}
        </NavLink>
      ))}
    </nav>
  );
}

export function AppShell() {
  const { user, logout, loggingOut } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const title =
    navigation.find((item) => location.pathname.startsWith(item.to))?.label ?? 'Seu espaço';
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Pular para o conteúdo
      </a>
      <aside className="sidebar">
        <Brand />
        <p className="nav-section-label">ESPAÇO DE TRABALHO</p>
        <Navigation />
        <div className="sidebar-bottom">
          <div className="workspace-mark">
            <span className="status-dot" /> Espaço dos sócios
          </div>
          <p>Organização para ir mais longe.</p>
        </div>
      </aside>
      <div className="shell-body">
        <header className="app-header">
          <div className="header-path">
            <Drawer open={open} onOpenChange={setOpen}>
              <DrawerTrigger>
                <Button className="mobile-menu" variant="ghost" size="icon" aria-label="Abrir menu">
                  <IconMenu2 size={21} aria-hidden />
                </Button>
              </DrawerTrigger>
              <DrawerContent title="Seu espaço" description="Navegue pelos módulos do sistema.">
                <Navigation onNavigate={() => setOpen(false)} />
              </DrawerContent>
            </Drawer>
            <span className="breadcrumb-root">Espaço de trabalho</span>
            <IconChevronRight className="breadcrumb-root" size={14} aria-hidden />
            <span>{title}</span>
          </div>
          <div className="header-user">
            <div className="user-avatar" aria-hidden>
              {user?.name.slice(0, 1).toUpperCase()}
            </div>
            <div className="user-name">
              <strong>{user?.name}</strong>
              <span>Sócio</span>
            </div>
            <Button
              variant="ghost"
              size="icon"
              disabled={loggingOut}
              onClick={() => {
                void logout();
              }}
              aria-label={loggingOut ? 'Saindo' : 'Sair'}
            >
              <IconLogout size={19} aria-hidden />
            </Button>
          </div>
        </header>
        <main id="main-content" className="main-content">
          <InitializationBanner />
          <Outlet />
        </main>
        <footer className="app-footer">
          <span>iPhone Resale</span>
          <span>
            <IconLock size={13} aria-hidden /> Acesso restrito aos sócios
          </span>
        </footer>
      </div>
    </div>
  );
}
