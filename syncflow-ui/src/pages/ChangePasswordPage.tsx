import { Paper, PasswordInput, Button, Stack, Text, Center, ThemeIcon, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconLock } from '@tabler/icons-react';
import { useAuth } from '../auth/AuthContext';
import { authApi } from '../services/api';

/** First-login password change for admin-provisioned accounts. */
export function ChangePasswordPage() {
  const { refreshUser, logout } = useAuth();

  const form = useForm({
    initialValues: { newPassword: '', confirm: '' },
    validate: {
      newPassword: (v) => (v.length >= 8 ? null : 'Password must be at least 8 characters'),
      confirm: (v, values) => (v === values.newPassword ? null : 'Passwords do not match'),
    },
  });

  return (
    <Center h="100vh" bg="dark.9">
      <Paper withBorder p="xl" radius="md" w={360} shadow="md">
        <Stack align="center" mb="md">
          <ThemeIcon size="lg" variant="gradient" gradient={{ from: 'blue', to: 'cyan' }}>
            <IconLock size={20} />
          </ThemeIcon>
          <Title order={3}>Set your password</Title>
          <Text size="sm" c="dimmed" ta="center">Your account was created by an administrator. Set your own password to continue.</Text>
        </Stack>
        <form onSubmit={form.onSubmit(async (v) => {
          try {
            await authApi.changePassword(v.newPassword);
            await refreshUser();
          } catch {
            notifications.show({ color: 'red', message: 'Failed to update password' });
          }
        })}>
          <Stack>
            <PasswordInput label="New password" autoFocus {...form.getInputProps('newPassword')} />
            <PasswordInput label="Confirm password" {...form.getInputProps('confirm')} />
            <Button type="submit" fullWidth mt="sm">Save password</Button>
            <Button variant="subtle" fullWidth onClick={logout}>Sign out</Button>
          </Stack>
        </form>
      </Paper>
    </Center>
  );
}
