import {
  IconLayoutDashboard,
  IconDeviceMobile,
  IconWallet,
  IconHistory,
  IconUsers,
  IconSettings,
} from '@tabler/icons-react';
export const navigation = [
  { to: '/dashboard', label: 'Dashboard', icon: IconLayoutDashboard, ready: true },
  { to: '/devices', label: 'Aparelhos', icon: IconDeviceMobile, ready: true },
  { to: '/settings/catalogs', label: 'Catálogos', icon: IconSettings, ready: true },
  { to: '/financial', label: 'Financeiro', icon: IconWallet, ready: false },
  { to: '/history', label: 'Histórico', icon: IconHistory, ready: false },
  { to: '/settings/users', label: 'Usuários', icon: IconUsers, ready: false },
];
