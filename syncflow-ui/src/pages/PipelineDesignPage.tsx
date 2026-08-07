import { useState } from 'react';
import { useParams, useNavigate } from 'react-router';
import { Paper, Title, Text, Group, Button, Badge, SimpleGrid, Stack, TextInput, Modal } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconArrowLeft, IconPlayerPlay, IconPlayerStop, IconRefresh, IconPlayerPause } from '@tabler/icons-react';
import api from '../services/api';

export function PipelineDesignPage({ edit }: { edit?: boolean }) {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [editing, { open: openEdit, close }] = useDisclosure(Boolean(edit));

  const { data: pipeline } = useQuery({
    queryKey: ['pipeline', id],
    queryFn: () => api.get(`/pipelines/${id}`).then(r => r.data),
  });

  // Live runtime state for this pipeline.
  const { data: syncState, refetch: refetchSync } = useQuery({
    queryKey: ['pipeline-sync', id],
    queryFn: () => api.get(`/pipelines/${id}/sync/status`).then(r => r.data).catch(() => null),
    refetchInterval: 5000,
  });
  const { data: captureState, refetch: refetchCapture } = useQuery({
    queryKey: ['pipeline-capture', id],
    queryFn: () => api.get(`/pipelines/${id}/capture/status`).then(r => r.data).catch(() => null),
    refetchInterval: 5000,
  });

  const run = (url: string) => api.post(url).then(() => { refetchSync(); refetchCapture(); });
  const syncStart = useMutation({ mutationFn: () => run(`/pipelines/${id}/sync/start`) });
  const syncStop = useMutation({ mutationFn: () => run(`/pipelines/${id}/sync/stop`) });
  const capturePause = useMutation({ mutationFn: () => run(`/pipelines/${id}/capture/pause`) });
  const captureResume = useMutation({ mutationFn: () => run(`/pipelines/${id}/capture/resume`) });
  const snapshotMutation = useMutation({ mutationFn: () => api.post(`/pipelines/${id}/snapshot`) });

  if (!pipeline) return null;

  const syncRunning = syncState?.state === 'RUNNING';
  const captureStatus = captureState?.captureStatus;

  return (
    <div>
      <Group justify="space-between" mb="md">
        <Button variant="subtle" leftSection={<IconArrowLeft size={16} />} onClick={() => navigate('/pipelines')}>Back</Button>
        <Button variant="light" onClick={openEdit}>Edit Pipeline</Button>
      </Group>

      <Paper p="md" radius="md" withBorder mb="lg">
        <Group justify="space-between" mb="sm">
          <Stack gap={2}>
            <Title order={3}>{pipeline.name}</Title>
            <Text size="sm" c="dimmed">ID: {pipeline.id} • Version {pipeline.audit?.version}</Text>
          </Stack>
          <Group>
            <Badge size="lg" color={pipeline.status === 'DRAFT' ? 'gray' : 'green'}>{pipeline.status}</Badge>
            {syncRunning && <Badge color="blue">SYNC RUNNING</Badge>}
            {captureStatus && <Badge color="violet">CAPTURE {captureStatus}</Badge>}
          </Group>
        </Group>

        <SimpleGrid cols={2} mb="md">
          <Paper p="sm" withBorder><Text size="xs" c="dimmed">Source</Text><Text size="sm">{pipeline.source?.schema}.{pipeline.source?.tableOrCollection}</Text></Paper>
          <Paper p="sm" withBorder><Text size="xs" c="dimmed">Destination</Text><Text size="sm">{pipeline.destination?.schema}.{pipeline.destination?.tableOrCollection}</Text></Paper>
        </SimpleGrid>

        <Group gap="xs">
          <Button leftSection={<IconPlayerPlay size={16} />} loading={snapshotMutation.isPending}
            onClick={() => snapshotMutation.mutateAsync().then(() => notifications.show({ message: 'Snapshot started', color: 'green' }))}>Snapshot</Button>
          <Button color="blue" variant="light" leftSection={<IconRefresh size={16} />} loading={syncStart.isPending} onClick={() => syncStart.mutate()}>Start Sync</Button>
          {syncRunning && <Button color="red" variant="light" leftSection={<IconPlayerStop size={16} />} loading={syncStop.isPending} onClick={() => syncStop.mutate()}>Stop Sync</Button>}
          <Button color="yellow" variant="light" leftSection={<IconPlayerPause size={16} />} loading={capturePause.isPending} onClick={() => capturePause.mutate()}>Pause</Button>
          <Button color="teal" variant="light" leftSection={<IconPlayerPlay size={16} />} loading={captureResume.isPending} onClick={() => captureResume.mutate()}>Resume</Button>
        </Group>
      </Paper>

      <EditModal opened={editing} onClose={close} id={id!} />
    </div>
  );
}

/** CRUD edit form backed by PUT /pipelines/{id}. */
function EditModal({ opened, onClose, id }: { opened: boolean; onClose: () => void; id: string }) {
  const { data: pipeline } = useQuery({ queryKey: ['pipeline', id], queryFn: () => api.get(`/pipelines/${id}`).then(r => r.data) });
  const queryClient = useQueryClient();

  const updateMutation = useMutation({
    mutationFn: (req: any) => api.put(`/pipelines/${id}`, req),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['pipeline', id] });
      notifications.show({ title: 'Saved', message: 'Pipeline updated', color: 'green' });
      onClose();
    },
  });

  const [name, setName] = useState('');
  const [srcSchema, setSrcSchema] = useState('');
  const [srcTable, setSrcTable] = useState('');
  const [dstSchema, setDstSchema] = useState('');
  const [dstTable, setDstTable] = useState('');

  // Seed the form once the pipeline loads (or the modal opens).
  const [seeded, setSeeded] = useState(false);
  if (!seeded && pipeline) {
    setName(pipeline.name);
    setSrcSchema(pipeline.source?.schema || '');
    setSrcTable(pipeline.source?.tableOrCollection || '');
    setDstSchema(pipeline.destination?.schema || '');
    setDstTable(pipeline.destination?.tableOrCollection || '');
    setSeeded(true);
  }

  return (
    <Modal opened={opened} onClose={onClose} title="Edit Pipeline" size="lg">
      <TextInput label="Name" value={name} onChange={(e) => setName(e.currentTarget.value)} mb="sm" />
      <Group grow mb="sm">
        <TextInput label="Source Schema" value={srcSchema} onChange={(e) => setSrcSchema(e.currentTarget.value)} />
        <TextInput label="Source Table" value={srcTable} onChange={(e) => setSrcTable(e.currentTarget.value)} />
      </Group>
      <Group grow mb="md">
        <TextInput label="Destination Schema" value={dstSchema} onChange={(e) => setDstSchema(e.currentTarget.value)} />
        <TextInput label="Destination Table" value={dstTable} onChange={(e) => setDstTable(e.currentTarget.value)} />
      </Group>
      <Group justify="flex-end">
        <Button variant="light" onClick={onClose}>Cancel</Button>
        <Button loading={updateMutation.isPending}
          onClick={() => updateMutation.mutate({ name, sourceSchema: srcSchema, sourceTable: srcTable, destSchema: dstSchema, destTable: dstTable })}>Save</Button>
      </Group>
    </Modal>
  );
}