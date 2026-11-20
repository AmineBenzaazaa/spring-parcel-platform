import { render, screen, within } from '@testing-library/react';
import { I18nProvider } from '@/lib/i18n';
import type { OrderStats } from '@/lib/types';
import { KpiTilesView } from './KpiTiles';

const stats = (over: Partial<OrderStats> = {}): OrderStats => ({
  since: '2026-11-19T23:00:00Z', ordersToday: 12, deliveredToday: 4, codCollectedToday: 1999.6, inTransit: 5,
  failedOrReturnedToday: 1, deliveredRate: 80, byStatus: {} as OrderStats['byStatus'], ...over,
});

describe('KpiTilesView', () => {
  it('renders the five KPIs', () => {
    render(<I18nProvider initial="en"><KpiTilesView stats={stats()} /></I18nProvider>);
    expect(within(screen.getByTestId('kpi-orders')).getByText('12')).toBeInTheDocument();
    expect(within(screen.getByTestId('kpi-delivered')).getByText('80%')).toBeInTheDocument();
    expect(within(screen.getByTestId('kpi-cod')).getByText('1,999.60')).toBeInTheDocument();
    expect(within(screen.getByTestId('kpi-transit')).getByText('5')).toBeInTheDocument();
    expect(within(screen.getByTestId('kpi-failed')).getByText('1')).toBeInTheDocument();
  });

  it('shows a dash, not 0%, before any parcel is closed', () => {
    render(<I18nProvider initial="en"><KpiTilesView stats={stats({ deliveredRate: null })} /></I18nProvider>);
    expect(within(screen.getByTestId('kpi-delivered')).getByText('—')).toBeInTheDocument();
    expect(screen.getByText('No parcel closed yet')).toBeInTheDocument();
  });
});
