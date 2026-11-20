import clsx from 'clsx';
import { useI18n, type MessageKey } from '@/lib/i18n';
import { statusStyle } from '@/lib/status';

export function StatusDot({ status, pulse }: { status: string; pulse?: boolean }) {
  const s = statusStyle(status);
  return (
    <span className="relative inline-flex h-2 w-2 shrink-0">
      {pulse && <span className={clsx('absolute inset-0 rounded-full animate-pulse-ring', s.dot)} />}
      <span className={clsx('relative inline-flex h-2 w-2 rounded-full', s.dot)} />
    </span>
  );
}

export function StatusBadge({ status, className }: { status: string; className?: string }) {
  const { t } = useI18n();
  const s = statusStyle(status);
  return (
    <span
      data-testid="status-badge"
      className={clsx(
        'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset',
        s.bg, s.text, s.ring, className,
      )}
    >
      <StatusDot status={status} />
      {t(`status.${status}` as MessageKey)}
    </span>
  );
}
