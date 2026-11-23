import { memo, useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import clsx from 'clsx';
import { Banknote, MapPin, RotateCcw, Truck } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import { formatMad, initials, relativeTime } from '@/lib/format';
import type { Order } from '@/lib/types';

export interface OrderCardProps {
  order: Order;
  courierName?: string | null;
  attempts: number;
  maxAttempts: number;
  onOpen: (orderId: string) => void;
}

/**
 * One parcel on the board. `layoutId` lets framer-motion animate the card from its old column to
 * the new one when a live event changes its status; a short ring flash marks what just moved.
 */
export const OrderCard = memo(function OrderCard({ order, courierName, attempts, maxAttempts, onOpen }: OrderCardProps) {
  const { t, lang } = useI18n();
  const previous = useRef(order.status);
  // a card that changed column is a fresh mount, so "just updated" is judged from updatedAt too
  const [flash, setFlash] = useState(() => Date.now() - Date.parse(order.updatedAt) < 2500);

  useEffect(() => {
    if (previous.current !== order.status) {
      previous.current = order.status;
      setFlash(true);
    }
  }, [order.status]);

  useEffect(() => {
    if (!flash) return;
    const id = setTimeout(() => setFlash(false), 1200);
    return () => clearTimeout(id);
  }, [flash]);

  return (
    <motion.button
      layout
      layoutId={order.id}
      initial={{ opacity: 0, y: -6, scale: 0.98 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ type: 'spring', stiffness: 520, damping: 40, mass: 0.8 }}
      type="button"
      onClick={() => onOpen(order.id)}
      data-testid="order-card"
      data-tracking={order.trackingCode}
      data-status={order.status}
      className={clsx(
        'focus-ring group w-full rounded-lg border border-line bg-panel p-3 text-left shadow-card transition-[border-color,box-shadow] hover:border-faint/60 hover:shadow-lift',
        flash && 'animate-flash',
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="whitespace-nowrap font-mono text-[12px] font-medium tracking-tight text-fg">{order.trackingCode}</span>
        <span className="truncate whitespace-nowrap text-[11px] tabular-nums text-faint">{relativeTime(order.updatedAt, lang)}</span>
      </div>
      <p className="mt-1.5 truncate text-[13px] font-medium text-fg">{order.recipientName || '—'}</p>
      <div className="mt-1 flex items-center justify-between gap-2 text-[12px] text-muted">
        <span className="inline-flex min-w-0 items-center gap-1">
          <MapPin className="h-3 w-3 shrink-0 text-faint" aria-hidden />
          <span className="truncate">{order.city}</span>
        </span>
        <span className="inline-flex shrink-0 items-center gap-1 whitespace-nowrap font-medium tabular-nums text-fg/80">
          <Banknote className="h-3 w-3 shrink-0 text-faint" aria-hidden />
          {formatMad(order.codAmount, lang)}
        </span>
      </div>
      <div className="mt-2.5 flex items-center justify-between gap-2 border-t border-line/70 pt-2">
        <span className="inline-flex min-w-0 items-center gap-1.5 text-[12px] text-muted">
          {courierName ? (
            <>
              <span className="avatar h-5 w-5 text-[8.5px]">
                {initials(courierName)}
              </span>
              <span className="truncate">{courierName}</span>
            </>
          ) : (
            <>
              <Truck className="h-3 w-3 text-faint" aria-hidden />
              <span className="text-faint">{t('board.unassigned')}</span>
            </>
          )}
        </span>
        {attempts > 0 && (
          <span
            title={t('attempts.hint', { n: attempts, max: maxAttempts })}
            className={clsx(
              'inline-flex shrink-0 items-center gap-1 rounded px-1.5 py-0.5 text-[10.5px] font-medium tabular-nums',
              attempts >= maxAttempts && order.status !== 'DELIVERED'
                ? 'bg-danger/10 text-danger'
                : 'bg-subtle text-muted',
            )}
          >
            <RotateCcw className="h-2.5 w-2.5" aria-hidden />
            <span className="sr-only">{t('board.attempts', { n: attempts, max: maxAttempts })}</span>
            <span aria-hidden>{attempts}/{maxAttempts}</span>
          </span>
        )}
      </div>
    </motion.button>
  );
});
