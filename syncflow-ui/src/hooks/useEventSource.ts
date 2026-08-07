import { useEffect, useRef, useState } from 'react';

/**
 * Subscribe to a Server-Sent Events stream using fetch (so the bearer token is
 * sent as an Authorization header — native EventSource cannot set headers and
 * would leak the token in the URL). Streams are parsed frame-by-frame from the
 * response body; the payload is the latest event received.
 */
export function useEventSource<T>(path: string | null): { data: T | null; connected: boolean } {
  const [data, setData] = useState<T | null>(null);
  const [connected, setConnected] = useState(false);
  const ctrlRef = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!path) {
      setData(null);
      setConnected(false);
      return;
    }
    const token = localStorage.getItem('syncflow.token');
    const url: string = path;
    const ctrl = new AbortController();
    ctrlRef.current = ctrl;

    async function connect() {
      try {
        const res = await fetch(url, {
          headers: token
            ? { Authorization: `Bearer ${token}`, 'X-Tenant-Id': 'default' }
            : {},
          signal: ctrl.signal,
        });
        if (!res.ok || !res.body) {
          setConnected(false);
          return;
        }
        setConnected(true);
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          // SSE frames are separated by a blank line.
          const frames = buffer.split('\n\n');
          buffer = frames.pop() ?? '';
          for (const frame of frames) {
            const lines = frame.split('\n');
            const dataLine = lines.find((l) => l.startsWith('data:'));
            if (!dataLine) continue;
            const raw = dataLine.slice(5).trim();
            if (!raw) continue;
            try {
              setData(JSON.parse(raw) as T);
            } catch {
              // ignore malformed frames
            }
          }
        }
      } catch {
        // aborted or network error — EventSource-style reconnect handled by the
        // caller or on a new mount; set disconnected so the UI can reflect it.
        setConnected(false);
      }
    }
    void connect();

    return () => {
      ctrl.abort();
      ctrlRef.current = null;
      setConnected(false);
    };
  }, [path]);

  return { data, connected };
}
