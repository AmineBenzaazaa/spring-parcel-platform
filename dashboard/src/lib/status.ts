import type { DeliveryStatus, OrderStatus } from './types';

/** Board columns, left to right. CANCELLED orders are not on the board. */
export const COLUMNS = ['CREATED', 'ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNED'] as const;
export type Column = (typeof COLUMNS)[number];

/** Same mapping as Order.map(DeliveryStatusV1) in order-service. */
export function orderStatusFor(delivery: DeliveryStatus): OrderStatus | null {
  switch (delivery) {
    case 'ASSIGNED':
      return 'ASSIGNED';
    case 'OUT_FOR_DELIVERY':
      return 'OUT_FOR_DELIVERY';
    case 'FAILED':
      return 'DELIVERY_FAILED';
    case 'DELIVERED':
      return 'DELIVERED';
    case 'RETURNED':
      return 'RETURNED';
    case 'CANCELLED':
      return 'CANCELLED';
    default:
      return null;
  }
}

export interface StatusStyle {
  dot: string;
  text: string;
  bg: string;
  ring: string;
}

// Brand semantic tones: navy = moving, green signal = delivered/paid, warn = retry, danger = failed,
// neutral = waiting. RETURNED is neutral-danger: the parcel is back, no cash collected.
const neutral: StatusStyle = { dot: 'bg-faint', text: 'text-muted', bg: 'bg-subtle', ring: 'ring-line' };
const moving: StatusStyle = { dot: 'bg-accent', text: 'text-accent', bg: 'bg-accent/10', ring: 'ring-accent/25' };
const signal: StatusStyle = { dot: 'bg-signal', text: 'text-signal-ink', bg: 'bg-signal/10', ring: 'ring-signal/25' };
const warn: StatusStyle = { dot: 'bg-warn', text: 'text-warn', bg: 'bg-warn/10', ring: 'ring-warn/25' };
const danger: StatusStyle = { dot: 'bg-danger', text: 'text-danger', bg: 'bg-danger/10', ring: 'ring-danger/25' };

const styles: Record<string, StatusStyle> = {
  CREATED: neutral,
  PENDING_ASSIGNMENT: neutral,
  ASSIGNED: moving,
  OUT_FOR_DELIVERY: { ...moving, dot: 'bg-info', text: 'text-info', bg: 'bg-info/10', ring: 'ring-info/25' },
  DELIVERED: signal,
  DELIVERY_FAILED: warn,
  FAILED: warn,
  RETURNED: danger,
  CANCELLED: neutral,
};

export function statusStyle(status: string): StatusStyle {
  return styles[status] ?? styles.CREATED;
}

/** Courier steps the ops user may trigger from a given order status. */
export function nextActions(status: OrderStatus): Array<'out' | 'delivered' | 'failed'> {
  switch (status) {
    case 'ASSIGNED':
    case 'DELIVERY_FAILED':
      return ['out'];
    case 'OUT_FOR_DELIVERY':
      return ['delivered', 'failed'];
    default:
      return [];
  }
}
