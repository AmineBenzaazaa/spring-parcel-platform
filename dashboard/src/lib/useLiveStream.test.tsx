import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useLiveStream } from './live';
import { keys, type OrdersPage } from './queries';

/** Minimal stand-in for the browser EventSource that a test can push named events through. */
class FakeEventSource {
  static last: FakeEventSource | null = null;
  listeners = new Map<string, ((e: MessageEvent) => void)[]>();
  onerror: (() => void) | null = null;
  closed = false;
  constructor(public url: string) {
    FakeEventSource.last = this;
  }
  addEventListener(name: string, fn: (e: MessageEvent) => void) {
    this.listeners.set(name, [...(this.listeners.get(name) ?? []), fn]);
  }
  emit(name: string, data: unknown) {
    this.listeners.get(name)?.forEach((fn) => fn(new MessageEvent(name, { data: JSON.stringify(data) })));
  }
  close() {
    this.closed = true;
  }
}

describe('useLiveStream', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      if (url === '/api/auth/stream-token') {
        return new Response(JSON.stringify({ accessToken: 'stream.jwt', tokenType: 'Bearer', expiresIn: 60, role: 'OPS' }));
      }
      return new Response('{}', { status: 200 });
    }));
  });
  afterEach(() => vi.unstubAllGlobals());

  it('trades the session for a stream token, connects, and applies delivery events to the cache', async () => {
    const qc = new QueryClient();
    qc.setQueryData<OrdersPage>(keys.orders, {
      content: [{ id: 'o1', trackingCode: 'PX-1', merchantId: 'M', recipientName: 'R', recipientPhone: '', addressLine: '',
        city: 'Rabat', codAmount: 10, status: 'ASSIGNED', deliveryAttempts: 0, cancelReason: null,
        createdAt: '2026-11-20T10:00:00Z', updatedAt: '2026-11-20T10:00:00Z' }],
      page: 0, size: 100, totalElements: 1, totalPages: 1,
    });
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
    const factory = (url: string) => new FakeEventSource(url) as unknown as EventSource;

    const { result, unmount } = renderHook(() => useLiveStream(true, factory), { wrapper });

    await waitFor(() => expect(FakeEventSource.last?.url).toBe('/api/activity/stream?access_token=stream.jwt'));
    expect(result.current.status).toBe('connecting');

    act(() => FakeEventSource.last!.emit('hello', { instance: 'n1', serverTime: '', circuit: { name: 'smsGateway', state: 'CLOSED', failureRate: -1, bufferedCalls: 0 } }));
    expect(result.current.status).toBe('connected');
    expect(qc.getQueryData(keys.circuit)).toMatchObject({ state: 'CLOSED' });

    act(() => FakeEventSource.last!.emit('delivery', { eventId: 'e', orderId: 'o1', trackingCode: 'PX-1', courierId: 'c',
      previousStatus: 'ASSIGNED', newStatus: 'OUT_FOR_DELIVERY', attempt: 1, reason: null, codAmount: 10, sequence: 2,
      correlationId: 'x', occurredAt: '2026-11-20T10:01:00Z' }));
    expect(qc.getQueryData<OrdersPage>(keys.orders)!.content[0].status).toBe('OUT_FOR_DELIVERY');
    expect(result.current.lastEventAt).not.toBeNull();

    const source = FakeEventSource.last!;
    unmount();
    expect(source.closed).toBe(true);
  });
});
