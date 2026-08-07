import { Paper, Title, SimpleGrid, Text, Group, Button, Code, Badge, Stack } from '@mantine/core';
import { useQuery, useMutation } from '@tanstack/react-query';
import api from '../../services/api';
import { QueryState } from '../../components/QueryState';

export function AdminPage() {
  const { data: tenant, isLoading: tenantLoading, isError: tenantError, error: tenantErr, refetch: refetchTenant } = useQuery({ queryKey: ['admin-me'], queryFn: () => api.get('/admin/tenants').then(r => r.data) });
  const { data: audit, isLoading: auditLoading, isError: auditError, error: auditErr, refetch: refetchAudit } = useQuery({ queryKey: ['admin-audit'], queryFn: () => api.get('/admin/audit').then(r => r.data) });
  const { data: quota, isLoading: quotaLoading, isError: quotaError, error: quotaErr, refetch: refetchQuota } = useQuery({ queryKey: ['admin-quota'], queryFn: () => api.get('/admin/quotas').then(r => r.data) });

  const issueKey = useMutation({
    mutationFn: () => api.post('/admin/apikeys', { label: 'Dashboard Key', scope: 'READ', ttlSeconds: '2592000' }).then(r => r.data),
  });

  return (
    <div>
      <Title order={2} mb="lg">Administration</Title>

      <QueryState
        isLoading={tenantLoading || auditLoading || quotaLoading}
        isError={tenantError || auditError || quotaError}
        error={tenantErr ?? auditErr ?? quotaErr}
        retry={tenantError ? refetchTenant : auditError ? refetchAudit : refetchQuota}
        isEmpty={!audit?.length}
      />
      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        <Paper p="md" radius="md" withBorder>
          <Title order={4} mb="sm">Tenant Context</Title>
          {tenant && <Stack gap="xs">
            <Text size="sm">Tenant ID: <Code>{tenant.tenantId}</Code></Text>
            <Text size="sm">User: <Code>{tenant.userId}</Code></Text>
            <Group gap="xs">{Object.entries(tenant.roles || {}).map(([k, v]) => v ? <Badge key={k}>{k}</Badge> : null)}</Group>
          </Stack>}
        </Paper>

        <Paper p="md" radius="md" withBorder>
          <Title order={4} mb="sm">Quota Usage</Title>
          {quota && <Stack gap="xs">
            {Object.entries(quota.limits || {}).map(([k, v]) => (
              <Group justify="space-between" key={k}><Text size="sm">{k}</Text><Text fw={500}>{String(v)}</Text></Group>
            ))}
          </Stack>}
        </Paper>

        <Paper p="md" radius="md" withBorder>
          <Title order={4} mb="sm">API Keys</Title>
          <Button onClick={() => issueKey.mutate()} loading={issueKey.isPending}>Issue API Key</Button>
          {issueKey.data && <Stack gap="xs" mt="sm">
            <Text size="sm">ID: <Code>{issueKey.data.id}</Code></Text>
            <Text size="sm">Prefix: <Code>{issueKey.data.prefix}</Code></Text>
            <Text size="sm">Expires: {issueKey.data.expiresAt}</Text>
          </Stack>}
        </Paper>

        <Paper p="md" radius="md" withBorder>
          <Title order={4} mb="sm">Audit Trail</Title>
          <Text size="sm" c="dimmed">Acting on tenant scope only.</Text>
          {audit && <Stack gap="xs" mt="sm" style={{ maxHeight: 200, overflow: 'auto' }}>
            {audit.slice(0, 10).map((a: any, i: number) => (
              <Group gap="xs" key={i}><Badge size="sm" variant="light">{a.action}</Badge><Text size="sm">{a.resourceType}</Text></Group>
            ))}
          </Stack>}
        </Paper>
      </SimpleGrid>
    </div>
  );
}
