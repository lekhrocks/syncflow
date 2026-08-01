import { Table, Button, Group, Title, Text, Badge, ActionIcon, Modal, TextInput, Select, PasswordInput, NumberInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { connectionApi } from '../services/api';
import { notifications } from '@mantine/notifications';
import { useForm } from '@mantine/form';
import { IconPlus, IconEdit, IconTrash, IconPlugConnected } from '@tabler/icons-react';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import type { CreateConnectionRequest, ConnectionResponse } from '../types/api';

const statusColor: Record<string, string> = { CREATED: 'gray', VALID: 'green', INVALID: 'red', ERROR: 'orange' };

export function ConnectionsPage() {
  const [opened, { open, close }] = useDisclosure(false);
  const [editing, setEditing] = useState<ConnectionResponse | null>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const { data, isLoading } = useQuery({ queryKey: ['connections'], queryFn: connectionApi.list });

  const form = useForm<CreateConnectionRequest>({
    initialValues: { name: '', connectionType: 'POSTGRESQL', host: '', port: 5432, database: '', username: '', password: '' },
    validate: { name: (v) => !v ? 'Required' : null, host: (v) => !v ? 'Required' : null, database: (v) => !v ? 'Required' : null },
  });

  const mutation = useMutation({
    mutationFn: (req: CreateConnectionRequest) => editing
      ? connectionApi.update(editing.id, req)
      : connectionApi.create(req),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['connections'] }); close(); form.reset(); setEditing(null); notifications.show({ title: 'Saved', message: 'Connection saved', color: 'green' }); },
    onError: (e: any) => notifications.show({ title: 'Error', message: e.message, color: 'red' }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => connectionApi.delete(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['connections'] }); notifications.show({ title: 'Deleted', message: 'Connection deleted', color: 'orange' }); },
  });

  const openCreate = () => { setEditing(null); form.reset(); open(); };
  const openEdit = (c: ConnectionResponse) => { setEditing(c); form.setValues({ name: c.name, connectionType: c.connectionType as any, host: c.host, port: c.port, database: c.database, username: '', password: '' }); open(); };

  return (
    <div>
      <Group justify="space-between" mb="lg">
        <Title order={2}>Connections</Title>
        <Button leftSection={<IconPlus size={16} />} onClick={openCreate}>New Connection</Button>
      </Group>

      <Table highlightOnHover withTableBorder>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Name</Table.Th>
            <Table.Th>Type</Table.Th>
            <Table.Th>Host</Table.Th>
            <Table.Th>Database</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {data?.map((c) => (
            <Table.Tr key={c.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/connections/${c.id}`)}>
              <Table.Td><Text fw={500}>{c.name}</Text></Table.Td>
              <Table.Td><Badge variant="light">{c.connectionType}</Badge></Table.Td>
              <Table.Td>{c.host}:{c.port}</Table.Td>
              <Table.Td>{c.database}</Table.Td>
              <Table.Td><Badge color={statusColor[c.status] || 'gray'}>{c.status}</Badge></Table.Td>
              <Table.Td>
                <Group gap="xs">
                  <ActionIcon variant="subtle" onClick={(e) => { e.stopPropagation(); navigate(`/connections/${c.id}/metadata`); }}><IconPlugConnected size={16} /></ActionIcon>
                  <ActionIcon variant="subtle" onClick={(e) => { e.stopPropagation(); openEdit(c); }}><IconEdit size={16} /></ActionIcon>
                  <ActionIcon variant="subtle" color="red" onClick={(e) => { e.stopPropagation(); deleteMutation.mutate(c.id); }}><IconTrash size={16} /></ActionIcon>
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      <Modal opened={opened} onClose={close} title={editing ? 'Edit Connection' : 'New Connection'} size="md">
        <form onSubmit={form.onSubmit((v) => mutation.mutateAsync(v))}>
          <TextInput label="Name" {...form.getInputProps('name')} mb="sm" />
          <Select label="Type" data={['POSTGRESQL', 'MYSQL', 'MONGODB', 'REDIS']} {...form.getInputProps('connectionType')} mb="sm" />
          <TextInput label="Host" {...form.getInputProps('host')} mb="sm" />
          <NumberInput label="Port" {...form.getInputProps('port')} mb="sm" />
          <TextInput label="Database" {...form.getInputProps('database')} mb="sm" />
          <TextInput label="Username" {...form.getInputProps('username')} mb="sm" />
          <PasswordInput label="Password" {...form.getInputProps('password')} mb="md" />
          <Group justify="flex-end">
            <Button variant="light" onClick={close}>Cancel</Button>
            <Button type="submit" loading={mutation.isPending}>{editing ? 'Update' : 'Create'}</Button>
          </Group>
        </form>
      </Modal>
    </div>
  );
}
