import { Paper, Title, Table, Text, Badge, Group } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import api from '../services/api';
import { QueryState } from '../components/QueryState';

export function AuditPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['audit'],
    queryFn: () => api.get<{ id: string; action: string; entityType: string; entityId: string; success: boolean; timestamp: string; details: string }[]>('/audit').then(r => r.data),
  });

  return (
    <div>
      <Title order={2} mb="lg">Audit Trail</Title>
      <QueryState isLoading={isLoading} isError={isError} error={error} retry={refetch} isEmpty={!data?.length} />
      <Paper p="md" radius="md" withBorder>
        <Table highlightOnHover withTableBorder>
          <Table.Thead>
            <Table.Tr><Table.Th>Timestamp</Table.Th><Table.Th>Action</Table.Th><Table.Th>Entity</Table.Th><Table.Th>Details</Table.Th><Table.Th>Status</Table.Th></Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data?.map((a) => (
              <Table.Tr key={a.id}>
                <Table.Td><Text size="sm">{new Date(a.timestamp).toLocaleString()}</Text></Table.Td>
                <Table.Td><Badge variant="light">{a.action}</Badge></Table.Td>
                <Table.Td><Text size="sm">{a.entityType}/{a.entityId?.substring(0, 8)}</Text></Table.Td>
                <Table.Td><Text size="sm">{a.details}</Text></Table.Td>
                <Table.Td><Badge color={a.success ? 'green' : 'red'}>{a.success ? 'OK' : 'FAIL'}</Badge></Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Paper>
    </div>
  );
}
