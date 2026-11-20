import * as Menu from '@radix-ui/react-dropdown-menu';
import { ChevronDown, CircleCheck, PlayCircle, Undo2, Zap } from 'lucide-react';
import { useI18n } from '@/lib/i18n';
import type { useSimulation } from '@/lib/useSimulation';
import { Button } from './ui/Button';

function Item({ icon, title, hint, onSelect, testId }: { icon: React.ReactNode; title: string; hint?: string; onSelect: () => void; testId: string }) {
  return (
    <Menu.Item onSelect={onSelect} data-testid={testId}
      className="flex cursor-default select-none gap-2.5 rounded-md px-2.5 py-2 outline-none data-[highlighted]:bg-subtle">
      <span className="mt-0.5 text-muted">{icon}</span>
      <span>
        <span className="block text-[13px] font-medium text-fg">{title}</span>
        {hint && <span className="block text-[12px] text-muted">{hint}</span>}
      </span>
    </Menu.Item>
  );
}

export function SimulateMenu({ sim }: { sim: ReturnType<typeof useSimulation> }) {
  const { t } = useI18n();
  const busy = sim.running.size > 0;
  return (
    <Menu.Root>
      <Menu.Trigger asChild>
        <Button size="sm" loading={busy} data-testid="simulate-menu">
          {!busy && <Zap className="h-3.5 w-3.5" />}
          <span className="hidden sm:inline">{t('actions.simulate')}</span>
          <ChevronDown className="hidden h-3 w-3 text-faint sm:block" />
        </Button>
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Content align="end" sideOffset={6}
          className="z-50 w-[290px] rounded-xl border border-line bg-panel p-1 shadow-lift">
          <Item testId="sim-happy" icon={<PlayCircle className="h-4 w-4" />} title={t('actions.simHappy')} hint={t('actions.simHappyHint')}
            onSelect={() => sim.runNewOrder('delivered')} />
          <Item testId="sim-fail" icon={<Undo2 className="h-4 w-4" />} title={t('actions.simFail')} hint={t('actions.simFailHint')}
            onSelect={() => sim.runNewOrder('returned')} />
          <Menu.Separator className="my-1 h-px bg-line" />
          <Item testId="sim-next" icon={<CircleCheck className="h-4 w-4" />} title={t('actions.simNext')}
            onSelect={() => sim.deliverOldestAssigned()} />
        </Menu.Content>
      </Menu.Portal>
    </Menu.Root>
  );
}
