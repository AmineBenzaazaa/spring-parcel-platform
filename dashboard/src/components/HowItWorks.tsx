import * as Dialog from '@radix-ui/react-dialog';
import clsx from 'clsx';
import { Banknote, Bot, FileSpreadsheet, Gavel, PackagePlus, Scale, X } from 'lucide-react';
import { HOW_IT_WORKS, type ProductKey } from '@/brand/suite-content';
import { useI18n } from '@/lib/i18n';

const ICONS = [PackagePlus, Banknote, FileSpreadsheet, Scale, Gavel, Bot];
const BADGE: Record<ProductKey | 'courier', string> = {
  dispatch: 'bg-accent/10 text-accent',
  settle: 'bg-signal/15 text-signal-ink',
  copilot: 'bg-info/10 text-info',
  courier: 'bg-subtle text-muted',
};

/** "How it works": the order-to-cash flow across MSE Dispatch, Settle and Copilot, as a stepper. */
export function HowItWorks({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const { lang } = useI18n();
  const flow = HOW_IT_WORKS[lang];
  const label = (p: ProductKey | 'courier') => (p === 'courier' ? (lang === 'fr' ? 'Livreur' : 'Courier') : `MSE ${p[0].toUpperCase()}${p.slice(1)}`);
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" />
        <Dialog.Content data-testid="how-it-works"
          className="fixed left-1/2 top-1/2 z-50 max-h-[90dvh] w-[calc(100vw-2rem)] max-w-3xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl border border-line bg-panel p-6 shadow-lift focus:outline-none">
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <Dialog.Title className="text-lg font-semibold tracking-tight">{flow.title}</Dialog.Title>
              <Dialog.Description className="mt-1 text-sm text-muted">{flow.intro}</Dialog.Description>
            </div>
            <Dialog.Close className="focus-ring grid h-8 w-8 shrink-0 place-items-center rounded-lg text-muted hover:bg-subtle hover:text-fg" aria-label="Close">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <ol className="grid gap-3 sm:grid-cols-3">
            {flow.steps.map((s, i) => {
              const Icon = ICONS[i] ?? PackagePlus;
              return (
                <li key={s.title} className="relative rounded-xl border border-line bg-bg p-4">
                  <div className="mb-2 flex items-center gap-2">
                    <span className="grid h-7 w-7 place-items-center rounded-full bg-accent text-accent-fg"><Icon className="h-3.5 w-3.5" aria-hidden /></span>
                    <span className={clsx('rounded-full px-2 py-0.5 text-[10.5px] font-semibold uppercase tracking-wide', BADGE[s.product])}>{label(s.product)}</span>
                    <span className="ms-auto text-[11px] tabular-nums text-faint">{i + 1}/{flow.steps.length}</span>
                  </div>
                  <p className="text-[13.5px] font-semibold">{s.title}</p>
                  <p className="mt-1 text-[12.5px] leading-snug text-muted">{s.detail}</p>
                </li>
              );
            })}
          </ol>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
