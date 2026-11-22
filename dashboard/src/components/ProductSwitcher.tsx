import { Bot, Check, ChevronsUpDown, Route, Wallet } from 'lucide-react';
import clsx from 'clsx';
import { BRAND, CURRENT_PRODUCT, PRODUCTS, type ProductId } from '@/brand/brand';
import { useI18n, type MessageKey } from '@/lib/i18n';
import { Logo } from './Logo';
import { Popover, menuItem } from './ui/Popover';

const ICONS: Record<ProductId, typeof Route> = { dispatch: Route, settle: Wallet, copilot: Bot };

/** Brand lockup that opens the suite switcher: Dispatch, Settle, Copilot. */
export function ProductSwitcher() {
  const { t } = useI18n();
  const current = PRODUCTS.find((p) => p.id === CURRENT_PRODUCT)!;
  return (
    <Popover label={t('suite.switch')} testId="product-switcher"
      trigger={(open) => (
        <span className={clsx('flex items-center gap-2 rounded-lg py-1 pe-1.5 ps-1 transition-colors hover:bg-subtle', open && 'bg-subtle')}>
          <Logo size={26} />
          <span className="whitespace-nowrap text-[15px] font-semibold tracking-tight">
            {BRAND.name} <span className="font-medium text-muted">{current.name}</span>
          </span>
          <ChevronsUpDown className="h-3.5 w-3.5 text-faint" aria-hidden />
        </span>
      )}>
      {(close) => (
        <>
          <p className="label px-2.5 pb-1.5 pt-1">{BRAND.suite}</p>
          {PRODUCTS.map((p) => {
            const Icon = ICONS[p.id];
            const active = p.id === CURRENT_PRODUCT;
            return (
              <a key={p.id} role="menuitem" href={active ? '/' : p.url} onClick={close} aria-current={active ? 'page' : undefined}
                className={menuItem} data-testid={`product-${p.id}`}>
                <span className={clsx('inline-flex h-8 w-8 items-center justify-center rounded-lg',
                  active ? 'bg-accent text-accent-fg' : 'bg-subtle text-muted')}>
                  <Icon className="h-4 w-4" aria-hidden />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block font-medium">{BRAND.name} {p.name}</span>
                  <span className="block truncate text-[12px] text-muted">{t(`suite.${p.id}` as MessageKey)}</span>
                </span>
                {active && <Check className="h-4 w-4 text-accent" aria-hidden />}
              </a>
            );
          })}
        </>
      )}
    </Popover>
  );
}
