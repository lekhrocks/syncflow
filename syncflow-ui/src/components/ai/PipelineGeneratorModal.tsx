import { useState } from 'react';
import { Modal, Stepper, TextInput, Select, Button, Group, Stack, Text, Textarea, Loader } from '@mantine/core';
import api from '../../services/api';

export function PipelineGeneratorModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const [active, setActive] = useState(0);
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState('');

  const generate = async () => {
    setLoading(true);
    try {
      const { data } = await api.post('/ai/pipeline', { description, sessionId: crypto.randomUUID() });
      setResult(data.message);
      setActive(2);
    } catch (e) {
      setResult('AI service unavailable.');
    }
    setLoading(false);
  };

  return (
    <Modal opened={opened} onClose={onClose} title="Pipeline Generator" size="lg">
      <Stepper active={active} onStepClick={setActive}>
        <Stepper.Step label="Describe" description="What do you want to sync?">
          <Stack>
            <Text size="sm">Describe the synchronization you need. For example: "Sync users table from PostgreSQL to MongoDB, map id to _id, ignore deleted_at records"</Text>
            <Textarea
              placeholder="Describe your pipeline..."
              minRows={4}
              value={description}
              onChange={(e) => setDescription(e.currentTarget.value)}
            />
            <Group justify="flex-end"><Button onClick={() => { setActive(1); generate(); }} loading={loading}>Generate</Button></Group>
          </Stack>
        </Stepper.Step>
        <Stepper.Step label="Generating" description="AI is creating the pipeline">
          <Stack align="center" py="xl">
            <Loader size="lg" />
            <Text c="dimmed">Analyzing connections, schemas, and generating pipeline definition...</Text>
          </Stack>
        </Stepper.Step>
        <Stepper.Step label="Result" description="Generated pipeline">
          <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{result}</Text>
          <Group justify="flex-end" mt="md">
            <Button variant="light" onClick={() => { setActive(0); setDescription(''); setResult(''); }}>Start Over</Button>
            <Button onClick={onClose}>Close</Button>
          </Group>
        </Stepper.Step>
      </Stepper>
    </Modal>
  );
}
