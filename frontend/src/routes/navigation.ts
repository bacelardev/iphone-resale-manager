import {
  IconLayoutDashboard,
  IconDeviceMobile,
  IconWallet,
  IconHistory,
  IconUsers,
} from '@tabler/icons-react';
export const navigation = [
  { to: '/dashboard', label: 'Dashboard', icon: IconLayoutDashboard },
  { to: '/devices', label: 'Aparelhos', icon: IconDeviceMobile },
  { to: '/financial', label: 'Financeiro', icon: IconWallet },
  { to: '/history', label: 'Histórico', icon: IconHistory },
  { to: '/settings/users', label: 'Usuários', icon: IconUsers },
];
