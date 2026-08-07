import { Table, Button, Group, Title, Text, Badge, ActionIcon, Modal, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { useForm } from '@mantine/form';
import { IconPlus, IconEdit, IconTrash, IconPlayerPlay } from '@tabler/icons-react';
import { useNavigate } from 'react-router';
import { QueryState } from '../components/QueryState';
import api from '../services/api';

const statusColor: Record<string, string> = { DRAFT: 'gray', VALIDATED: 'green', ACTIVATED: 'blue', ARCHIVED: 'orange' };

export function PipelinesPage() {
  const [opened, { open, close }] = useDisclosure(false);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ['pipelines'], queryFn: () => api.get('/pipelines').then(r => r.data) });

  const form = useForm({
    initialValues: { name: '', sourceConnectionId: '', sourceSchema: '', sourceTable: '', destConnectionId: '', destSchema: '', destTable: '' },
    validate: { name: (v: string) => !v ? 'Required' : null },
  });

  const mutation = useMutation({
    mutationFn: (req: any) => api.post('/pipelines', req),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['pipelines'] }); close(); form.reset(); notifications.show({ title: 'Created', message: 'Pipeline created', color: 'green' }); },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/pipelines/${id}`),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['pipelines'] }); notifications.show({ title: 'Deleted', message: 'Pipeline deleted', color: 'green' }); },
  });

  return (
    <div>
      <Group justify="space-between" mb="lg">
        <Title order={2}>Pipelines</Title>
        <Button leftSection={<IconPlus size={16} />} onClick={open}>New Pipeline</Button>
      </Group>

      <QueryState isLoading={isLoading} isError={isError} error={error} retry={refetch} isEmpty={!data?.length} />

      <Table highlightOnHover withTableBorder>
        <Table.Thead>
          <Table.Tr><Table.Th>Name</Table.Th><Table.Th>Source</Table.Th><Table.Th>Destination</Table.Th><Table.Th>Status</Table.Th><Table.Th>Version</Table.Th><Table.Th>Actions</Table.Th></Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {data?.map((p: any) => (
            <Table.Tr key={p.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/pipelines/${p.id}`)}>
              <Table.Td><Text fw={500}>{p.name}</Text></Table.Td>
              <Table.Td><Text size="sm">{p.source.tableOrCollection}</Text></Table.Td>
              <Table.Td><Text size="sm">{p.destination.tableOrCollection}</Text></Table.Td>
              <Table.Td><Badge color={statusColor[p.status]}>{p.status}</Badge></Table.Td>
              <Table.Td>{p.audit?.version ?? 1}</Table.Td>
              <Table.Td>
                <Group gap="xs">
                  <ActionIcon variant="subtle" color="green" onClick={(e) => { e.stopPropagation(); api.post(`/pipelines/${p.id}/snapshot`).then(() => notifications.show({ title: 'Started', message: 'Snapshot started', color: 'green' })); }}><IconPlayerPlay size={16} /></ActionIcon>
                  <ActionIcon variant="subtle" onClick={(e) => { e.stopPropagation(); navigate(`/pipelines/${p.id}/edit`); }}><IconEdit size={16} /></ActionIcon>
                  <ActionIcon variant="subtle" color="red" onClick={(e) => { e.stopPropagation(); if (window.confirm(`Delete pipeline "${p.name}"?`)) deleteMutation.mutate(p.id); }}><IconTrash size={16} /></ActionIcon>
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      <Modal opened={opened} onClose={close} title="New Pipeline" size="lg">
        <form onSubmit={form.onSubmit((v) => mutation.mutateAsync(v))}>
          <TextInput label="Name" {...form.getInputProps('name')} mb="sm" />
          <TextInput label="Source Connection ID" {...form.getInputProps('sourceConnectionId')} mb="sm" />
          <TextInput label="Source Schema" {...form.getInputProps('sourceSchema')} mb="sm" />
          <TextInput label="Source Table" {...form.getInputProps('sourceTable')} mb="sm" />
          <TextInput label="Destination Connection ID" {...form.getInputProps('destConnectionId')} mb="sm" />
          <TextInput label="Destination Schema" {...form.getInputProps('destSchema')} mb="sm" />
          <TextInput label="Destination Table" {...form.getInputProps('destTable')} mb="md" />
          <Group justify="flex-end">
            <Button variant="light" onClick={close}>Cancel</Button>
            <Button type="submit" loading={mutation.isPending}>Create</Button>
          </Group>
        </form>
      </Modal>
    </div>
  );
}
