import { useState, useRef, useEffect } from 'react';
import { Drawer, TextInput, Button, Stack, Text, Paper, Group, ScrollArea, Badge, ActionIcon } from '@mantine/core';
import { IconSend, IconRobot, IconX } from '@tabler/icons-react';
import { motion, AnimatePresence } from 'framer-motion';
import api from '../../services/api';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}

let sessionId = crypto.randomUUID();

export function AiChatDrawer({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const viewport = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (viewport.current) {
      viewport.current.scrollTo({ top: viewport.current.scrollHeight, behavior: 'smooth' });
    }
  }, [messages]);

  const send = async () => {
    if (!input.trim() || loading) return;
    const userMsg: Message = { role: 'user', content: input, timestamp: Date.now() };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const { data } = await api.post('/ai/chat', { sessionId, message: input });
      const aiMsg: Message = { role: 'assistant', content: data.message, timestamp: Date.now() };
      setMessages((prev) => [...prev, aiMsg]);
    } catch (e) {
      setMessages((prev) => [...prev, { role: 'assistant', content: 'AI service unavailable. Check SYNCFLOW_AI_API_KEY is configured.', timestamp: Date.now() }]);
    }
    setLoading(false);
  };

  return (
    <Drawer opened={opened} onClose={onClose} title="AI Copilot" position="right" size="md" padding="md">
      <Stack h="calc(100vh - 100px)">
        <ScrollArea style={{ flex: 1 }} viewportRef={viewport}>
          <AnimatePresence>
            {messages.length === 0 && (
              <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
                <Paper p="md" withBorder mb="sm">
                  <Group mb="xs"><IconRobot size={20} /><Text fw={600}>SyncFlow AI Copilot</Text></Group>
                  <Text size="sm" c="dimmed">
                    Ask me to generate pipelines, suggest mappings, review configurations, or analyze performance.
                    Examples:
                  </Text>
                  <Stack gap={4} mt="sm">
                    {['"Sync users from PostgreSQL to MongoDB"', '"Rename first_name to firstName"', '"Why is my pipeline slow?"', '"Generate mapping for these tables"'].map((ex) => (
                      <Text key={ex} size="xs" c="blue" style={{ cursor: 'pointer' }} onClick={() => setInput(ex)}>{ex}</Text>
                    ))}
                  </Stack>
                </Paper>
              </motion.div>
            )}
            {messages.map((msg, i) => (
              <motion.div key={i} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
                <Paper p="sm" withBorder mb="sm" bg={msg.role === 'user' ? 'blue.9' : 'dark.6'}>
                  <Group mb={4}>
                    <Badge size="sm" variant="light">{msg.role === 'user' ? 'You' : 'AI'}</Badge>
                  </Group>
                  <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</Text>
                </Paper>
              </motion.div>
            ))}
            {loading && (
              <Paper p="sm" withBorder mb="sm" bg="dark.6">
                <Text size="sm" c="dimmed">Thinking...</Text>
              </Paper>
            )}
          </AnimatePresence>
        </ScrollArea>

        <Group>
          <TextInput
            value={input}
            onChange={(e) => setInput(e.currentTarget.value)}
            onKeyDown={(e) => e.key === 'Enter' && send()}
            placeholder="Ask the AI Copilot..."
            style={{ flex: 1 }}
            disabled={loading}
          />
          <ActionIcon onClick={send} loading={loading} color="blue" size="lg"><IconSend size={18} /></ActionIcon>
        </Group>
      </Stack>
    </Drawer>
  );
}
