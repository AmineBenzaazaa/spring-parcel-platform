import { useState, type FormEvent, type ReactNode } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import clsx from 'clsx';
import { Sparkles, X } from 'lucide-react';
import { toast } from 'sonner';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '@/lib/api';
import { useI18n } from '@/lib/i18n';
import { normalizeMoroccanPhone } from '@/lib/phone';
import { keys } from '@/lib/queries';
import { CITIES, MERCHANTS, sampleOrder } from '@/lib/simulate';
import type { Order } from '@/lib/types';
import { Button } from './ui/Button';

interface FormState {
  merchantId: string;
  recipientName: string;
  phone: string;
  addressLine: string;
  city: string;
  codAmount: string;
}

const empty: FormState = { merchantId: MERCHANTS[0].id, recipientName: '', phone: '', addressLine: '', city: 'Casablanca', codAmount: '' };

export type FieldErrors = Partial<Record<keyof FormState, string>>;

export function validateOrderForm(f: FormState, t: (k: 'create.required' | 'create.phoneInvalid' | 'create.amountInvalid') => string): FieldErrors {
  const errors: FieldErrors = {};
  if (!f.recipientName.trim()) errors.recipientName = t('create.required');
  if (!f.addressLine.trim()) errors.addressLine = t('create.required');
  if (!normalizeMoroccanPhone(f.phone)) errors.phone = t('create.phoneInvalid');
  const amount = Number(f.codAmount.replace(',', '.'));
  if (f.codAmount.trim() === '' || Number.isNaN(amount) || amount < 0 || amount > 50_000) errors.codAmount = t('create.amountInvalid');
  return errors;
}

function Field({ label, error, hint, children, htmlFor }: { label: string; error?: string; hint?: string; children: ReactNode; htmlFor: string }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={htmlFor} className="text-[12.5px] font-medium text-fg">{label}</label>
      {children}
      {error ? <p role="alert" className="text-[12px] text-rose-600 dark:text-rose-400">{error}</p>
        : hint ? <p className="text-[12px] text-faint">{hint}</p> : null}
    </div>
  );
}

const inputClass = (error?: string) => clsx(
  'focus-ring h-9 w-full rounded-lg border bg-panel px-3 text-sm text-fg placeholder:text-faint transition-colors',
  error ? 'border-rose-400 dark:border-rose-500/60' : 'border-line hover:border-faint/60',
);

export function CreateOrderDialog({ open, onOpenChange, onCreated }: {
  open: boolean; onOpenChange: (open: boolean) => void; onCreated?: (order: Order) => void;
}) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [form, setForm] = useState<FormState>(empty);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitting, setSubmitting] = useState(false);

  const set = (k: keyof FormState) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const fillSample = () => {
    const s = sampleOrder();
    setForm({ merchantId: s.merchantId, recipientName: s.recipientName, phone: `0${s.recipientPhone.slice(4)}`,
      addressLine: s.addressLine, city: s.city, codAmount: s.codAmount.toFixed(2) });
    setErrors({});
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found = validateOrderForm(form, t);
    setErrors(found);
    if (Object.keys(found).length) return;
    setSubmitting(true);
    try {
      const order = await api.createOrder({
        merchantId: form.merchantId, recipientName: form.recipientName.trim(),
        recipientPhone: normalizeMoroccanPhone(form.phone)!, addressLine: form.addressLine.trim(), city: form.city,
        codAmount: Number(form.codAmount.replace(',', '.')),
      });
      toast.success(t('create.created', { code: order.trackingCode }));
      qc.invalidateQueries({ queryKey: keys.orders });
      qc.invalidateQueries({ queryKey: keys.stats });
      setForm(empty);
      onOpenChange(false);
      onCreated?.(order);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length) {
        const map: FieldErrors = {};
        for (const [k, v] of Object.entries(err.fieldErrors)) map[(k === 'recipientPhone' ? 'phone' : k) as keyof FormState] = v;
        setErrors(map);
      } else {
        toast.error(err instanceof Error ? err.message : t('error.generic'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/30 backdrop-blur-[2px] data-[state=open]:animate-in" />
        <Dialog.Content
          className="fixed left-1/2 top-[8vh] z-50 w-[calc(100vw-32px)] max-w-lg -translate-x-1/2 rounded-2xl border border-line bg-panel shadow-lift focus:outline-none"
          aria-describedby="create-desc"
        >
          <form onSubmit={submit} noValidate data-testid="create-order-form">
            <div className="flex items-start justify-between gap-4 border-b border-line px-5 py-4">
              <div>
                <Dialog.Title className="text-[15px] font-semibold">{t('create.title')}</Dialog.Title>
                <Dialog.Description id="create-desc" className="mt-0.5 text-[12.5px] text-muted">{t('create.subtitle')}</Dialog.Description>
              </div>
              <Dialog.Close className="focus-ring rounded-md p-1 text-faint hover:bg-subtle hover:text-fg" aria-label={t('create.cancel')}>
                <X className="h-4 w-4" />
              </Dialog.Close>
            </div>

            <div className="grid grid-cols-1 gap-4 px-5 py-5 sm:grid-cols-2">
              <Field label={t('create.merchant')} htmlFor="merchant">
                <select id="merchant" className={inputClass()} value={form.merchantId} onChange={set('merchantId')}>
                  {MERCHANTS.map((m) => <option key={m.id} value={m.id}>{m.name} · {m.id}</option>)}
                </select>
              </Field>
              <Field label={t('create.city')} htmlFor="city">
                <select id="city" className={inputClass()} value={form.city} onChange={set('city')}>
                  {CITIES.map((c) => <option key={c}>{c}</option>)}
                </select>
              </Field>
              <Field label={t('create.recipient')} htmlFor="recipient" error={errors.recipientName}>
                <input id="recipient" className={inputClass(errors.recipientName)} value={form.recipientName} onChange={set('recipientName')}
                  placeholder="Salma Idrissi" autoComplete="off" />
              </Field>
              <Field label={t('create.phone')} htmlFor="phone" error={errors.phone} hint={t('create.phoneHint')}>
                <div className="relative">
                  <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-sm text-faint">🇲🇦</span>
                  <input id="phone" inputMode="tel" className={clsx(inputClass(errors.phone), 'pl-9 font-mono text-[13px]')} value={form.phone}
                    onChange={set('phone')} placeholder="06 12 34 56 78" autoComplete="off" />
                </div>
              </Field>
              <div className="sm:col-span-2">
                <Field label={t('create.address')} htmlFor="address" error={errors.addressLine}>
                  <input id="address" className={inputClass(errors.addressLine)} value={form.addressLine} onChange={set('addressLine')}
                    placeholder="12 Rue Atlas, Maarif" autoComplete="off" />
                </Field>
              </div>
              <Field label={t('create.amount')} htmlFor="amount" error={errors.codAmount}>
                <div className="relative">
                  <input id="amount" inputMode="decimal" className={clsx(inputClass(errors.codAmount), 'pr-14 tabular-nums')} value={form.codAmount}
                    onChange={set('codAmount')} placeholder="249.90" />
                  <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-[12px] font-medium text-faint">MAD</span>
                </div>
              </Field>
            </div>

            <div className="flex items-center justify-between gap-2 rounded-b-2xl border-t border-line bg-subtle/50 px-5 py-3">
              <Button type="button" variant="ghost" size="sm" onClick={fillSample} data-testid="fill-sample">
                <Sparkles className="h-3.5 w-3.5" />{t('create.sample')}
              </Button>
              <div className="flex gap-2">
                <Dialog.Close asChild><Button type="button" size="sm">{t('create.cancel')}</Button></Dialog.Close>
                <Button type="submit" variant="primary" size="sm" loading={submitting} data-testid="submit-order">{t('create.submit')}</Button>
              </div>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
