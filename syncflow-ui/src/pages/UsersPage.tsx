import { Table, Button, Group, Title, Text, Badge, ActionIcon, Modal, TextInput, Select, PasswordInput, Switch, Stack } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { userApi } from '../services/api';
import { notifications } from '@mantine/notifications';
import { useForm } from '@mantine/form';
import { IconPlus, IconTrash } from '@tabler/icons-react';

const ROLE_OPTIONS = [
  { value: 'ADMIN', label: 'ADMIN' },
  { value: 'USER', label: 'USER' },
];

export function UsersPage() {
  const [opened, { open, close }] = useDisclosure(false);
  const queryClient = useQueryClient();

  const { data: users } = useQuery({
    queryKey: ['users'],
    queryFn: userApi.list,
  });

  const form = useForm({
    initialValues: { username: '', password: '', email: '', roles: 'USER' },
    validate: {
      username: (v) => (v.length >= 3 ? null : 'Username must be at least 3 characters'),
      password: (v) => (v.length >= 8 ? null : 'Password must be at least 8 characters'),
    },
  });

  const createMutation = useMutation({
    mutationFn: userApi.create,
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'User created' });
      queryClient.invalidateQueries({ queryKey: ['users'] });
      close();
      form.reset();
    },
    onError: (e) => {
      const message = axios.isAxiosError(e)
        ? (e.response?.data as { error?: string } | undefined)?.error
        : undefined;
      notifications.show({ color: 'red', message: message ?? 'Failed to create user' });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: userApi.delete,
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'User deleted' });
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
    onError: () => notifications.show({ color: 'red', message: 'Failed to delete user' }),
  });

  const toggleEnabledMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      userApi.update(id, { enabled }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  return (
    <div>
      <Group justify="space-between" mb="lg">
        <Title order={2}>Users</Title>
        <Button leftSection={<IconPlus size={16} />} onClick={open}>New User</Button>
      </Group>

      <Table highlightOnHover withTableBorder>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Username</Table.Th>
            <Table.Th>Email</Table.Th>
            <Table.Th>Roles</Table.Th>
            <Table.Th>Enabled</Table.Th>
            <Table.Th></Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {users?.map((u) => (
            <Table.Tr key={u.id}>
              <Table.Td><Text size="sm">{u.username}</Text></Table.Td>
              <Table.Td><Text size="sm">{u.email}</Text></Table.Td>
              <Table.Td>
                <Group gap={4}>
                  {u.roles.split(',').map((r) => (
                    <Badge key={r} variant="light" color={r === 'ADMIN' ? 'grape' : 'blue'}>{r.trim()}</Badge>
                  ))}
                </Group>
              </Table.Td>
              <Table.Td>
                <Switch
                  checked={u.enabled}
                  onChange={(ev) => toggleEnabledMutation.mutate({ id: u.id, enabled: ev.currentTarget.checked })}
                  aria-label={`toggle ${u.username}`}
                />
              </Table.Td>
              <Table.Td>
                <ActionIcon color="red" onClick={() => deleteMutation.mutate(u.id)} aria-label={`delete ${u.username}`}>
                  <IconTrash size={16} />
                </ActionIcon>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      <Modal opened={opened} onClose={close} title="Create user" centered>
        <form onSubmit={form.onSubmit((v) => createMutation.mutate(v))}>
          <Stack>
            <TextInput label="Username" required {...form.getInputProps('username')} />
            <PasswordInput label="Password" required {...form.getInputProps('password')} />
            <TextInput label="Email" {...form.getInputProps('email')} />
            <Select label="Roles" data={ROLE_OPTIONS} {...form.getInputProps('roles')} />
            <Group justify="flex-end" mt="md">
              <Button variant="default" onClick={close}>Cancel</Button>
              <Button type="submit" loading={createMutation.isPending}>Create</Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </div>
  );
}
