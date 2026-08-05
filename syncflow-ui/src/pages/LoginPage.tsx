import { Paper, TextInput, PasswordInput, Button, Stack, Text, Center, ThemeIcon } from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import axios from 'axios';
import { IconPipeline } from '@tabler/icons-react';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { login } = useAuth();

  const form = useForm({
    initialValues: { username: '', password: '' },
    validate: {
      username: (v) => (v.length >= 1 ? null : 'Username is required'),
      password: (v) => (v.length >= 1 ? null : 'Password is required'),
    },
  });

  return (
    <Center h="100vh" bg="dark.9">
      <Paper withBorder p="xl" radius="md" w={360} shadow="md">
        <Stack align="center" mb="md">
          <ThemeIcon size="lg" variant="gradient" gradient={{ from: 'blue', to: 'cyan' }}>
            <IconPipeline size={20} />
          </ThemeIcon>
          <Text fw={700} size="lg">SyncFlow</Text>
          <Text size="sm" c="dimmed">Sign in to continue</Text>
        </Stack>
        <form onSubmit={form.onSubmit(async (v) => {
          try {
            await login(v.username, v.password);
          } catch (e) {
            const msg = axios.isAxiosError(e)
              ? (e.response?.data as { error?: string } | undefined)?.error
              : undefined;
            notifications.show({ color: 'red', message: msg ?? 'Invalid credentials' });
          }
        })}>
          <Stack>
            <TextInput label="Username" autoFocus {...form.getInputProps('username')} />
            <PasswordInput label="Password" {...form.getInputProps('password')} />
            <Button type="submit" fullWidth mt="sm">Sign in</Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}