import { useParams, useNavigate } from 'react-router-dom';
import { Paper, Title, Text, Group, Badge, Button, SimpleGrid, Stack, Tabs } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { connectionApi, metadataApi } from '../services/api';
import { IconArrowLeft, IconSchema, IconPlugConnected } from '@tabler/icons-react';
import { motion } from 'framer-motion';

export function ConnectionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data: conn } = useQuery({ queryKey: ['connection', id], queryFn: () => connectionApi.get(id!) });
  const { data: health } = useQuery({ queryKey: ['connection-health', id], queryFn: () => connectionApi.health(id!), refetchInterval: 10_000 });

  if (!conn) return null;

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
      <Button variant="subtle" leftSection={<IconArrowLeft size={16} />} onClick={() => navigate('/connections')} mb="md">Back</Button>

      <Paper p="md" radius="md" withBorder mb="lg">
        <Group justify="space-between" mb="sm">
          <div>
            <Title order={3}>{conn.name}</Title>
            <Text size="sm" c="dimmed">{conn.connectionType} • {conn.host}:{conn.port}/{conn.database}</Text>
          </div>
          <Group>
            <Badge size="lg" color={health?.status === 'ONLINE' ? 'green' : 'red'}>{health?.status || 'UNKNOWN'}</Badge>
            <Button leftSection={<IconSchema size={16} />} onClick={() => navigate(`/connections/${id}/metadata`)}>Explore Metadata</Button>
          </Group>
        </Group>
        {health && (
          <SimpleGrid cols={3}>
            <div><Text size="xs" c="dimmed">Latency</Text><Text fw={500}>{health.latencyMs}ms</Text></div>
            <div><Text size="xs" c="dimmed">Version</Text><Text fw={500}>{health.databaseVersion || '-'}</Text></div>
            <div><Text size="xs" c="dimmed">Last Checked</Text><Text fw={500}>{health.lastChecked ? new Date(health.lastChecked).toLocaleString() : '-'}</Text></div>
          </SimpleGrid>
        )}
      </Paper>

      <Tabs defaultValue="overview">
        <Tabs.List>
          <Tabs.Tab value="overview">Overview</Tabs.Tab>
          <Tabs.Tab value="metadata" leftSection={<IconPlugConnected size={14} />} onClick={() => navigate(`/connections/${id}/metadata`)}>Metadata</Tabs.Tab>
        </Tabs.List>
        <Tabs.Panel value="overview" pt="md">
          <SimpleGrid cols={{ base: 1, lg: 2 }}>
            <Paper p="md" radius="md" withBorder><Text fw={600} mb="sm">Connection Details</Text>
              <Stack gap="xs">
                <Text size="sm">Type: <Badge variant="light">{conn.connectionType}</Badge></Text>
                <Text size="sm">Host: {conn.host}:{conn.port}</Text>
                <Text size="sm">Database: {conn.database}</Text>
                <Text size="sm">Driver: {conn.driverName || '-'}</Text>
                <Text size="sm">Created: {new Date(conn.createdAt).toLocaleString()}</Text>
              </Stack>
            </Paper>
          </SimpleGrid>
        </Tabs.Panel>
      </Tabs>
    </motion.div>
  );
}
