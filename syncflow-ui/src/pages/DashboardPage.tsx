import { SimpleGrid, Paper, Text, Group, ThemeIcon, Title } from '@mantine/core';
import { IconPlugConnected, IconPipeline, IconPlayerPlay, IconAlertTriangle } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../services/api';
import { motion } from 'framer-motion';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';
import { QueryState } from '../components/QueryState';

const COLORS = ['#228be6', '#40c057', '#fa5252', '#fab005'];

function MetricCard({ title, value, icon, color }: { title: string; value: number; icon: React.ReactNode; color: string }) {
  return (
    <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }}>
      <Paper p="md" radius="md" withBorder>
        <Group justify="space-between">
        <div>
          <Text size="xs" c="dimmed" tt="uppercase">{title}</Text>
          <Text fw={700} size="xl">{value}</Text>
        </div>
        <ThemeIcon size="lg" radius="md" color={color}>{icon}</ThemeIcon>
      </Group>
    </Paper>
    </motion.div>
  );
}

export function DashboardPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.overview, refetchInterval: 10_000 });

  if (isLoading || isError) return <QueryState isLoading={isLoading} isError={isError} error={error} retry={refetch} />;

  const pipelineData = [
    { name: 'Total', value: data?.pipelines.total ?? 0 },
    { name: 'Draft', value: data?.pipelines.draft ?? 0 },
    { name: 'Validated', value: data?.pipelines.validated ?? 0 },
  ];

  return (
    <div>
      <Title order={2} mb="lg">Dashboard</Title>

      <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }} mb="xl">
        <MetricCard title="Pipelines" value={data?.pipelines.total ?? 0} icon={<IconPipeline size={20} />} color="blue" />
        <MetricCard title="Connections" value={data?.connections.total ?? 0} icon={<IconPlugConnected size={20} />} color="teal" />
        <MetricCard title="Running Syncs" value={data?.syncJobs.running ?? 0} icon={<IconPlayerPlay size={20} />} color="green" />
        <MetricCard title="Active Alerts" value={data?.alerts ?? 0} icon={<IconAlertTriangle size={20} />} color="red" />
      </SimpleGrid>

      <SimpleGrid cols={{ base: 1, lg: 2 }} spacing="lg">
        <Paper p="md" radius="md" withBorder>
          <Text fw={600} mb="md">Pipeline Status Distribution</Text>
          <ResponsiveContainer width="100%" height={250}>
            <BarChart data={pipelineData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" />
              <XAxis dataKey="name" stroke="#888" />
              <YAxis stroke="#888" />
              <Tooltip />
              <Bar dataKey="value" fill="#228be6" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </Paper>

        <Paper p="md" radius="md" withBorder>
          <Text fw={600} mb="md">Snapshot Status</Text>
          <ResponsiveContainer width="100%" height={250}>
            <PieChart>
              <Pie data={[
                { name: 'Running', value: data?.snapshots.running ?? 0 },
                { name: 'Completed', value: data?.snapshots.completed ?? 0 },
                { name: 'Failed', value: data?.snapshots.failed ?? 0 },
              ].filter(d => d.value > 0)} cx="50%" cy="50%" outerRadius={80} dataKey="value" label>
                {pipelineData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </Paper>
      </SimpleGrid>
    </div>
  );
}
