import { useEffect, useRef, useState } from 'react';
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { api } from './api';
import { keys, type OrdersPage } from './queries';
import { orderStatusFor } from './status';
import type { CircuitInfo, Delivery, DeliveryLive, Hello, NotificationItem, Order, OrderLive } from './types';

// ---- pure cache updates (unit-tested) --------------------------------------------------

/**
 * Applies a DeliveryStatusChanged to the cached board. Mirrors order-service's projection: an event
 * whose per-delivery sequence is not newer than the last one applied is ignored (Kafka retries and
 * redeliveries can reorder events).
 * @returns true if the board changed
 */
export function applyDeliveryEvent(qc: QueryClient, e: DeliveryLive, lastSeq: Map<string, number>): boolean {
  if ((lastSeq.get(e.orderId) ?? 0) >= e.sequence) return false;
  lastSeq.set(e.orderId, e.sequence);

  qc.setQueryData<Delivery[]>(keys.deliveries, (list) => {
    if (!list) return list;
    const i = list.findIndex((d) => d.orderId === e.orderId);
    if (i < 0) {
      return [
        {
          id: e.orderId, orderId: e.orderId, trackingCode: e.trackingCode, city: '', zone: null,
          codAmount: e.codAmount ?? 0, courierId: e.courierId, status: e.newStatus, attempts: e.attempt,
          maxAttempts: 3, lastFailureReason: e.reason, updatedAt: e.occurredAt,
        },
        ...list,
      ];
    }
    const next = [...list];
    next[i] = {
      ...next[i], courierId: e.courierId ?? next[i].courierId, status: e.newStatus, attempts: e.attempt,
      lastFailureReason: e.newStatus === 'FAILED' ? e.reason : next[i].lastFailureReason, updatedAt: e.occurredAt,
    };
    return next;
  });

  const status = orderStatusFor(e.newStatus);
  if (!status) return false;
  let changed = false;
  qc.setQueryData<OrdersPage>(keys.orders, (page) => {
    if (!page) return page;
    const content = page.content.map((o) => {
      if (o.id !== e.orderId) return o;
      // a cancelled order only accepts a terminal outcome from the field (same rule as the backend)
      if (o.status === 'CANCELLED' && status !== 'DELIVERED' && status !== 'RETURNED') return o;
      changed = true;
      return { ...o, status, deliveryAttempts: Math.max(o.deliveryAttempts, e.attempt), updatedAt: e.occurredAt };
    });
    return changed ? { ...page, content } : page;
  });
  return changed;
}

export function applyOrderEvent(qc: QueryClient, e: OrderLive): void {
  qc.setQueryData<OrdersPage>(keys.orders, (page) => {
    if (!page) return page;
    const existing = page.content.find((o) => o.id === e.orderId);
    if (e.type === 'ORDER_CANCELLED') {
      if (!existing) return page;
      return { ...page, content: page.content.map((o) => (o.id === e.orderId ? { ...o, status: 'CANCELLED' } : o)) };
    }
    if (existing) return page;
    const order: Order = {
      id: e.orderId, trackingCode: e.trackingCode, merchantId: e.merchantId ?? '', recipientName: e.recipientName ?? '',
      recipientPhone: '', addressLine: '', city: e.city ?? '', codAmount: e.codAmount ?? 0, status: 'CREATED',
      deliveryAttempts: 0, cancelReason: null, createdAt: e.occurredAt, updatedAt: e.occurredAt,
    };
    return { ...page, content: [order, ...page.content], totalElements: page.totalElements + 1 };
  });
}

export function applyNotification(qc: QueryClient, n: NotificationItem, cap = 50): void {
  qc.setQueryData<NotificationItem[]>(keys.notifications, (list) => {
    const current = list ?? [];
    if (current.some((x) => x.id === n.id)) return current;
    return [n, ...current].slice(0, cap);
  });
}

// ---- the hook --------------------------------------------------------------------------

export type LiveStatus = 'connecting' | 'connected' | 'reconnecting' | 'offline';

export type EventSourceFactory = (url: string) => EventSource;
const defaultFactory: EventSourceFactory = (url) => new EventSource(url);

/**
 * One SSE connection for the whole console. EventSource cannot send an Authorization header, so we
 * first trade the session token for a 60-second stream token and pass it as ?access_token=. When the
 * connection drops we do not let EventSource retry with the (soon expired) URL: we close it, back
 * off, fetch a fresh token and reconnect. Every (re)connect refetches the queries, which covers any
 * events missed while disconnected.
 */
export function useLiveStream(enabled: boolean, factory: EventSourceFactory = defaultFactory) {
  const qc = useQueryClient();
  const [status, setStatus] = useState<LiveStatus>('connecting');
  const [lastEventAt, setLastEventAt] = useState<number | null>(null);
  const lastSeq = useRef(new Map<string, number>());

  useEffect(() => {
    if (!enabled) return;
    let source: EventSource | null = null;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let stopped = false;
    const pending = new Set<string>();
    let flushTimer: ReturnType<typeof setTimeout> | undefined;

    // coalesce bursts (a failed last attempt emits FAILED then RETURNED) into one refetch
    const invalidateSoon = (...queryKeys: (readonly unknown[])[]) => {
      queryKeys.forEach((k) => pending.add(JSON.stringify(k)));
      clearTimeout(flushTimer);
      flushTimer = setTimeout(() => {
        pending.forEach((k) => qc.invalidateQueries({ queryKey: JSON.parse(k) }));
        pending.clear();
      }, 350);
    };

    const touched = () => setLastEventAt(Date.now());

    const connect = async () => {
      setStatus(attempt === 0 ? 'connecting' : 'reconnecting');
      let token: string;
      try {
        token = (await api.streamToken()).accessToken;
      } catch {
        scheduleRetry();
        return;
      }
      if (stopped) return;
      const es = factory(`/api/activity/stream?access_token=${encodeURIComponent(token)}`);
      source = es;

      es.addEventListener('hello', (ev) => {
        attempt = 0;
        setStatus('connected');
        const hello = JSON.parse((ev as MessageEvent).data) as Hello;
        qc.setQueryData<CircuitInfo>(keys.circuit, hello.circuit);
        // anything that happened while we were away
        qc.invalidateQueries();
      });
      es.addEventListener('order', (ev) => {
        const e = JSON.parse((ev as MessageEvent).data) as OrderLive;
        applyOrderEvent(qc, e);
        touched();
        invalidateSoon(keys.stats, keys.orders, keys.timeline(e.orderId));
      });
      es.addEventListener('delivery', (ev) => {
        const e = JSON.parse((ev as MessageEvent).data) as DeliveryLive;
        applyDeliveryEvent(qc, e, lastSeq.current);
        touched();
        invalidateSoon(keys.stats, keys.couriers, keys.timeline(e.orderId));
      });
      es.addEventListener('notification', (ev) => {
        const n = JSON.parse((ev as MessageEvent).data) as NotificationItem;
        applyNotification(qc, n);
        touched();
        invalidateSoon(keys.orderNotifications(n.orderId));
      });
      es.addEventListener('circuit', (ev) => {
        qc.setQueryData<CircuitInfo>(keys.circuit, JSON.parse((ev as MessageEvent).data));
      });
      es.onerror = () => {
        es.close();
        if (source === es) source = null;
        scheduleRetry();
      };
    };

    const scheduleRetry = () => {
      if (stopped) return;
      setStatus(attempt > 3 ? 'offline' : 'reconnecting');
      const delay = Math.min(1000 * 2 ** attempt, 10_000);
      attempt++;
      retryTimer = setTimeout(connect, delay);
    };

    connect();
    return () => {
      stopped = true;
      clearTimeout(retryTimer);
      clearTimeout(flushTimer);
      source?.close();
    };
  }, [enabled, factory, qc]);

  return { status, lastEventAt };
}
