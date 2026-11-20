import { useMemo } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { AnimatePresence, motion } from 'framer-motion';
import clsx from 'clsx';
import { CircleCheck, CircleX, MessageSquareText, Play, Terminal, Truck, Undo2, X } from 'lucide-react';
import { useI18n, type MessageKey } from '@/lib/i18n';
import { formatMad, formatTime, initials } from '@/lib/format';
import { formatMoroccanPhone } from '@/lib/phone';
import { deliveriesByOrder, useCouriers, useDeliveries, useOrderNotifications, useOrders, useTimeline } from '@/lib/queries';
import { nextActions, statusStyle } from '@/lib/status';
import type { useSimulation } from '@/lib/useSimulation';
import { StatusBadge } from './ui/StatusBadge';
import { Button } from './ui/Button';
import { CopyButton } from './ui/CopyButton';
import { Skeleton } from './ui/Skeleton';

interface Row {
  key: string;
  at: string;
  kind: 'event' | 'notification';
  title: string;
  detail?: string | null;
  status: string;
  correlationId?: string | null;
  tone?: string;
  icon?: 'sms' | 'log';
}

function Info({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="label">{label}</dt>
      <dd className="mt-1 truncate text-[13px] text-fg">{children}</dd>
    </div>
  );
}

export function OrderDrawer({ orderId, onClose, sim }: {
  orderId: string | null; onClose: () => void; sim: ReturnType<typeof useSimulation>;
}) {
  const { t, lang } = useI18n();
  const orders = useOrders();
  const deliveries = useDeliveries();
  const couriers = useCouriers();
  const timeline = useTimeline(orderId);
  const notifications = useOrderNotifications(orderId);

  const order = orders.data?.content.find((o) => o.id === orderId);
  const delivery = orderId ? deliveriesByOrder(deliveries.data).get(orderId) : undefined;
  const courier = couriers.data?.find((c) => c.id === delivery?.courierId);
  const busy = orderId ? sim.running.has(orderId) : false;

  const rows = useMemo<Row[]>(() => {
    const events: Row[] = (timeline.data ?? []).map((e) => ({
      key: e.eventId,
      at: e.occurredAt,
      kind: 'event',
      status: e.status ?? 'CREATED',
      title: e.eventType === 'DELIVERY_STATUS_CHANGED' ? t(`status.${e.status}` as MessageKey) : t(`event.${e.eventType}` as MessageKey),
      detail: [e.attempt > 0 && e.eventType === 'DELIVERY_STATUS_CHANGED' ? t('board.attempts', { n: e.attempt, max: delivery?.maxAttempts ?? 3 }) : null, e.reason]
        .filter(Boolean).join(' · ') || null,
      correlationId: e.correlationId,
    }));
    const notes: Row[] = (notifications.data ?? []).map((n) => ({
      key: n.id,
      at: n.createdAt,
      kind: 'notification',
      status: n.status,
      icon: n.channel === 'SMS' ? 'sms' : 'log',
      title: t('event.notification', { channel: n.channel, status: n.status }),
      detail: n.error ?? n.message,
      tone: n.status === 'SENT' ? 'text-emerald-600 dark:text-emerald-300' : n.status === 'FAILED' ? 'text-rose-600 dark:text-rose-300' : 'text-amber-600 dark:text-amber-300',
    }));
    return [...events, ...notes].sort((a, b) => a.at.localeCompare(b.at));
  }, [timeline.data, notifications.data, t, delivery?.maxAttempts]);

  const actions = order ? nextActions(order.status) : [];
  const closed = order ? ['DELIVERED', 'RETURNED', 'CANCELLED'].includes(order.status) : false;

  return (
    <Dialog.Root open={!!orderId} onOpenChange={(o) => !o && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/20" />
        <Dialog.Content
          className="fixed inset-y-0 right-0 z-50 flex w-full max-w-[440px] flex-col border-l border-line bg-panel shadow-lift focus:outline-none"
          data-testid="order-drawer"
          aria-describedby={undefined}
        >
          {!order ? (
            <div className="space-y-3 p-5"><Skeleton className="h-6 w-40" /><Skeleton className="h-24" /><Skeleton className="h-64" /></div>
          ) : (
            <>
              <header className="border-b border-line px-5 py-4">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <Dialog.Title className="font-mono text-[15px] font-semibold tracking-tight">{order.trackingCode}</Dialog.Title>
                      <CopyButton value={order.trackingCode} />
                    </div>
                    <div className="mt-1.5"><StatusBadge status={order.status} /></div>
                  </div>
                  <Dialog.Close className="focus-ring rounded-md p-1 text-faint hover:bg-subtle hover:text-fg" aria-label="Close">
                    <X className="h-4 w-4" />
                  </Dialog.Close>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3">
                  <Info label={t('drawer.recipient')}>
                    <span className="font-medium">{order.recipientName}</span>
                    {order.recipientPhone && <span className="block font-mono text-[12px] text-muted">{formatMoroccanPhone(order.recipientPhone)}</span>}
                  </Info>
                  <Info label={t('drawer.cod')}><span className="text-[15px] font-semibold tabular-nums">{formatMad(order.codAmount, lang)}</span></Info>
                  <Info label={t('create.address')}>
                    <span title={order.addressLine}>{order.addressLine || '—'}</span>
                    <span className="block text-[12px] text-muted">{order.city}{delivery?.zone ? ` · ${t('drawer.zone')} ${delivery.zone}` : ''}</span>
                  </Info>
                  <Info label={t('drawer.courier')}>
                    {courier ? (
                      <span className="inline-flex items-center gap-1.5">
                        <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-accent/15 text-[9px] font-semibold text-accent">{initials(courier.name)}</span>
                        {courier.name}
                      </span>
                    ) : <span className="text-faint">{t('board.unassigned')}</span>}
                    <span className="block text-[12px] text-muted">
                      {t('drawer.attempts')}: {Math.max(delivery?.attempts ?? 0, order.deliveryAttempts)}/{delivery?.maxAttempts ?? 3}
                    </span>
                  </Info>
                </dl>
              </header>

              <section className="border-b border-line px-5 py-4" aria-label={t('drawer.actions')}>
                <p className="label mb-2.5">{t('drawer.actions')}</p>
                {closed ? (
                  <p className="text-[12.5px] text-muted">{t('drawer.noActions')}</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {actions.includes('out') && (
                      <Button size="sm" onClick={() => sim.step(order.id, 'out')} disabled={busy}><Truck className="h-3.5 w-3.5" />{t('actions.out')}</Button>
                    )}
                    {actions.includes('delivered') && (
                      <Button size="sm" onClick={() => sim.step(order.id, 'delivered')} disabled={busy}><CircleCheck className="h-3.5 w-3.5" />{t('actions.delivered')}</Button>
                    )}
                    {actions.includes('failed') && (
                      <Button size="sm" onClick={() => sim.step(order.id, 'failed')} disabled={busy}><CircleX className="h-3.5 w-3.5" />{t('actions.failed')}</Button>
                    )}
                    {(order.status === 'ASSIGNED' || order.status === 'OUT_FOR_DELIVERY' || order.status === 'DELIVERY_FAILED') && (
                      <>
                        <Button size="sm" variant="primary" loading={busy} onClick={() => sim.deliver(order.id)} data-testid="simulate-delivery">
                          {!busy && <Play className="h-3.5 w-3.5" />}{t('actions.simulateDelivery')}
                        </Button>
                        <Button size="sm" variant="ghost" disabled={busy} onClick={() => sim.fail(order.id)} data-testid="simulate-failure"
                          className="text-rose-600 hover:bg-rose-500/10 hover:text-rose-600 dark:text-rose-400">
                          <Undo2 className="h-3.5 w-3.5" />{t('actions.failDelivery')}
                        </Button>
                      </>
                    )}
                  </div>
                )}
              </section>

              <section className="scrollbar-thin min-h-0 flex-1 overflow-y-auto px-5 py-4" aria-label={t('drawer.timeline')}>
                <p className="label">{t('drawer.timeline')}</p>
                <p className="mb-4 mt-1 text-[12px] text-faint">{t('drawer.timelineHint')}</p>
                {rows.length === 0 ? (
                  <p className="text-[12.5px] text-muted">{t('drawer.noEvents')}</p>
                ) : (
                  <ol className="relative ml-1.5 border-l border-line" data-testid="timeline">
                    <AnimatePresence initial={false}>
                      {rows.map((r) => (
                        <motion.li key={r.key} initial={{ opacity: 0, x: 8 }} animate={{ opacity: 1, x: 0 }}
                          className="relative pb-4 pl-5 last:pb-0" data-kind={r.kind} data-status={r.status}>
                          <span className={clsx('absolute -left-[5px] top-1.5 h-[9px] w-[9px] rounded-full ring-[3px] ring-panel',
                            r.kind === 'event' ? statusStyle(r.status).dot : 'bg-line')} />
                          <div className="flex items-baseline justify-between gap-3">
                            <span className={clsx('inline-flex items-center gap-1.5 text-[13px]', r.kind === 'event' ? 'font-medium text-fg' : r.tone)}>
                              {r.icon === 'sms' && <MessageSquareText className="h-3 w-3" />}
                              {r.icon === 'log' && <Terminal className="h-3 w-3" />}
                              {r.title}
                            </span>
                            <time className="shrink-0 font-mono text-[11px] tabular-nums text-faint" dateTime={r.at}>{formatTime(r.at, lang, true)}</time>
                          </div>
                          {r.detail && <p className="mt-0.5 text-[12px] leading-snug text-muted">{r.detail}</p>}
                          {r.correlationId && (
                            <div className="mt-1 inline-flex items-center gap-1 rounded bg-subtle px-1.5 py-0.5">
                              <span className="font-mono text-[10.5px] text-muted" title="X-Correlation-Id">corr {r.correlationId}</span>
                              <CopyButton value={r.correlationId} />
                            </div>
                          )}
                        </motion.li>
                      ))}
                    </AnimatePresence>
                  </ol>
                )}
              </section>
            </>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
