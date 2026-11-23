import { useState } from 'react';
import { Board } from '@/components/Board';
import { CouriersPanel } from '@/components/CouriersPanel';
import { CreateOrderDialog } from '@/components/CreateOrderDialog';
import { HealthStrip } from '@/components/HealthStrip';
import { KpiTiles } from '@/components/KpiTiles';
import { NotificationsFeed } from '@/components/NotificationsFeed';
import { OrderDrawer } from '@/components/OrderDrawer';
import { TopBar } from '@/components/TopBar';
import { useLiveStream } from '@/lib/live';
import { useSimulation } from '@/lib/useSimulation';
import { useI18n } from '@/lib/i18n';

export function OpsPage() {
  const { t } = useI18n();
  const live = useLiveStream(true);
  const sim = useSimulation();
  const [createOpen, setCreateOpen] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);

  return (
    <div className="min-h-full overflow-x-hidden">
      <TopBar live={live.status} sim={sim} onCreate={() => setCreateOpen(true)} />
      <div className="border-b border-line bg-panel/60">
        <div className="mx-auto max-w-[1680px] px-4 py-2 sm:px-6"><HealthStrip /></div>
      </div>
      <main className="mx-auto max-w-[1680px] space-y-4 px-4 py-6 sm:px-6">
        <p className="text-[13px] text-muted" data-testid="page-intro">{t('page.intro')}</p>
        <KpiTiles />
        <div className="grid grid-cols-1 gap-4 2xl:grid-cols-[minmax(0,1fr)_320px]">
          <Board onOpen={setSelected} onCreate={() => setCreateOpen(true)} />
          <aside className="grid min-h-0 grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-1 2xl:grid-rows-[auto_minmax(0,1fr)]">
            <CouriersPanel />
            <div className="min-h-[320px] max-h-[520px] 2xl:max-h-[calc(100vh-470px)] 2xl:min-h-[280px] [&>section]:h-full">
              <NotificationsFeed />
            </div>
          </aside>
        </div>
      </main>
      <CreateOrderDialog open={createOpen} onOpenChange={setCreateOpen} />
      <OrderDrawer orderId={selected} onClose={() => setSelected(null)} sim={sim} />
    </div>
  );
}
