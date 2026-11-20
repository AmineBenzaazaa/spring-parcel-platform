import type {
  CircuitInfo, Courier, Delivery, NotificationItem, Order, OrderStats, Page, SystemHealth, TimelineEntry,
  TokenResponse,
} from './types';

/** RFC 9457 problem details from the services, surfaced with the field errors. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
    readonly correlationId?: string,
  ) {
    super(message);
  }
}

type TokenSource = () => string | null;
let tokenSource: TokenSource = () => null;
let onUnauthorized: () => void = () => {};

export function configureApi(source: TokenSource, unauthorized: () => void) {
  tokenSource = source;
  onUnauthorized = unauthorized;
}

export async function request<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = tokenSource();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let body = init.body;
  if (init.json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(init.json);
  }
  const res = await fetch(path, { ...init, headers, body });
  if (res.status === 401 && token) onUnauthorized();
  if (!res.ok) {
    let problem: { detail?: string; title?: string; errors?: Record<string, string>; correlationId?: string } = {};
    try {
      problem = await res.json();
    } catch {
      /* empty or non-JSON body */
    }
    throw new ApiError(res.status, problem.detail ?? problem.title ?? res.statusText, problem.errors ?? {},
      problem.correlationId ?? res.headers.get('X-Correlation-Id') ?? undefined);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export interface CreateOrderInput {
  merchantId: string;
  recipientName: string;
  recipientPhone: string;
  addressLine: string;
  city: string;
  codAmount: number;
}

const shortId = () => Math.random().toString(36).slice(2, 8);

export const api = {
  login: (username: string, password: string) =>
    request<TokenResponse>('/api/auth/login', { method: 'POST', json: { username, password } }),
  streamToken: () => request<TokenResponse>('/api/auth/stream-token', { method: 'POST' }),

  orders: (size = 100, statuses: string[] = []) => {
    const q = new URLSearchParams({ size: String(size) });
    statuses.forEach((s) => q.append('status', s));
    return request<Page<Order>>(`/api/orders?${q}`);
  },
  stats: () => request<OrderStats>('/api/orders/stats'),
  createOrder: (input: CreateOrderInput, correlationId = `ui-${shortId()}`) =>
    request<Order>('/api/orders', {
      method: 'POST',
      json: input,
      // Idempotency-Key: a double-click or a retried request cannot create two orders
      headers: { 'Idempotency-Key': crypto.randomUUID(), 'X-Correlation-Id': correlationId },
    }),

  deliveries: (limit = 200) => request<Delivery[]>(`/api/deliveries?limit=${limit}`),
  delivery: (orderId: string) => request<Delivery>(`/api/deliveries/${orderId}`),
  outForDelivery: (orderId: string) =>
    request<Delivery>(`/api/deliveries/${orderId}/out-for-delivery`, { method: 'POST' }),
  delivered: (orderId: string) => request<Delivery>(`/api/deliveries/${orderId}/delivered`, { method: 'POST' }),
  failed: (orderId: string, reason: string) =>
    request<Delivery>(`/api/deliveries/${orderId}/failed`, { method: 'POST', json: { reason } }),
  couriers: () => request<Courier[]>('/api/couriers'),

  notifications: (orderId?: string) =>
    request<NotificationItem[]>(orderId ? `/api/notifications?orderId=${orderId}` : '/api/notifications'),
  circuit: () => request<CircuitInfo>('/api/notifications/circuit-breaker'),
  smsProviderMode: (mode: 'OK' | 'FAIL') =>
    request<{ mode: string }>(`/api/demo/sms-provider/${mode}`, { method: 'POST' }),
  timeline: (orderId: string) => request<TimelineEntry[]>(`/api/activity/orders/${orderId}`),

  health: () => request<SystemHealth>('/api/system/health'),
};
