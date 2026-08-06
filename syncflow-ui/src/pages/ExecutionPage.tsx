import { Tabs, Title, Table, Badge, Group, Text, Progress } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import { useEventSource } from '../hooks/useEventSource';

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

  // Live status: subscribe to the SSE stream of the first running job of each
  // kind, overlaying real-time updates while the index list keeps polling.
  const runningSnapshot = (snapshots as any[])?.find((s) => s.status === 'RUNNING');
  const runningSync = (syncJobs as any[])?.find((j) => j.state === 'RUNNING');
  const liveSnapshot = useEventSource<any>(
    runningSnapshot ? `/api/snapshots/${runningSnapshot.id?.value ?? runningSnapshot.id}/events` : null,
  );
  const liveSync = useEventSource<any>(
    runningSync ? `/api/sync/jobs/${runningSync.id}/events` : null,
  );
  const liveSnapshotData = liveSnapshot.data ?? runningSnapshot;
  const liveSyncData = liveSync.data ?? runningSync;

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
              {snapshots?.map((s: any) => {
                const isRunning = s.status === 'RUNNING';
                const row = isRunning && liveSnapshot.connected && liveSnapshotData ? liveSnapshotData : s;
                return (
                  <Table.Tr key={s.id?.value ?? s.id}>
                    <Table.Td><Text size="sm">{s.id?.value ?? s.id?.substring(0, 8)}</Text></Table.Td>
                    <Table.Td>{row.pipelineId ?? s.pipelineId}</Table.Td>
                    <Table.Td><Badge color={statusColors[row.status ?? s.status]}>{row.status ?? s.status}</Badge></Table.Td>
                    <Table.Td>
                      {(row.progress ?? s.progress) && (
                        <Group><Progress value={(row.progress ?? s.progress).percentComplete} size="sm" style={{ flex: 1 }} />
                        <Text size="xs">{Math.round((row.progress ?? s.progress).percentComplete)}%</Text></Group>
                      )}
                    </Table.Td>
                    <Table.Td>{(row.progress ?? s.progress)?.rowsProcessed ?? 0}</Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </Tabs.Panel>

        <Tabs.Panel value="sync" pt="md">
          <Table highlightOnHover withTableBorder>
            <Table.Thead>
              <Table.Tr><Table.Th>ID</Table.Th><Table.Th>Pipeline</Table.Th><Table.Th>State</Table.Th><Table.Th>Processed</Table.Th><Table.Th>Failed</Table.Th></Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {syncJobs?.map((j: any) => {
                const isRunning = j.state === 'RUNNING';
                const row = isRunning && liveSync.connected && liveSyncData ? liveSyncData : j;
                return (
                  <Table.Tr key={j.id}>
                    <Table.Td><Text size="sm">{j.id?.substring(0, 8)}</Text></Table.Td>
                    <Table.Td>{row.pipelineId ?? j.pipelineId}</Table.Td>
                    <Table.Td><Badge color={statusColors[row.state ?? j.state]}>{row.state ?? j.state}</Badge></Table.Td>
                    <Table.Td>{(row.statistics ?? j.statistics)?.processedEvents ?? 0}</Table.Td>
                    <Table.Td>{(row.statistics ?? j.statistics)?.failedEvents ?? 0}</Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </Tabs.Panel>
      </Tabs>
    </div>
  );
}
