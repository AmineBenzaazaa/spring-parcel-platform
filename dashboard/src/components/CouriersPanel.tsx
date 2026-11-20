import clsx from 'clsx';
import { Users } from 'lucide-react';
import { useI18n, type MessageKey } from '@/lib/i18n';
import { initials } from '@/lib/format';
import { useCouriers } from '@/lib/queries';
import type { Availability } from '@/lib/types';
import { Skeleton } from './ui/Skeleton';
import { EmptyState } from './ui/EmptyState';

const availabilityStyle: Record<Availability, string> = {
  ON_ROUTE: 'bg-amber-500',
  AT_HUB: 'bg-sky-500',
  IDLE: 'bg-emerald-500',
  OFFLINE: 'bg-zinc-400',
};

/** Soft cap used only to draw the load bar. */
const CAPACITY = 6;

export function CouriersPanel() {
  const { t } = useI18n();
  const couriers = useCouriers();

  return (
    <section className="panel flex min-h-0 flex-col" aria-labelledby="couriers-title">
      <header className="flex items-center justify-between border-b border-line px-4 py-3">
        <h2 id="couriers-title" className="text-sm font-semibold">{t('couriers.title')}</h2>
        {couriers.data && (
          <span className="text-[12px] tabular-nums text-muted">
            {t('couriers.onRoad', { n: couriers.data.filter((c) => c.availability === 'ON_ROUTE').length })}
          </span>
        )}
      </header>
      {!couriers.data ? (
        <div className="space-y-3 p-4">
          {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-9" />)}
        </div>
      ) : couriers.data.length === 0 ? (
        <EmptyState icon={<Users className="h-5 w-5" />} title="—" />
      ) : (
        <ul className="scrollbar-thin divide-y divide-line/70 overflow-y-auto" data-testid="couriers">
          {couriers.data.map((c) => (
            <li key={c.id} className="flex items-center gap-3 px-4 py-2.5">
              <span className="relative">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-subtle text-[11px] font-semibold text-muted ring-1 ring-line">
                  {initials(c.name)}
                </span>
                <span className={clsx('absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full ring-2 ring-panel', availabilityStyle[c.availability])} />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="truncate text-[13px] font-medium">{c.name}</span>
                  <span className="shrink-0 rounded bg-subtle px-1.5 py-0.5 font-mono text-[10px] font-medium text-muted">{c.zone}</span>
                </div>
                <div className="mt-1.5 flex items-center gap-2">
                  <div className="h-1 flex-1 overflow-hidden rounded-full bg-subtle">
                    <div
                      className={clsx('h-full rounded-full transition-[width] duration-500', c.activeLoad >= CAPACITY ? 'bg-rose-500' : 'bg-accent')}
                      style={{ width: `${Math.min(100, (c.activeLoad / CAPACITY) * 100)}%` }}
                    />
                  </div>
                  <span className="w-[86px] shrink-0 text-right text-[11px] tabular-nums text-faint">
                    {c.availability === 'OFFLINE' ? t('couriers.OFFLINE') : t('couriers.load', { n: c.activeLoad })}
                  </span>
                </div>
                <span className="sr-only">{t(`couriers.${c.availability}` as MessageKey)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
