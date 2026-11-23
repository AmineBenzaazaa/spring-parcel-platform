import clsx from 'clsx';
import { useI18n } from '@/lib/i18n';
import { useHealth } from '@/lib/queries';

const label = (name: string) => name.replace('-service', '');

/** Gateway-aggregated actuator health of every service plus the Kafka connection. */
export function HealthStrip() {
  const { t } = useI18n();
  const health = useHealth();
  const components = health.data?.components ?? [];
  const overall = health.isError ? 'DOWN' : health.data?.status;

  return (
    <div className="flex items-center gap-x-4 gap-y-1.5 overflow-x-auto whitespace-nowrap text-[12px] scrollbar-thin" data-testid="health-strip"
      role="status" aria-label={t('health.label')}>
      <span className="label">{t('health.label')}</span>
      <span className="inline-flex items-center gap-1.5 font-medium text-fg">
        <span className={clsx('h-1.5 w-1.5 rounded-full',
          overall === 'UP' ? 'bg-signal' : overall === 'DEGRADED' ? 'bg-warn' : overall ? 'bg-danger' : 'bg-faint')} />
        {overall ? t(`health.${overall}` as 'health.UP') : t('health.title')}
      </span>
      <span className="h-3 w-px bg-line" aria-hidden />
      {components.map((c) => (
        <span key={c.name} className="inline-flex items-center gap-1.5 text-muted" title={`${c.name}: ${c.status} (${c.latencyMs} ms)`}
          data-component={c.name} data-status={c.status}>
          <span className={clsx('h-1.5 w-1.5 rounded-full', c.status === 'UP' ? 'bg-signal' : 'bg-danger')} />
          {label(c.name)}
          {c.name !== 'gateway' && <span className="tabular-nums text-faint">{c.latencyMs}ms</span>}
        </span>
      ))}
    </div>
  );
}
