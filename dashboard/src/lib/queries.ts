import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import type { Delivery, Order, Page } from './types';

export const keys = {
  orders: ['orders'] as const,
  deliveries: ['deliveries'] as const,
  couriers: ['couriers'] as const,
  stats: ['stats'] as const,
  notifications: ['notifications'] as const,
  circuit: ['circuit'] as const,
  health: ['health'] as const,
  timeline: (orderId: string) => ['timeline', orderId] as const,
  orderNotifications: (orderId: string) => ['order-notifications', orderId] as const,
};

export const BOARD_SIZE = 100;

// Live events keep these fresh; the intervals are only a safety net for anything the stream missed.
export const useOrders = () =>
  useQuery({ queryKey: keys.orders, queryFn: () => api.orders(BOARD_SIZE), refetchInterval: 30_000 });
export const useDeliveries = () =>
  useQuery({ queryKey: keys.deliveries, queryFn: () => api.deliveries(200), refetchInterval: 30_000 });
export const useCouriers = () => useQuery({ queryKey: keys.couriers, queryFn: api.couriers, refetchInterval: 30_000 });
export const useStats = () => useQuery({ queryKey: keys.stats, queryFn: api.stats, refetchInterval: 30_000 });
export const useNotifications = () =>
  useQuery({ queryKey: keys.notifications, queryFn: () => api.notifications(), refetchInterval: 60_000 });
export const useCircuit = () => useQuery({ queryKey: keys.circuit, queryFn: api.circuit, refetchInterval: 15_000 });
export const useHealth = () =>
  useQuery({ queryKey: keys.health, queryFn: api.health, refetchInterval: 10_000, retry: false });
export const useTimeline = (orderId: string | null) =>
  useQuery({ queryKey: keys.timeline(orderId ?? ''), queryFn: () => api.timeline(orderId!), enabled: !!orderId });
export const useOrderNotifications = (orderId: string | null) =>
  useQuery({
    queryKey: keys.orderNotifications(orderId ?? ''),
    queryFn: () => api.notifications(orderId!),
    enabled: !!orderId,
  });

export type OrdersPage = Page<Order>;

export function deliveriesByOrder(list: Delivery[] | undefined): Map<string, Delivery> {
  const map = new Map<string, Delivery>();
  list?.forEach((d) => map.set(d.orderId, d));
  return map;
}
