import { Component, type ReactNode } from 'react';
import { Button, Center, Paper, Stack, Text } from '@mantine/core';

interface Props {
  children: ReactNode;
}
interface State {
  hasError: boolean;
  message?: string;
}

/** Catches render/throw errors in children so the app doesn't blank out. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(err: unknown): State {
    return { hasError: true, message: err instanceof Error ? err.message : String(err) };
  }

  render() {
    if (this.state.hasError) {
      return (
        <Center h="100vh">
          <Paper p="lg" withBorder>
            <Stack align="center" gap="xs">
              <Text fw={600}>Something went wrong</Text>
              <Text size="sm" c="dimmed">{this.state.message}</Text>
              <Button variant="light" onClick={() => { this.setState({ hasError: false }); window.location.href = '/'; }}>
                Reload
              </Button>
            </Stack>
          </Paper>
        </Center>
      );
    }
    return this.props.children;
  }
}