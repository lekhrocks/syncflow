import { Tabs, Paper, Title, Table, Badge, Group, Text, Progress } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

const statusColors: Record<string, string> = {
  PENDING: 'gray', RUNNING: 'blue', COMPLETED: 'green', FAILED: 'red', CANCELLED: 'orange',
};

export function ExecutionPage() {
  const { data: snapshots } = useQuery({
    queryKey: ['snapshots'],
    queryFn: () => api.get('/snapshots').then(r => r.data),
    refetchInterval: 5000,
  });

  const { data: syncJobs } = useQuery({
    queryKey: ['sync-jobs'],
    queryFn: () => api.get('/sync/jobs').then(r => r.data),
    refetchInterval: 5000,
  });

  return (
    <div>
      <Title order={2} mb="lg">Execution Center</Title>
      <Tabs defaultValue="snapshots">
        <Tabs.List>
          <Tabs.Tab value="snapshots">Snapshots ({snapshots?.length ?? 0})</Tabs.Tab>
          <Tabs.Tab value="sync">Synchronization ({syncJobs?.length ?? 0})</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="snapshots" pt="md">
          <Table highlightOnHover withTableBorder>
            <Table.Thead>
              <Table.Tr><Table.Th>ID</Table.Th><Table.Th>Pipeline</Table.Th><Table.Th>Status</Table.Th><Table.Th>Progress</Table.Th><Table.Th>Rows</Table.Th></Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {snapshots?.map((s: any) => (
                <Table.Tr key={s.id?.value ?? s.id}>
                  <Table.Td><Text size="sm">{s.id?.value ?? s.id?.substring(0, 8)}</Text></Table.Td>
                  <Table.Td>{s.pipelineId}</Table.Td>
                  <Table.Td><Badge color={statusColors[s.status]}>{s.status}</Badge></Table.Td>
                  <Table.Td>
                    {s.progress && (
                      <Group><Progress value={s.progress.percentComplete} size="sm" style={{ flex: 1 }} />
                      <Text size="xs">{Math.round(s.progress.percentComplete)}%</Text></Group>
                    )}
                  </Table.Td>
                  <Table.Td>{s.progress?.rowsProcessed ?? 0}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Tabs.Panel>

        <Tabs.Panel value="sync" pt="md">
          <Table highlightOnHover withTableBorder>
            <Table.Thead>
              <Table.Tr><Table.Th>ID</Table.Th><Table.Th>Pipeline</Table.Th><Table.Th>State</Table.Th><Table.Th>Processed</Table.Th><Table.Th>Failed</Table.Th></Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {syncJobs?.map((j: any) => (
                <Table.Tr key={j.id}>
                  <Table.Td><Text size="sm">{j.id?.substring(0, 8)}</Text></Table.Td>
                  <Table.Td>{j.pipelineId}</Table.Td>
                  <Table.Td><Badge color={statusColors[j.state]}>{j.state}</Badge></Table.Td>
                  <Table.Td>{j.statistics?.processedEvents ?? 0}</Table.Td>
                  <Table.Td>{j.statistics?.failedEvents ?? 0}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Tabs.Panel>
      </Tabs>
    </div>
  );
}
