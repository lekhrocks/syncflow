import { useState } from 'react';
import { Paper, Title, SimpleGrid, Text, Group, Badge, Button, Stack, Code, Modal, FileInput, Table, ThemeIcon, ActionIcon } from '@mantine/core';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import axios from 'axios';
import { IconPackage, IconUpload, IconPlayerPlay, IconPlayerStop, IconTrash, IconSettings } from '@tabler/icons-react';

const api = axios.create({ baseURL: '/api' });

export function MarketplacePage() {
  const queryClient = useQueryClient();
  const [installOpened, setInstallOpened] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);

  const { data: plugins } = useQuery({
    queryKey: ['plugins'],
    queryFn: () => api.get('/plugins').then(r => r.data),
    refetchInterval: 5000,
  });

  const installMutation = useMutation({
    mutationFn: (file: File) => {
      const fd = new FormData();
      fd.append('file', file);
      return api.post('/plugins/install', fd);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] });
      setInstallOpened(false);
      setSelectedFile(null);
      notifications.show({ title: 'Plugin Installed', message: 'Plugin installed successfully', color: 'green' });
    },
    onError: (e: any) => notifications.show({ title: 'Install Failed', message: e.response?.data?.message || e.message, color: 'red' }),
  });

  const enableMutation = useMutation({
    mutationFn: (id: string) => api.post(`/plugins/${id}/enable`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['plugins'] }); },
  });

  const disableMutation = useMutation({
    mutationFn: (id: string) => api.post(`/plugins/${id}/disable`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['plugins'] }); },
  });

  const uninstallMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/plugins/${id}`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['plugins'] }); },
  });

  return (
    <div>
      <Group justify="space-between" mb="lg">
        <Title order={2}>Plugin Marketplace</Title>
        <Button leftSection={<IconUpload size={16} />} onClick={() => setInstallOpened(true)}>Install Plugin</Button>
      </Group>

      <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="lg">
        {plugins?.map((plugin: any) => {
          const desc = plugin.descriptor || {};
          const life = plugin.lifecycle || 'UNKNOWN';
          const caps = plugin.connector?.capabilities?.() || {};
          return (
            <Paper key={desc.pluginId} p="md" radius="md" withBorder>
              <Group mb="sm">
                <ThemeIcon size="lg" color="violet" variant="light"><IconPackage size={20} /></ThemeIcon>
                <div style={{ flex: 1 }}>
                  <Text fw={600}>{desc.pluginName || desc.pluginId}</Text>
                  <Text size="xs" c="dimmed">{desc.vendor} • v{desc.version}</Text>
                </div>
                <Badge color={life === 'ENABLED' ? 'green' : life === 'DISABLED' ? 'gray' : 'orange'}>{life}</Badge>
              </Group>

              <Text size="sm" c="dimmed" mb="sm">{desc.description || 'No description'}</Text>
              <Text size="xs" mb="md">Type: <Code>{desc.connectorType}</Code> • Platform: {desc.minimumPlatformVersion}–{desc.maximumPlatformVersion || 'latest'}</Text>

              <Group gap={4} mb="md">
                {desc.supportedDatabases?.map((db: string) => <Badge key={db} variant="light" size="sm">{db}</Badge>)}
              </Group>

              <Group gap="xs">
                {life === 'ENABLED' ? (
                  <ActionIcon variant="subtle" color="orange" onClick={() => disableMutation.mutate(desc.pluginId)}><IconPlayerStop size={16} /></ActionIcon>
                ) : (
                  <ActionIcon variant="subtle" color="green" onClick={() => enableMutation.mutate(desc.pluginId)}><IconPlayerPlay size={16} /></ActionIcon>
                )}
                <ActionIcon variant="subtle" color="red" onClick={() => uninstallMutation.mutate(desc.pluginId)}><IconTrash size={16} /></ActionIcon>
              </Group>
            </Paper>
          );
        })}
      </SimpleGrid>

      <Modal opened={installOpened} onClose={() => setInstallOpened(false)} title="Install Plugin" size="md">
        <Stack>
          <Text size="sm">Upload a plugin JAR file. The plugin must contain a valid manifest with Plugin-Id, Plugin-Name, Plugin-Version, and Plugin-Connector-Class entries.</Text>
          <FileInput
            label="Plugin JAR"
            placeholder="Select plugin JAR file"
            accept=".jar"
            value={selectedFile}
            onChange={setSelectedFile}
          />
          <Button onClick={() => selectedFile && installMutation.mutate(selectedFile)} loading={installMutation.isPending} disabled={!selectedFile}>Install</Button>
        </Stack>
      </Modal>
    </div>
  );
}