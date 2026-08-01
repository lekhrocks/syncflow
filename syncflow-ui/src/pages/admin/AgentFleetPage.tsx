import { Paper, Title, SimpleGrid, Text, Group, Badge, Table, Code, ThemeIcon, RingProgress, Tooltip } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { IconServer, IconCpu, IconDeviceSdCard } from '@tabler/icons-react';

const api = axios.create({ baseURL: '/api' });

const STATUS_COLORS: Record<string, string> = {
  ONLINE: 'green',
  OFFLINE: 'gray',
  DRAINING: 'yellow',
  UNREACHABLE: 'red',
  UPGRADING: 'blue',
  MAINTENANCE: 'orange',
};

export function AgentFleetPage() {
  const { data: agents } = useQuery({
    queryKey: ['agents'],
    queryFn: () => api.get('/agents').then(r => r.data),
    refetchInterval: 5000,
  });

  const onlineCount = agents?.filter((a: any) => a.status === 'ONLINE').length ?? 0;

  return (
    <div>
      <Title order={2} mb="lg">Agent Fleet</Title>

      <SimpleGrid cols={{ base: 1, lg: 4 }} mb="lg">
        <Paper p="md" radius="md" withBorder>
          <Group><ThemeIcon color="green"><IconServer size={20} /></ThemeIcon><div><Text size="xs" c="dimmed">Total Agents</Text><Text fw={700} size="xl">{agents?.length ?? 0}</Text></div></Group>
        </Paper>
        <Paper p="md" radius="md" withBorder>
          <Group><ThemeIcon color="green" variant="light"><IconServer size={20} /></ThemeIcon><div><Text size="xs" c="dimmed">Online</Text><Text fw={700} size="xl">{onlineCount}</Text></div></Group>
        </Paper>
        <Paper p="md" radius="md" withBorder>
          <Group><ThemeIcon color="yellow" variant="light"><IconDeviceSdCard size={20} /></ThemeIcon><div><Text size="xs" c="dimmed">Utilization</Text><Text fw={700} size="xl">{agents?.length > 0 ? Math.round(onlineCount / agents.length * 100) : 0}%</Text></div></Group>
        </Paper>
        <Paper p="md" radius="md" withBorder>
          <Group><ThemeIcon color="blue" variant="light"><IconCpu size={20} /></ThemeIcon><div><Text size="xs" c="dimmed">Regions</Text><Text fw={700} size="xl">{new Set(agents?.map((a: any) => a.region) ?? []).size}</Text></div></Group>
        </Paper>
      </SimpleGrid>

      <Paper p="md" radius="md" withBorder>
        <Text fw={600} mb="md">Agent Details</Text>
        <Table highlightOnHover withTableBorder>
          <Table.Thead>
            <Table.Tr><Table.Th>Agent</Table.Th><Table.Th>Version</Table.Th><Table.Th>Status</Table.Th><Table.Th>Region</Table.Th><Table.Th>CPU</Table.Th><Table.Th>Memory</Table.Th><Table.Th>Last Heartbeat</Table.Th></Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {agents?.map((a: any) => (
              <Table.Tr key={a.id?.value ?? a.id}>
                <Table.Td><Text size="sm">{a.hostname ?? a.id?.value?.substring(0, 8)}</Text></Table.Td>
                <Table.Td><Code>{a.version}</Code></Table.Td>
                <Table.Td><Badge color={STATUS_COLORS[a.status] || 'gray'}>{a.status}</Badge></Table.Td>
                <Table.Td><Badge variant="light">{a.region}</Badge></Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <RingProgress size={32} thickness={4} sections={[{ value: a.hardware?.cpuPercent ?? 0, color: 'blue' }]} />
                    <Text size="xs">{Math.round(a.hardware?.cpuPercent ?? 0)}%</Text>
                  </Group>
                </Table.Td>
                <Table.Td>{a.hardware ? `${Math.round(a.hardware.memoryUsed / 1024 / 1024)}/${Math.round(a.hardware.memoryTotal / 1024 / 1024)} MB` : '-'}</Table.Td>
                <Table.Td><Text size="xs">{a.lastHeartbeat ? new Date(a.lastHeartbeat).toLocaleString() : '-'}</Text></Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Paper>
    </div>
  );
}
