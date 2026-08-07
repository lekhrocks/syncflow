import { SimpleGrid, Paper, Title, Text, Group, ThemeIcon } from '@mantine/core';
import { IconCpu, IconDatabase, IconServer } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { diagnosticsApi } from '../services/api';
import { QueryState } from '../components/QueryState';

export function DiagnosticsPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ['diagnostics-system'], queryFn: diagnosticsApi.system });

  if (isLoading || isError) return <QueryState isLoading={isLoading} isError={isError} error={error} retry={refetch} />;

  return (
    <div>
      <Title order={2} mb="lg">System Diagnostics</Title>

      <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="lg">
        <Paper p="md" radius="md" withBorder>
          <Group mb="sm"><ThemeIcon color="blue" size="md"><IconCpu size={16} /></ThemeIcon><Text fw={600}>JVM</Text></Group>
          <Text size="sm">Processors: {data?.jvm.availableProcessors}</Text>
          <Text size="sm">Threads: {data?.jvm.threadCount}</Text>
          <Text size="sm">Virtual Threads: {data?.jvm.virtualThreadCount}</Text>
          <Text size="sm">Free Memory: {data ? Math.round(data.jvm.freeMemory / 1024 / 1024) : '?'} MB</Text>
          <Text size="sm">Max Memory: {data ? Math.round(data.jvm.maxMemory / 1024 / 1024) : '?'} MB</Text>
        </Paper>

        <Paper p="md" radius="md" withBorder>
          <Group mb="sm"><ThemeIcon color="teal" size="md"><IconServer size={16} /></ThemeIcon><Text fw={600}>System</Text></Group>
          <Text size="sm">OS: {data?.os.name} {data?.os.version}</Text>
          <Text size="sm">Java: {data?.java.version}</Text>
          <Text size="sm">Vendor: {data?.java.vm}</Text>
        </Paper>

        <Paper p="md" radius="md" withBorder>
          <Group mb="sm"><ThemeIcon color="orange" size="md"><IconDatabase size={16} /></ThemeIcon><Text fw={600}>Runtime</Text></Group>
          <Text size="sm">Total Memory: {data ? Math.round(data.jvm.totalMemory / 1024 / 1024) : '?'} MB</Text>
          <Text size="sm">Heap Used: {data ? Math.round(data.jvm.heapMemoryUsage / 1024 / 1024) : '?'} MB</Text>
          <Text size="sm">Peak Threads: {data?.jvm.peakThreadCount}</Text>
        </Paper>
      </SimpleGrid>
    </div>
  );
}
