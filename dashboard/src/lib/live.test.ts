import { QueryClient } from '@tanstack/react-query';
import { applyDeliveryEvent, applyNotification, applyOrderEvent } from './live';
import { keys, type OrdersPage } from './queries';
import type { Delivery, DeliveryLive, NotificationItem, Order } from './types';

const order = (over: Partial<Order> = {}): Order => ({
  id: 'o1', trackingCode: 'PX-AAAA1111', merchantId: 'M-1001', recipientName: 'Salma Idrissi',
  recipientPhone: '+212612345678', addressLine: '12 Rue Atlas', city: 'Casablanca', codAmount: 249.9,
  status: 'ASSIGNED', deliveryAttempts: 0, cancelReason: null, createdAt: '2026-11-20T10:00:00Z',
  updatedAt: '2026-11-20T10:00:00Z', ...over,
});

const event = (over: Partial<DeliveryLive> = {}): DeliveryLive => ({
  eventId: crypto.randomUUID(), orderId: 'o1', trackingCode: 'PX-AAAA1111', courierId: 'c1',
  previousStatus: 'ASSIGNED', newStatus: 'OUT_FOR_DELIVERY', attempt: 1, reason: null, codAmount: 249.9,
  sequence: 3, correlationId: 'ui-abc', occurredAt: '2026-11-20T10:05:00Z', ...over,
});

function client(orders: Order[], deliveries: Delivery[] = []) {
  const qc = new QueryClient();
  qc.setQueryData<OrdersPage>(keys.orders, { content: orders, page: 0, size: 100, totalElements: orders.length, totalPages: 1 });
  qc.setQueryData<Delivery[]>(keys.deliveries, deliveries);
  return qc;
}

const statusOf = (qc: QueryClient, id = 'o1') => qc.getQueryData<OrdersPage>(keys.orders)!.content.find((o) => o.id === id)!.status;

describe('applyDeliveryEvent', () => {
  it('moves the card to the projected order status and records the attempt', () => {
    const qc = client([order()]);
    expect(applyDeliveryEvent(qc, event(), new Map())).toBe(true);
    expect(statusOf(qc)).toBe('OUT_FOR_DELIVERY');
    expect(qc.getQueryData<Delivery[]>(keys.deliveries)![0]).toMatchObject({ courierId: 'c1', attempts: 1 });
  });

  it('maps FAILED to DELIVERY_FAILED and ignores stale sequences (redelivery / retry topic)', () => {
    const qc = client([order()]);
    const seq = new Map<string, number>();
    applyDeliveryEvent(qc, event({ newStatus: 'FAILED', sequence: 4 }), seq);
    expect(statusOf(qc)).toBe('DELIVERY_FAILED');
    expect(applyDeliveryEvent(qc, event({ newStatus: 'OUT_FOR_DELIVERY', sequence: 3 }), seq)).toBe(false);
    expect(statusOf(qc)).toBe('DELIVERY_FAILED');
  });

  it('lets a cancelled order accept only a terminal field outcome', () => {
    const qc = client([order({ status: 'CANCELLED' })]);
    const seq = new Map<string, number>();
    applyDeliveryEvent(qc, event({ newStatus: 'OUT_FOR_DELIVERY', sequence: 2 }), seq);
    expect(statusOf(qc)).toBe('CANCELLED');
    applyDeliveryEvent(qc, event({ newStatus: 'DELIVERED', sequence: 3 }), seq);
    expect(statusOf(qc)).toBe('DELIVERED');
  });
});

describe('applyOrderEvent / applyNotification', () => {
  it('adds a newly created order once', () => {
    const qc = client([]);
    const created = { eventId: 'e1', type: 'ORDER_CREATED' as const, orderId: 'o9', trackingCode: 'PX-NEW00001',
      merchantId: 'M-1001', recipientName: 'Mehdi', city: 'Rabat', codAmount: 120, reason: null, correlationId: 'c',
      occurredAt: '2026-11-20T11:00:00Z' };
    applyOrderEvent(qc, created);
    applyOrderEvent(qc, created);
    const page = qc.getQueryData<OrdersPage>(keys.orders)!;
    expect(page.content).toHaveLength(1);
    expect(page.content[0]).toMatchObject({ status: 'CREATED', city: 'Rabat' });
  });

  it('prepends notifications, dedupes by id and caps the feed', () => {
    const qc = new QueryClient();
    const n = (id: string): NotificationItem => ({ id, orderId: 'o1', trackingCode: 'PX', channel: 'SMS', message: 'm',
      status: 'SENT', error: null, createdAt: '2026-11-20T10:00:00Z' });
    applyNotification(qc, n('a'), 2);
    applyNotification(qc, n('b'), 2);
    applyNotification(qc, n('b'), 2);
    applyNotification(qc, n('c'), 2);
    expect(qc.getQueryData<NotificationItem[]>(keys.notifications)!.map((x) => x.id)).toEqual(['c', 'b']);
  });
});
