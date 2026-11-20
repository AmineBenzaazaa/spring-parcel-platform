import { useMemo } from 'react';
import { AnimatePresence, LayoutGroup } from 'framer-motion';
import { Inbox, PackagePlus } from 'lucide-react';
import { useI18n, type MessageKey } from '@/lib/i18n';
import { COLUMNS, statusStyle, type Column } from '@/lib/status';
import { deliveriesByOrder, useCouriers, useDeliveries, useOrders } from '@/lib/queries';
import { formatMad } from '@/lib/format';
import type { Order } from '@/lib/types';
import { OrderCard } from './OrderCard';
import { StatusDot } from './ui/StatusBadge';
import { Skeleton } from './ui/Skeleton';
import { EmptyState } from './ui/EmptyState';
import { Button } from './ui/Button';

export function Board({ onOpen, onCreate }: { onOpen: (orderId: string) => void; onCreate: () => void }) {
  const { t, lang } = useI18n();
  const orders = useOrders();
  const deliveries = useDeliveries();
  const couriers = useCouriers();

  const byOrder = useMemo(() => deliveriesByOrder(deliveries.data), [deliveries.data]);
  const courierNames = useMemo(() => new Map(couriers.data?.map((c) => [c.id, c.name])), [couriers.data]);
  const columns = useMemo(() => {
    const groups = new Map<Column, Order[]>(COLUMNS.map((c) => [c, []]));
    orders.data?.content.forEach((o) => groups.get(o.status as Column)?.push(o));
    // most recently changed on top: that is where the eye goes when a card lands
    groups.forEach((list) => list.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)));
    return groups;
  }, [orders.data]);

  const total = orders.data?.content.filter((o) => o.status !== 'CANCELLED').length ?? 0;

  return (
    <section className="panel flex min-h-0 flex-col" aria-labelledby="board-title">
      <header className="flex items-center justify-between gap-3 border-b border-line px-4 py-3">
        <div>
          <h2 id="board-title" className="text-sm font-semibold">{t('board.title')}</h2>
          <p className="text-[12px] text-muted">{t('board.subtitle')}</p>
        </div>
      </header>

      {orders.isLoading ? (
        <div className="grid grid-cols-6 gap-3 p-3">
          {COLUMNS.map((c) => (
            <div key={c} className="space-y-2">
              <Skeleton className="h-4 w-20" />
              <Skeleton className="h-24" />
              <Skeleton className="h-24" />
            </div>
          ))}
        </div>
      ) : total === 0 ? (
        <EmptyState
          icon={<Inbox className="h-5 w-5" />}
          title={t('board.noOrders')}
          hint={t('board.noOrdersHint')}
          action={<Button variant="primary" size="sm" onClick={onCreate}><PackagePlus className="h-3.5 w-3.5" />{t('actions.createOrder')}</Button>}
        />
      ) : (
        <LayoutGroup>
          <div className="scrollbar-thin grid min-h-0 flex-1 auto-cols-[minmax(232px,1fr)] grid-flow-col gap-3 overflow-x-auto p-3">
            {COLUMNS.map((column) => {
              const list = columns.get(column) ?? [];
              const cod = list.reduce((sum, o) => sum + o.codAmount, 0);
              return (
                <div key={column} data-testid={`column-${column}`} className="flex min-h-[320px] min-w-0 flex-col rounded-lg bg-subtle/60 p-2">
                  <div className="mb-2 flex items-center justify-between px-1.5 pt-0.5">
                    <span className="inline-flex items-center gap-2 text-[12px] font-medium text-fg">
                      <StatusDot status={column} />
                      {t(`status.${column}` as MessageKey)}
                      <span className="rounded bg-panel px-1.5 text-[11px] tabular-nums text-muted ring-1 ring-line">{list.length}</span>
                    </span>
                    {list.length > 0 && <span className="text-[11px] tabular-nums text-faint">{formatMad(cod, lang, true)}</span>}
                  </div>
                  <div className="scrollbar-thin flex max-h-[calc(100vh-380px)] min-h-[120px] flex-col gap-2 overflow-y-auto pb-1">
                    <AnimatePresence initial={false} mode="popLayout">
                      {list.map((o) => {
                        const d = byOrder.get(o.id);
                        return (
                          <OrderCard
                            key={o.id}
                            order={o}
                            courierName={d?.courierId ? courierNames.get(d.courierId) : null}
                            attempts={Math.max(d?.attempts ?? 0, o.deliveryAttempts)}
                            maxAttempts={d?.maxAttempts ?? 3}
                            onOpen={onOpen}
                          />
                        );
                      })}
                    </AnimatePresence>
                    {list.length === 0 && (
                      <div className={`flex flex-1 items-center justify-center rounded-md border border-dashed border-line px-3 py-6 text-center text-[12px] ${statusStyle(column).text} opacity-60`}>
                        {column === 'CREATED' ? t('board.emptyCreated') : t('board.empty')}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </LayoutGroup>
      )}
    </section>
  );
}
