import { useParams, useNavigate } from 'react-router';
import { Tree, Paper, Title, Text, Group, Button, Tabs, Table, Badge, SimpleGrid } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { metadataApi } from '../services/api';
import { IconArrowLeft, IconRefresh } from '@tabler/icons-react';
import { useState } from 'react';
import type { ColumnMetadata, IndexMetadata, ConstraintMetadata } from '../types/api';

export function MetadataPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [selectedSchema, setSelectedSchema] = useState<string | null>(null);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);

  const { data: schemasData } = useQuery({ queryKey: ['metadata-schemas', id], queryFn: () => metadataApi.schemas(id!) });

  const { data: columnsData } = useQuery({
    queryKey: ['metadata-columns', id, selectedSchema, selectedTable],
    queryFn: () => metadataApi.columns(id!, selectedSchema!, selectedTable!),
    enabled: !!selectedSchema && !!selectedTable,
  });

  const { data: indexesData } = useQuery({
    queryKey: ['metadata-indexes', id, selectedSchema, selectedTable],
    queryFn: () => metadataApi.indexes(id!, selectedSchema!, selectedTable!),
    enabled: !!selectedSchema && !!selectedTable,
  });

  const { data: constraintsData } = useQuery({
    queryKey: ['metadata-constraints', id, selectedSchema, selectedTable],
    queryFn: () => metadataApi.constraints(id!, selectedSchema!, selectedTable!),
    enabled: !!selectedSchema && !!selectedTable,
  });

  const handleSelect = (value: string) => {
    const parts = value.split('.');
    if (parts.length === 1) { setSelectedSchema(parts[0]); setSelectedTable(null); }
    else { setSelectedSchema(parts[0]); setSelectedTable(parts[1]); }
  };

  const treeData = schemasData?.data?.map((s) => ({
    label: s.name,
    value: s.name,
    children: s.tables?.map((t) => ({ label: `${t.name} (${t.type})`, value: `${s.name}.${t.name}` })),
  })) ?? [];

  return (
    <div>
      <Group mb="md">
        <Button variant="subtle" leftSection={<IconArrowLeft size={16} />} onClick={() => navigate(`/connections/${id}`)}>Back</Button>
        <Title order={3}>Metadata Explorer</Title>
        <Button variant="light" leftSection={<IconRefresh size={16} />} onClick={() => metadataApi.refresh(id!)}>Refresh</Button>
      </Group>

      <Group align="flex-start" gap="md">
        <Paper p="md" radius="md" withBorder style={{ minWidth: 280 }}>
          <Text fw={600} mb="sm">Schemas</Text>
          {treeData.length > 0 ? (
            <Tree data={treeData as any} />
          ) : <Text c="dimmed" size="sm">No schemas found</Text>}
          {treeData.map((s) => (
            <Button key={s.value} variant="subtle" fullWidth onClick={() => handleSelect(s.value)} size="sm">{s.label}</Button>
          ))}
          {selectedSchema && treeData.flatMap(s => s.children || []).map((t: any) => (
            <Button key={t.value} variant="subtle" fullWidth onClick={() => handleSelect(t.value)} size="sm" ml="md">{t.label}</Button>
          ))}
        </Paper>

        {selectedTable && (
          <div style={{ flex: 1 }}>
            <Paper p="md" radius="md" withBorder mb="md">
              <Text fw={600} mb="sm">{selectedSchema}.{selectedTable} — Columns</Text>
              <Table highlightOnHover withTableBorder>
                <Table.Thead>
                  <Table.Tr><Table.Th>Name</Table.Th><Table.Th>Type</Table.Th><Table.Th>Size</Table.Th><Table.Th>PK</Table.Th><Table.Th>FK</Table.Th><Table.Th>Nullable</Table.Th></Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {columnsData?.data?.map((col: ColumnMetadata) => (
                    <Table.Tr key={col.name}>
                      <Table.Td><Text fw={500}>{col.name}</Text></Table.Td>
                      <Table.Td><Badge variant="light">{col.dataType.jdbcType}</Badge></Table.Td>
                      <Table.Td>{col.dataType.columnSize ?? '-'}</Table.Td>
                      <Table.Td>{col.primaryKey ? '✓' : '-'}</Table.Td>
                      <Table.Td>{col.foreignKey ? '✓' : '-'}</Table.Td>
                      <Table.Td>{col.dataType.nullable ? 'YES' : 'NO'}</Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Paper>

            <SimpleGrid cols={2} spacing="md">
              <Paper p="md" radius="md" withBorder>
                <Text fw={600} mb="sm">Indexes</Text>
                <Table withTableBorder>
                  <Table.Thead><Table.Tr><Table.Th>Name</Table.Th><Table.Th>Columns</Table.Th><Table.Th>Unique</Table.Th></Table.Tr></Table.Thead>
                  <Table.Tbody>
                    {indexesData?.data?.map((idx: IndexMetadata) => (
                      <Table.Tr key={idx.name}>
                        <Table.Td>{idx.name}</Table.Td>
                        <Table.Td>{idx.columnNames.join(', ')}</Table.Td>
                        <Table.Td>{idx.unique ? '✓' : '-'}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Paper>
              <Paper p="md" radius="md" withBorder>
                <Text fw={600} mb="sm">Constraints</Text>
                <Table withTableBorder>
                  <Table.Thead><Table.Tr><Table.Th>Name</Table.Th><Table.Th>Type</Table.Th></Table.Tr></Table.Thead>
                  <Table.Tbody>
                    {constraintsData?.data?.map((c: ConstraintMetadata) => (
                      <Table.Tr key={c.name}>
                        <Table.Td>{c.name}</Table.Td>
                        <Table.Td><Badge>{c.type}</Badge></Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Paper>
            </SimpleGrid>
          </div>
        )}
      </Group>
    </div>
  );
}
