import { Alert, Button, Center, Loader, Stack } from '@mantine/core';

interface QueryStateProps {
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
  retry?: () => void;
  isEmpty?: boolean;
  emptyText?: string;
}

/** Consistent loading / error / empty rendering for react-query data pages. */
export function QueryState({ isLoading, isError, error, retry, isEmpty, emptyText }: QueryStateProps) {
  if (isLoading) {
    return <Center h="30vh"><Loader /></Center>;
  }
  if (isError) {
    return (
      <Stack align="center" gap="sm" py="lg">
        <Alert color="red" title="Failed to load">
          {error instanceof Error ? error.message : String(error)}
        </Alert>
        {retry && <Button variant="light" onClick={retry}>Retry</Button>}
      </Stack>
    );
  }
  if (isEmpty) {
    return <Stack align="center" py="lg"><Alert color="gray" title={emptyText ?? 'Nothing here'}/></Stack>;
  }
  return null;
}