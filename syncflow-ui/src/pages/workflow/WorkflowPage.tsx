import { useState, useCallback } from 'react';
import { Paper, Title, Group, Button, Text, Badge, Table, SimpleGrid, Skeleton } from '@mantine/core';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notifications } from '@mantine/notifications';
import { IconPlayerPlay, IconPlayerStop, IconPlus, IconGitBranch } from '@tabler/icons-react';
import api from '../../services/api';
import { QueryState } from '../../components/QueryState';
import {
  ReactFlow,
  MiniMap,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
  Node,
  Edge,
  MarkerType,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

const TASK_COLORS: Record<string, string> = {
  VALIDATION: '#228be6',
  METADATA_DISCOVERY: '#40c057',
  SNAPSHOT: '#fab005',
  CDC_CAPTURE: '#fd7e14',
  SYNCHRONIZATION: '#7950f2',
  MONITORING: '#15aabf',
};

function buildGraph(tasks: any[]): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = tasks.map((t, i) => ({
    id: t.taskId,
    type: 'default',
    position: { x: 250, y: i * 120 },
    data: {
      label: (
        <div style={{ padding: 8, textAlign: 'center' }}>
          <div style={{ fontSize: 14, fontWeight: 600 }}>{t.name}</div>
          <Badge size="sm" variant="light" color={TASK_COLORS[t.type] ? 'blue' : 'gray'}>{t.type}</Badge>
          {t.maxRetries > 0 && <div style={{ fontSize: 10, marginTop: 2 }}>retries: {t.maxRetries}</div>}
        </div>
      ),
    },
    style: {
      background: '#1a1b1e',
      border: `2px solid ${TASK_COLORS[t.type] || '#373a40'}`,
      borderRadius: 8,
      padding: 4,
      width: 180,
    },
  }));

  const edges: Edge[] = tasks.flatMap((t) =>
    (t.dependsOn || []).map((dep: string) => ({
      id: `${dep}->${t.taskId}`,
      source: dep,
      target: t.taskId,
      animated: true,
      style: { stroke: '#555' },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#555' },
    }))
  );
  return { nodes, edges };
}

export function WorkflowPage() {
  const queryClient = useQueryClient();
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  const { data: workflows, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['workflows'],
    queryFn: () => api.get('/workflows').then(r => r.data),
    refetchInterval: 5000,
  });

  const createMutation = useMutation({
    mutationFn: (pipelineId: string) => api.post('/workflows', { pipelineId }).then(r => r.data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['workflows'] }); },
  });

  const startMutation = useMutation({
    mutationFn: (id: string) => api.post(`/workflows/${id}/start`).then(r => r.data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['workflows'] }); },
  });

  const cancelMutation = useMutation({
    mutationFn: (id: string) => api.post(`/workflows/${id}/cancel`).then(r => r.data),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['workflows'] }); },
  });

  const [selectedWf, setSelectedWf] = useState<any>(null);
  const { data: graphData } = useQuery({
    queryKey: ['workflow-graph', selectedWf?.id?.value],
    queryFn: () => api.get(`/workflows/${selectedWf.id.value}/graph`).then(r => r.data),
    enabled: !!selectedWf,
  });

  const showGraph = useCallback((wf: any) => {
    setSelectedWf(wf);
    api.get(`/workflows/${wf.id.value}/graph`).then(r => {
      const { nodes: n, edges: e } = buildGraph(r.data);
      setNodes(n as any);
      setEdges(e as any);
    });
  }, [setNodes, setEdges]);

  return (
    <div>
      <Group justify="space-between" mb="lg">
        <Title order={2}>Workflow Orchestrator</Title>
        <Button leftSection={<IconPlus size={16} />} onClick={() => {
          const pid = prompt('Enter Pipeline ID:');
          if (pid) createMutation.mutate(pid);
        }}>New Workflow</Button>
      </Group>

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        <Paper p="md" radius="md" withBorder>
          <Text fw={600} mb="md">Workflows</Text>
          <QueryState isLoading={isLoading} isError={isError} error={error} retry={refetch} isEmpty={!workflows?.length} />
          <Table highlightOnHover withTableBorder>
            <Table.Thead>
              <Table.Tr><Table.Th>ID</Table.Th><Table.Th>Pipeline</Table.Th><Table.Th>Status</Table.Th><Table.Th>Actions</Table.Th></Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {workflows?.map((wf: any) => {
                const wfId = wf.id?.value || wf.id;
                return (
                  <Table.Tr key={wfId} style={{ cursor: 'pointer' }} onClick={() => showGraph(wf)}>
                    <Table.Td><Text size="sm">{wfId?.substring(0, 8)}</Text></Table.Td>
                    <Table.Td>{wf.pipelineId}</Table.Td>
                    <Table.Td><Badge color={wf.status === 'RUNNING' ? 'blue' : wf.status === 'COMPLETED' ? 'green' : 'gray'}>{wf.status}</Badge></Table.Td>
                    <Table.Td>
                      <Group gap="xs">
                        {wf.status === 'PENDING' && <Button size="xs" leftSection={<IconPlayerPlay size={12} />} onClick={(e) => { e.stopPropagation(); startMutation.mutate(wfId); }}>Start</Button>}
                        {wf.status === 'RUNNING' && <Button size="xs" color="red" leftSection={<IconPlayerStop size={12} />} onClick={(e) => { e.stopPropagation(); cancelMutation.mutate(wfId); }}>Cancel</Button>}
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </Paper>

        <Paper p="md" radius="md" withBorder style={{ height: 500 }}>
          {selectedWf ? (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              fitView
            >
              <MiniMap />
              <Controls />
              <Background />
            </ReactFlow>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
              <Text c="dimmed">Select a workflow to view its DAG</Text>
            </div>
          )}
        </Paper>
      </SimpleGrid>
    </div>
  );
}