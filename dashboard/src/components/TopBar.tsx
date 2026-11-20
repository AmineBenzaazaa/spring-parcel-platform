import { Languages, LogOut, Moon, PackagePlus, Sun } from 'lucide-react';
import { useAuth } from '@/lib/auth';
import { useI18n } from '@/lib/i18n';
import type { LiveStatus } from '@/lib/live';
import { useTheme } from '@/lib/theme';
import type { useSimulation } from '@/lib/useSimulation';
import { Logo } from './Logo';
import { LiveIndicator } from './LiveIndicator';
import { SimulateMenu } from './SimulateMenu';
import { Button, IconButton } from './ui/Button';

export function TopBar({ live, sim, onCreate }: { live: LiveStatus; sim: ReturnType<typeof useSimulation>; onCreate: () => void }) {
  const { t, lang, setLang } = useI18n();
  const { session, logout } = useAuth();
  const { theme, toggle } = useTheme();

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-bg/80 backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-[1680px] items-center gap-2 px-4 sm:gap-3">
        <div className="flex items-center gap-2.5">
          <Logo />
          <span className="hidden whitespace-nowrap text-[14px] font-semibold tracking-tight sm:inline">{t('app.name')}</span>
          <span className="hidden text-[13px] text-faint lg:inline">/</span>
          <span className="hidden whitespace-nowrap text-[13px] text-muted lg:inline">{t('app.tagline')}</span>
        </div>
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
          <IconButton onClick={logout} title={`${t('actions.logout')} (${session?.username})`} aria-label={t('actions.logout')}>
            <LogOut className="h-4 w-4" />
          </IconButton>
        </div>
      </div>
    </header>
  );
}
