import { AppShell, Group, Text, ThemeIcon, UnstyledButton, Flex, ActionIcon } from '@mantine/core';
import { IconDashboard, IconPlugConnected, IconPipeline, IconPlayerPlay, IconChartLine, IconShieldCheck, IconReportAnalytics, IconSettings, IconPackage, IconHierarchy, IconCloud, IconUsers, IconLogout } from '@tabler/icons-react';
import { Outlet, useNavigate, useLocation } from 'react-router';
import { useAuth } from '../../auth/AuthContext';
import { AiFloatingButton } from '../ai/AiFloatingButton';

const navItems = [
  { label: 'Dashboard', icon: IconDashboard, path: '/dashboard' },
  { label: 'Connections', icon: IconPlugConnected, path: '/connections' },
  { label: 'Pipelines', icon: IconPipeline, path: '/pipelines' },
  { label: 'Execution', icon: IconPlayerPlay, path: '/execution' },
  { label: 'Monitoring', icon: IconChartLine, path: '/monitoring' },
  { label: 'Audit', icon: IconShieldCheck, path: '/audit' },
  { label: 'Users', icon: IconUsers, path: '/users', adminOnly: true },
  { label: 'Diagnostics', icon: IconReportAnalytics, path: '/diagnostics' },
  { label: 'Workflows', icon: IconHierarchy, path: '/workflows' },
  { label: 'Agents', icon: IconCloud, path: '/agents', adminOnly: true },
  { label: 'Admin', icon: IconSettings, path: '/admin', adminOnly: true },
  { label: 'Plugins', icon: IconPackage, path: '/marketplace' },
  { label: 'Dead Letter Queue', icon: IconReportAnalytics, path: '/dlq' },
];

export function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAdmin, logout } = useAuth();
  const visibleNav = navItems.filter((item) => !item.adminOnly || isAdmin);

  return (
    <AppShell
      navbar={{ width: 240, breakpoint: 'sm' }}
      header={{ height: 56 }}
      padding="md"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group>
            <ThemeIcon size="lg" variant="gradient" gradient={{ from: 'blue', to: 'cyan' }}>
              <IconPipeline size={20} />
            </ThemeIcon>
            <Text fw={700} size="lg">SyncFlow</Text>
          </Group>
          <ActionIcon variant="subtle" onClick={logout} aria-label="Sign out">
            <IconLogout size={18} />
          </ActionIcon>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <Flex direction="column" gap={4}>
          {visibleNav.map((item) => {
            const active = location.pathname.startsWith(item.path);
            return (
              <UnstyledButton
                key={item.path}
                onClick={() => navigate(item.path)}
                style={(theme) => ({
                  display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px',
                  borderRadius: 8, width: '100%',
                  backgroundColor: active ? theme.colors.blue[8] : 'transparent',
                  color: active ? theme.white : theme.colors.gray[3],
                  '&:hover': { backgroundColor: active ? theme.colors.blue[8] : theme.colors.dark[5] },
                })}
              >
                <item.icon size={18} />
                <Text size="sm">{item.label}</Text>
              </UnstyledButton>
            );
          })}
        </Flex>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
        <AiFloatingButton />
      </AppShell.Main>
    </AppShell>
  );
}
