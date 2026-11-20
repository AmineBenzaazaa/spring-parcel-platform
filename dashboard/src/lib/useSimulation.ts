import { useCallback, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { api, ApiError } from './api';
import { useI18n, type MessageKey } from './i18n';
import { keys, type OrdersPage } from './queries';
import { sampleOrder, simulateDelivery, simulateFailure, waitForAssignment } from './simulate';
import type { Delivery } from './types';

type Step = 'out' | 'delivered' | 'failed';

/** The "courier app" for the demo: every action goes through the real gateway and dispatch API. */
export function useSimulation() {
  const qc = useQueryClient();
  const { t } = useI18n();
  const [running, setRunning] = useState<Set<string>>(new Set());

  const track = useCallback(async <T,>(id: string, work: () => Promise<T>): Promise<T | undefined> => {
    setRunning((s) => new Set(s).add(id));
    try {
      return await work();
    } catch (e) {
      toast.error(e instanceof ApiError || e instanceof Error ? e.message : t('error.generic'));
      return undefined;
    } finally {
      setRunning((s) => {
        const n = new Set(s);
        n.delete(id);
        return n;
      });
    }
  }, [t]);

  const done = useCallback((d: Delivery | undefined) => {
    if (d) toast.success(t('sim.done', { code: d.trackingCode, status: t(`status.${d.status}` as MessageKey) }));
  }, [t]);

  const step = useCallback((orderId: string, action: Step) => track(orderId, () =>
    action === 'out' ? api.outForDelivery(orderId)
      : action === 'delivered' ? api.delivered(orderId)
        : api.failed(orderId, 'customer absent')), [track]);

  const deliver = useCallback((orderId: string) => track(orderId, () => simulateDelivery(orderId)).then(done), [track, done]);
  const fail = useCallback((orderId: string) => track(orderId, () => simulateFailure(orderId)).then(done), [track, done]);

  /** Full story for a screen recording: create, wait for dispatch, then drive the courier steps. */
  const runNewOrder = useCallback((outcome: 'delivered' | 'returned') => track(`new-${outcome}`, async () => {
    const order = await api.createOrder(sampleOrder());
    toast(t('create.created', { code: order.trackingCode }), { description: t('sim.waitingAssign') });
    await waitForAssignment(order.id);
    await new Promise((r) => setTimeout(r, 900));
    return outcome === 'delivered' ? simulateDelivery(order.id) : simulateFailure(order.id);
  }).then(done), [track, done, t]);

  const deliverOldestAssigned = useCallback(() => {
    const page = qc.getQueryData<OrdersPage>(keys.orders);
    const oldest = page?.content.filter((o) => o.status === 'ASSIGNED')
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))[0];
    if (!oldest) {
      toast(t('sim.noAssigned'));
      return Promise.resolve();
    }
    return deliver(oldest.id);
  }, [qc, deliver, t]);

  return { running, step, deliver, fail, runNewOrder, deliverOldestAssigned };
}
