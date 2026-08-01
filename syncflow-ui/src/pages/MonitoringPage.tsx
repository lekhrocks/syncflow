import { SimpleGrid, Paper, Title, Text, Skeleton } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { dashboardApi, diagnosticsApi } from '../services/api';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts';

export function MonitoringPage() {
  const { data: overview } = useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.overview, refetchInterval: 10_000 });
  const { data: sys } = useQuery({ queryKey: ['diagnostics-system'], queryFn: diagnosticsApi.system });

  const throughput = [
    { name: 'Total Pipelines', value: overview?.pipelines.total ?? 0 },
    { name: 'Running Syncs', value: overview?.syncJobs.running ?? 0 },
    { name: 'Snapshots Running', value: overview?.snapshots.running ?? 0 },
    { name: 'Failed', value: overview?.snapshots.failed ?? 0 },
  ];

  const memData = sys ? [
    { name: 'Heap Used', value: Math.round(sys.jvm.heapMemoryUsage / 1024 / 1024) },
    { name: 'Heap Max', value: Math.round(sys.jvm.maxMemory / 1024 / 1024) },
    { name: 'Free', value: Math.round(sys.jvm.freeMemory / 1024 / 1024) },
  ] : [];

  return (
    <div>
      <Title order={2} mb="lg">Monitoring</Title>

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        <Paper p="md" radius="md" withBorder>
          <Text fw={600} mb="md">Pipeline & Job Throughput</Text>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={throughput}>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" />
              <XAxis dataKey="name" stroke="#888" />
              <YAxis stroke="#888" />
              <Tooltip />
              <Bar dataKey="value" fill="#228be6" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Paper>

        <Paper p="md" radius="md" withBorder>
          <Text fw={600} mb="md">Memory Usage (MB)</Text>
          {memData.length > 0 ? (
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={memData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                <XAxis dataKey="name" stroke="#888" />
                <YAxis stroke="#888" />
                <Tooltip />
                <Bar dataKey="value" fill="#40c057" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          ) : <Skeleton height={250} />}
        </Paper>
      </SimpleGrid>
    </div>
  );
}
