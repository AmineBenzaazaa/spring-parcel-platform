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

const styles: Record<string, StatusStyle> = {
  CREATED: { dot: 'bg-zinc-400', text: 'text-zinc-600 dark:text-zinc-300', bg: 'bg-zinc-500/10', ring: 'ring-zinc-500/20' },
  PENDING_ASSIGNMENT: { dot: 'bg-zinc-400', text: 'text-zinc-600 dark:text-zinc-300', bg: 'bg-zinc-500/10', ring: 'ring-zinc-500/20' },
  ASSIGNED: { dot: 'bg-sky-500', text: 'text-sky-700 dark:text-sky-300', bg: 'bg-sky-500/10', ring: 'ring-sky-500/25' },
  OUT_FOR_DELIVERY: { dot: 'bg-amber-500', text: 'text-amber-700 dark:text-amber-300', bg: 'bg-amber-500/10', ring: 'ring-amber-500/25' },
  DELIVERED: { dot: 'bg-emerald-500', text: 'text-emerald-700 dark:text-emerald-300', bg: 'bg-emerald-500/10', ring: 'ring-emerald-500/25' },
  DELIVERY_FAILED: { dot: 'bg-rose-500', text: 'text-rose-700 dark:text-rose-300', bg: 'bg-rose-500/10', ring: 'ring-rose-500/25' },
  FAILED: { dot: 'bg-rose-500', text: 'text-rose-700 dark:text-rose-300', bg: 'bg-rose-500/10', ring: 'ring-rose-500/25' },
  RETURNED: { dot: 'bg-violet-500', text: 'text-violet-700 dark:text-violet-300', bg: 'bg-violet-500/10', ring: 'ring-violet-500/25' },
  CANCELLED: { dot: 'bg-zinc-300', text: 'text-zinc-500', bg: 'bg-zinc-500/10', ring: 'ring-zinc-500/20' },
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
