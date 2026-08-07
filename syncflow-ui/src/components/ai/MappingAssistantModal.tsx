import { useState } from 'react';
import { Modal, TextInput, Button, Group, Stack, Text, Textarea, Select, Loader } from '@mantine/core';
import api from '../../services/api';

export function MappingAssistantModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const [sourceConnId, setSourceConnId] = useState('');
  const [sourceSchema, setSourceSchema] = useState('');
  const [sourceTable, setSourceTable] = useState('');
  const [destConnId, setDestConnId] = useState('');
  const [destSchema, setDestSchema] = useState('');
  const [destTable, setDestTable] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState('');

  const generate = async () => {
    setLoading(true);
    try {
      const { data } = await api.post('/ai/mapping', {
        sourceConnectionId: sourceConnId,
        sourceSchema, sourceTable,
        destConnectionId: destConnId,
        destSchema, destTable,
        sessionId: crypto.randomUUID(),
      });
      setResult(data.message);
    } catch (e) {
      setResult('AI service unavailable.');
    }
    setLoading(false);
  };

  return (
    <Modal opened={opened} onClose={onClose} title="Mapping Assistant" size="lg">
      <Stack>
        <Text size="sm">Configure source and destination to generate column mappings with transformations.</Text>
        <TextInput label="Source Connection ID" value={sourceConnId} onChange={(e) => setSourceConnId(e.currentTarget.value)} />
        <TextInput label="Source Schema" value={sourceSchema} onChange={(e) => setSourceSchema(e.currentTarget.value)} />
        <TextInput label="Source Table" value={sourceTable} onChange={(e) => setSourceTable(e.currentTarget.value)} />
        <TextInput label="Destination Connection ID" value={destConnId} onChange={(e) => setDestConnId(e.currentTarget.value)} />
        <TextInput label="Destination Schema" value={destSchema} onChange={(e) => setDestSchema(e.currentTarget.value)} />
        <TextInput label="Destination Table" value={destTable} onChange={(e) => setDestTable(e.currentTarget.value)} />
        <Group justify="flex-end">
          <Button onClick={generate} loading={loading}>Generate Mappings</Button>
        </Group>
        {result && <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{result}</Text>}
      </Stack>
    </Modal>
  );
}
