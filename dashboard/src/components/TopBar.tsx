import clsx from 'clsx';
import { useState } from 'react';
import { CircleHelp, Languages, LogOut, Moon, PackagePlus, Sun } from 'lucide-react';
import { HowItWorks } from './HowItWorks';
import { useAuth } from '@/lib/auth';
import { useI18n } from '@/lib/i18n';
import type { LiveStatus } from '@/lib/live';
import { useTheme } from '@/lib/theme';
import type { useSimulation } from '@/lib/useSimulation';
import { ProductSwitcher } from './ProductSwitcher';
import { Popover, menuItem } from './ui/Popover';
import { LiveIndicator } from './LiveIndicator';
import { SimulateMenu } from './SimulateMenu';
import { Button, IconButton } from './ui/Button';

export function TopBar({ live, sim, onCreate }: { live: LiveStatus; sim: ReturnType<typeof useSimulation>; onCreate: () => void }) {
  const { t, lang, setLang } = useI18n();
  const { session, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const [howOpen, setHowOpen] = useState(false);

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-bg/80 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-[1680px] items-center gap-2 px-4 sm:gap-3 sm:px-6">
        <ProductSwitcher />
        <span className="hidden h-5 w-px bg-line xl:block" aria-hidden />
        <span className="hidden whitespace-nowrap text-[13px] text-muted xl:inline">{t('app.tagline')}</span>
        <LiveIndicator status={live} />
        <div className="ml-auto flex items-center gap-1 sm:gap-1.5">
          <SimulateMenu sim={sim} />
          <Button size="sm" variant="primary" onClick={onCreate} data-testid="open-create-order">
            <PackagePlus className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{t('actions.createOrder')}</span>
          </Button>
          <span className="mx-1 hidden h-5 w-px bg-line sm:block" aria-hidden />
          <IconButton onClick={() => setLang(lang === 'en' ? 'fr' : 'en')} title={t('lang.switch')} aria-label={t('lang.switch')} data-testid="lang-toggle">
            <span className="flex items-center gap-1 text-[11px] font-semibold"><Languages className="h-3.5 w-3.5" />{lang.toUpperCase()}</span>
          </IconButton>
          <IconButton onClick={toggle} title={t('actions.theme')} aria-label={t('actions.theme')}>
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </IconButton>
          <UserMenu username={session?.username ?? ''} onLogout={logout} onHowItWorks={() => setHowOpen(true)} />
        </div>
      </div>
      <HowItWorks open={howOpen} onOpenChange={setHowOpen} />
    </header>
  );
}

function UserMenu({ username, onLogout, onHowItWorks }: { username: string; onLogout: () => void; onHowItWorks: () => void }) {
  const { t } = useI18n();
  const initials = username.replace(/[^a-z]/gi, '').slice(0, 2).toUpperCase();
  return (
    <Popover label={t('user.menu')} align="end" testId="user-menu"
      trigger={(open) => (
        <span className={clsx('flex items-center gap-2 rounded-full border border-line bg-panel py-0.5 pe-0.5 ps-0.5 transition-colors hover:bg-subtle sm:pe-3', open && 'bg-subtle')}>
          <span className="avatar h-7 w-7 text-[11px]">{initials}</span>
          <span className="hidden text-start leading-tight sm:block">
            <span className="block text-[12.5px] font-medium">{username}</span>
            <span className="block text-[10.5px] text-faint">{t('role.OPS')}</span>
          </span>
        </span>
      )}>
      {(close) => (
        <>
          <div className="border-b border-line px-2.5 pb-2.5 pt-1.5">
            <p className="truncate text-[13px] font-medium">{username}</p>
            <p className="truncate text-[12px] text-muted">{t('role.OPS')}</p>
          </div>
          <div className="pt-1.5">
            <button type="button" role="menuitem" className={menuItem} data-testid="how-it-works-open" onClick={() => { close(); onHowItWorks(); }}>
              <CircleHelp className="h-4 w-4 text-muted" aria-hidden />{t('help.howItWorks')}
            </button>
            <button type="button" role="menuitem" className={menuItem} data-testid="logout" onClick={() => { close(); onLogout(); }}>
              <LogOut className="h-4 w-4 text-muted" aria-hidden />{t('actions.logout')}
            </button>
          </div>
        </>
      )}
    </Popover>
  );
}
