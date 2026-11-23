import type { ReactNode } from 'react';
import { AlertTriangle, Banknote, CircleCheck, Package, Truck } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { formatMad, formatNumber } from '@/lib/format';
import { useStats } from '@/lib/queries';
import type { OrderStats } from '@/lib/types';
import { Skeleton } from './ui/Skeleton';
import { Term } from './ui/Term';

function Tile({ label, value, hint, icon, tone, testId }: {
  label: ReactNode; value: ReactNode; hint: string; icon: ReactNode; tone: string; testId: string;
}) {
  return (
    <div className="panel flex flex-col gap-2 p-4" data-testid={testId}>
      <div className="flex items-center justify-between gap-2">
        <span className="text-[12px] font-medium text-muted">{label}</span>
        <span className={`flex h-6 w-6 items-center justify-center rounded-md ${tone}`}>{icon}</span>
      </div>
      <div className="text-[26px] font-semibold leading-none tracking-tight tabular-nums">{value}</div>
      <p className="text-[12px] leading-snug text-faint">{hint}</p>
    </div>
  );
}

export function KpiTilesView({ stats }: { stats: OrderStats }) {
  const { t, lang } = useI18n();
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
      <Tile testId="kpi-orders" label={t('kpi.ordersToday')} value={formatNumber(stats.ordersToday, lang)}
        hint={new Date(stats.since).toLocaleDateString(lang === 'fr' ? 'fr-MA' : 'en-GB', { weekday: 'long', day: 'numeric', month: 'long' })}
        icon={<Package className="h-3.5 w-3.5" />} tone="bg-accent/10 text-accent" />
      <Tile testId="kpi-delivered" label={t('kpi.deliveredRate')}
        value={stats.deliveredRate == null ? '—' : `${formatNumber(stats.deliveredRate, lang)}%`}
        hint={stats.deliveredRate == null ? t('kpi.noneClosed') : t('kpi.deliveredRateHint', { n: stats.deliveredToday })}
        icon={<CircleCheck className="h-3.5 w-3.5" />} tone="bg-signal/12 text-signal-ink" />
      <Tile testId="kpi-cod" label={<Term k="cod">{t('kpi.cod')}</Term>}
        value={<>{formatMad(stats.codCollectedToday, lang).replace(' MAD', '')}<span className="ml-1 text-sm font-medium text-muted">MAD</span></>}
        hint={t('kpi.codHint')}
        icon={<Banknote className="h-3.5 w-3.5" />} tone="bg-signal/12 text-signal-ink" />
      <Tile testId="kpi-transit" label={t('kpi.inTransit')} value={formatNumber(stats.inTransit, lang)} hint={t('kpi.inTransitHint')}
        icon={<Truck className="h-3.5 w-3.5" />} tone="bg-accent/10 text-accent" />
      <Tile testId="kpi-failed" label={<Term k="returned">{t('kpi.failedReturned')}</Term>} value={formatNumber(stats.failedOrReturnedToday, lang)}
        hint={t('kpi.failedReturnedHint')}
        icon={<AlertTriangle className="h-3.5 w-3.5" />} tone="bg-danger/10 text-danger" />
    </div>
  );
}

export function KpiTiles() {
  const stats = useStats();
  if (!stats.data) {
    return (
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
        {Array.from({ length: 5 }, (_, i) => (
          <div key={i} className="panel space-y-3 p-4">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-7 w-16" />
            <Skeleton className="h-3 w-32" />
          </div>
        ))}
      </div>
    );
  }
  return <KpiTilesView stats={stats.data} />;
}
