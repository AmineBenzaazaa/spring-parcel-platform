import { useState } from 'react';
import clsx from 'clsx';
import { AnimatePresence, motion } from 'framer-motion';
import { BellOff, MessageSquareText, ShieldAlert, ShieldCheck, ShieldHalf, Terminal, Zap, ZapOff } from 'lucide-react';
import { toast } from 'sonner';
import { useI18n, type MessageKey } from '@/lib/i18n';
import { relativeTime } from '@/lib/format';
import { api } from '@/lib/api';
import { useCircuit, useNotifications } from '@/lib/queries';
import type { CircuitState, NotificationStatus } from '@/lib/types';
import { Skeleton } from './ui/Skeleton';
import { EmptyState } from './ui/EmptyState';

const statusTone: Record<NotificationStatus, string> = {
  SENT: 'text-emerald-600 bg-emerald-500/10 dark:text-emerald-300',
  FAILED: 'text-rose-600 bg-rose-500/10 dark:text-rose-300',
  SKIPPED: 'text-amber-600 bg-amber-500/10 dark:text-amber-300',
};

function CircuitPill({ state }: { state: CircuitState }) {
  const { t } = useI18n();
  const tone =
    state === 'CLOSED' ? 'text-emerald-700 bg-emerald-500/10 ring-emerald-500/25 dark:text-emerald-300'
      : state === 'OPEN' || state === 'FORCED_OPEN' ? 'text-rose-700 bg-rose-500/10 ring-rose-500/25 dark:text-rose-300'
        : 'text-amber-700 bg-amber-500/10 ring-amber-500/25 dark:text-amber-300';
  const Icon = state === 'CLOSED' ? ShieldCheck : state === 'OPEN' ? ShieldAlert : ShieldHalf;
  const key = (['CLOSED', 'OPEN', 'HALF_OPEN', 'DISABLED'].includes(state) ? `circuit.${state}` : 'circuit.DISABLED') as MessageKey;
  return (
    <span data-testid="circuit-state" data-state={state}
      className={clsx('inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset', tone)}
      title="Resilience4j smsGateway">
      <Icon className="h-3 w-3" aria-hidden />
      {t('notifications.circuit')}: {t(key)}
    </span>
  );
}

export function NotificationsFeed() {
  const { t, lang } = useI18n();
  const notifications = useNotifications();
  const circuit = useCircuit();
  const [smsBroken, setSmsBroken] = useState(false);
  const [busy, setBusy] = useState(false);

  const toggleProvider = async () => {
    setBusy(true);
    try {
      await api.smsProviderMode(smsBroken ? 'OK' : 'FAIL');
      setSmsBroken(!smsBroken);
      toast(smsBroken ? t('sms.restored') : t('sms.broken'));
    } catch {
      toast.error(t('error.generic'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel flex min-h-0 flex-col" aria-labelledby="notifications-title">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-4 py-3">
        <h2 id="notifications-title" className="text-sm font-semibold">{t('notifications.title')}</h2>
        <div className="flex items-center gap-1.5">
          {circuit.data && <CircuitPill state={circuit.data.state} />}
          <button
            type="button"
            onClick={toggleProvider}
            disabled={busy}
            title={smsBroken ? t('notifications.restoreSms') : t('notifications.breakSms')}
            aria-label={smsBroken ? t('notifications.restoreSms') : t('notifications.breakSms')}
            className={clsx('focus-ring inline-flex h-6 w-6 items-center justify-center rounded-md transition-colors',
              smsBroken ? 'bg-rose-500/10 text-rose-600 hover:bg-rose-500/20' : 'text-faint hover:bg-subtle hover:text-fg')}
          >
            {smsBroken ? <Zap className="h-3.5 w-3.5" /> : <ZapOff className="h-3.5 w-3.5" />}
          </button>
        </div>
      </header>
      {!notifications.data ? (
        <div className="space-y-3 p-4">{Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-10" />)}</div>
      ) : notifications.data.length === 0 ? (
        <EmptyState icon={<BellOff className="h-5 w-5" />} title={t('notifications.empty')} hint={t('notifications.emptyHint')} />
      ) : (
        <ul className="scrollbar-thin min-h-0 flex-1 overflow-y-auto" data-testid="notifications">
          <AnimatePresence initial={false}>
            {notifications.data.map((n) => (
              <motion.li
                key={n.id}
                layout
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                transition={{ duration: 0.25 }}
                className="border-b border-line/70 last:border-0"
              >
                <div className="flex gap-3 px-4 py-2.5">
                  <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-subtle text-muted">
                    {n.channel === 'SMS' ? <MessageSquareText className="h-3.5 w-3.5" /> : <Terminal className="h-3.5 w-3.5" />}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                      <span className="text-[11px] font-semibold text-muted">{n.channel}</span>
                      <span className={clsx('rounded px-1 py-px text-[10px] font-semibold', statusTone[n.status])}>{n.status}</span>
                      <span className="font-mono text-[11px] text-faint">{n.trackingCode}</span>
                      <span className="ml-auto shrink-0 text-[11px] tabular-nums text-faint">{relativeTime(n.createdAt, lang)}</span>
                    </div>
                    <p className="mt-0.5 line-clamp-2 text-[12.5px] leading-snug text-fg/90" title={n.error ?? n.message}>
                      {n.message}
                    </p>
                    {n.error && <p className="mt-0.5 truncate text-[11px] text-rose-600 dark:text-rose-300">{n.error}</p>}
                  </div>
                </div>
              </motion.li>
            ))}
          </AnimatePresence>
        </ul>
      )}
    </section>
  );
}
