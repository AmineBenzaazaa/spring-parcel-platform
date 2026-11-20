import clsx from 'clsx';
import { useI18n } from '@/lib/i18n';
import type { LiveStatus } from '@/lib/live';

export function LiveIndicator({ status }: { status: LiveStatus }) {
  const { t } = useI18n();
  const color = status === 'connected' ? 'bg-emerald-500' : status === 'offline' ? 'bg-rose-500' : 'bg-amber-500';
  return (
    <span
      data-testid="live-indicator"
      data-status={status}
      className="inline-flex items-center gap-1.5 rounded-full border border-line bg-panel px-2 py-0.5 text-[11.5px] font-medium text-muted"
      title="Server-Sent Events: /api/activity/stream"
    >
      <span className="relative flex h-2 w-2">
        {status === 'connected' && <span className={clsx('absolute inset-0 rounded-full animate-pulse-ring', color)} />}
        <span className={clsx('relative h-2 w-2 rounded-full', color)} />
      </span>
      {t(`live.${status}`)}
    </span>
  );
}
