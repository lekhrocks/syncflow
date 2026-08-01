import { useParams, useNavigate } from 'react-router-dom';
import { Paper, Title, Text, Group, Button, Badge, SimpleGrid, Tabs, Stack, Code } from '@mantine/core';
import { useQuery, useMutation } from '@tanstack/react-query';
import axios from 'axios';
import { IconArrowLeft, IconPlayerPlay, IconPlayerStop, IconRefresh } from '@tabler/icons-react';
import { motion } from 'framer-motion';

const api = axios.create({ baseURL: '/api' });

export function PipelineDesignPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { data: pipeline, refetch } = useQuery({
    queryKey: ['pipeline', id],
    queryFn: () => api.get(`/pipelines/${id}`).then(r => r.data),
  });

  const { data: validation } = useQuery({
    queryKey: ['pipeline-validation', id],
    queryFn: () => api.post(`/pipelines/${id}/validate`).then(r => r.data),
  });

  const { data: preview } = useQuery({
    queryKey: ['pipeline-preview', id],
    queryFn: () => api.get(`/pipelines/${id}/preview`).then(r => r.data),
  });

  const snapshotMutation = useMutation({
    mutationFn: () => api.post(`/pipelines/${id}/snapshot`),
  });

  const syncMutation = useMutation({
    mutationFn: () => api.post(`/pipelines/${id}/sync/start`),
  });

  if (!pipeline) return null;

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
      <Button variant="subtle" leftSection={<IconArrowLeft size={16} />} onClick={() => navigate('/pipelines')} mb="md">Back</Button>

      <Paper p="md" radius="md" withBorder mb="lg">
        <Group justify="space-between" mb="sm">
          <div>
            <Title order={3}>{pipeline.name}</Title>
            <Text size="sm" c="dimmed">ID: {pipeline.id} • Version {pipeline.audit?.version}</Text>
          </div>
          <Group>
            <Badge size="lg" color={pipeline.status === 'DRAFT' ? 'gray' : 'green'}>{pipeline.status}</Badge>
            <Button leftSection={<IconPlayerPlay size={16} />} loading={snapshotMutation.isPending} onClick={() => snapshotMutation.mutateAsync()}>Snapshot</Button>
            <Button leftSection={<IconRefresh size={16} />} loading={syncMutation.isPending} onClick={() => syncMutation.mutateAsync()}>Sync</Button>
          </Group>
        </Group>

        <SimpleGrid cols={2} mb="md">
          <Paper p="sm" withBorder><Text size="xs" c="dimmed">Source</Text><Text size="sm">{pipeline.source?.schema}.{pipeline.source?.tableOrCollection}</Text></Paper>
          <Paper p="sm" withBorder><Text size="xs" c="dimmed">Destination</Text><Text size="sm">{pipeline.destination?.schema}.{pipeline.destination?.tableOrCollection}</Text></Paper>
        </SimpleGrid>
      </Paper>

      <Tabs defaultValue="mappings">
        <Tabs.List>
          <Tabs.Tab value="mappings">Mappings</Tabs.Tab>
          <Tabs.Tab value="validation">Validation {validation && !validation.valid ? `(${validation.issues?.length} issues)` : ''}</Tabs.Tab>
          <Tabs.Tab value="preview">Preview</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="mappings" pt="md">
          {pipeline.tableMappings?.map((tm: any, i: number) => (
            <Paper key={i} p="md" withBorder mb="sm">
              <Text fw={600} mb="xs">{tm.sourceTable} → {tm.destinationTable || tm.destinationCollection}</Text>
              {tm.columnMappings?.map((cm: any, j: number) => (
                <Text key={j} size="sm">{cm.sourceColumn} → {cm.destinationColumn}</Text>
              ))}
            </Paper>
          ))}
        </Tabs.Panel>

        <Tabs.Panel value="validation" pt="md">
          {validation?.issues?.map((issue: any, i: number) => (
            <Paper key={i} p="sm" withBorder mb="xs">
              <Group><Badge color={issue.severity === 'ERROR' ? 'red' : issue.severity === 'WARNING' ? 'yellow' : 'blue'}>{issue.severity}</Badge>
              <Text size="sm">{issue.message}</Text></Group>
              <Text size="xs" c="dimmed">{issue.code} — {issue.field}</Text>
            </Paper>
          ))}
          {(!validation || validation.valid) && <Text c="dimmed">No validation issues</Text>}
        </Tabs.Panel>

        <Tabs.Panel value="preview" pt="md">
          {preview && (
            <SimpleGrid cols={2} spacing="md">
              <Paper p="md" withBorder>
                <Text fw={600} mb="sm">Source Columns ({preview.sourceColumns?.length})</Text>
                {preview.sourceColumns?.map((c: any, i: number) => <Text key={i} size="sm">{c.name} ({c.sourceType})</Text>)}
              </Paper>
              <Paper p="md" withBorder>
                <Text fw={600} mb="sm">Destination Columns ({preview.destinationColumns?.length})</Text>
                {preview.destinationColumns?.map((c: any, i: number) => <Text key={i} size="sm">{c.name}</Text>)}
              </Paper>
            </SimpleGrid>
          )}
        </Tabs.Panel>
      </Tabs>
    </motion.div>
  );
}
