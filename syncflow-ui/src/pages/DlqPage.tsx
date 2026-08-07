import { Title, Table, Badge, Group, ActionIcon, Text, Paper } from '@mantine/core';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconReload, IconTrash } from '@tabler/icons-react';
import api from '../services/api';
import { QueryState } from '../components/QueryState';

const reasonColor: Record<string, string> = { RETRYABLE: 'yellow', UNRETRYABLE: 'red', SCHEMA: 'orange', DUPLICATE: 'gray' };

export function DlqPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ['dlq'], queryFn: () => api.get('/dlq').then(r => r.data) });

  const replay = useMutation({
    mutationFn: (id: string) => api.post(`/dlq/${id}/replay`),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['dlq'] }); notifications.show({ message: 'Replayed', color: 'green' }); },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/dlq/${id}`),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['dlq'] }); notifications.show({ message: 'Removed', color: 'green' }); },
  });

  return (
    <div>
      <Group justify="space-between" mb="lg">
        <Title order={2}>Dead Letter Queue</Title>
      </Group>
      <QueryState isLoading={isLoading} isError={isError} error={error} retry={refetch} />
      {data?.length ? (
        <Table highlightOnHover withTableBorder>
          <Table.Thead>
            <Table.Tr><Table.Th>Pipeline</Table.Th><Table.Th>Reason</Table.Th><Table.Th>Retries</Table.Th><Table.Th>Replays</Table.Th><Table.Th>Timestamp</Table.Th><Table.Th>Actions</Table.Th></Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data.map((e: any) => (
              <Table.Tr key={e.id}>
                <Table.Td><Text size="sm">{e.pipelineId}</Text></Table.Td>
                <Table.Td><Badge color={reasonColor[e.reason] ?? 'gray'}>{e.reason}</Badge></Table.Td>
                <Table.Td>{e.retryCount}</Table.Td>
                <Table.Td>{e.replayCount}</Table.Td>
                <Table.Td><Text size="sm" c="dimmed">{e.timestamp}</Text></Table.Td>
                <Table.Td>
                  <Group gap="xs">
                    <ActionIcon variant="subtle" color="blue" onClick={() => replay.mutate(e.id)}><IconReload size={16} /></ActionIcon>
                    <ActionIcon variant="subtle" color="red" onClick={() => remove.mutate(e.id)}><IconTrash size={16} /></ActionIcon>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      ) : (
        <Paper p="lg" withBorder><Text c="dimmed">No dead-letter events.</Text></Paper>
      )}
    </div>
  );
}