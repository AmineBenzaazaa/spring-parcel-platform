import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nProvider } from '@/lib/i18n';
import type { Order } from '@/lib/types';
import { OrderCard } from './OrderCard';

const order: Order = {
  id: 'o1', trackingCode: 'PX-7K2M9QXA', merchantId: 'M-1001', recipientName: 'Salma Idrissi',
  recipientPhone: '+212612345678', addressLine: '12 Rue Atlas', city: 'Casablanca', codAmount: 1249.5,
  status: 'DELIVERY_FAILED', deliveryAttempts: 2, cancelReason: null, createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('OrderCard', () => {
  it('shows tracking code, recipient, city, COD in MAD, courier and attempts', async () => {
    const onOpen = vi.fn();
    render(<I18nProvider initial="en"><OrderCard order={order} courierName="Youssef El Amrani" attempts={2} maxAttempts={3} onOpen={onOpen} /></I18nProvider>);

    expect(screen.getByText('PX-7K2M9QXA')).toBeInTheDocument();
    expect(screen.getByText('Salma Idrissi')).toBeInTheDocument();
    expect(screen.getByText('Casablanca')).toBeInTheDocument();
    expect(screen.getByText('1,249.50 MAD')).toBeInTheDocument();
    expect(screen.getByText('Youssef El Amrani')).toBeInTheDocument();
    expect(screen.getByText('Attempt 2/3')).toBeInTheDocument(); // screen-reader label
    expect(screen.getByText('2/3')).toBeInTheDocument();

    await userEvent.click(screen.getByTestId('order-card'));
    expect(onOpen).toHaveBeenCalledWith('o1');
  });

  it('speaks French: unassigned label, attempts and Moroccan number format', () => {
    render(<I18nProvider initial="fr"><OrderCard order={order} courierName={null} attempts={1} maxAttempts={3} onOpen={() => {}} /></I18nProvider>);

    expect(screen.getByText('Non assignée')).toBeInTheDocument();
    expect(screen.getByText('Tentative 1/3')).toBeInTheDocument();
    expect(screen.getByText(/1[.\s\u202f]249,50 MAD/)).toBeInTheDocument(); // CLDR fr-MA groups with a dot
  });
});
