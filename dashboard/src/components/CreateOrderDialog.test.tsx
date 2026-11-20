import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nProvider, translate } from '@/lib/i18n';
import { CreateOrderDialog, validateOrderForm } from './CreateOrderDialog';

const t = (k: Parameters<typeof translate>[1]) => translate('en', k);

describe('validateOrderForm', () => {
  const valid = { merchantId: 'M-1001', recipientName: 'Salma', phone: '06 12 34 56 78', addressLine: '12 Rue Atlas', city: 'Rabat', codAmount: '249,90' };

  it('accepts a local Moroccan number and a comma decimal', () => {
    expect(validateOrderForm(valid, t)).toEqual({});
  });

  it('flags a non-Moroccan phone and an out-of-range amount', () => {
    const errors = validateOrderForm({ ...valid, phone: '+33 6 12 34 56 78', codAmount: '60000' }, t);
    expect(errors.phone).toMatch(/Moroccan number/);
    expect(errors.codAmount).toMatch(/50,000/);
  });
});

describe('CreateOrderDialog', () => {
  it('does not call the API when the form is invalid', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    render(
      <QueryClientProvider client={new QueryClient()}>
        <I18nProvider initial="en"><CreateOrderDialog open onOpenChange={() => {}} /></I18nProvider>
      </QueryClientProvider>,
    );
    await userEvent.type(screen.getByLabelText('Phone'), '0812');
    await userEvent.click(screen.getByTestId('submit-order'));

    expect(await screen.findByText('Enter a Moroccan number like 06 12 34 56 78')).toBeInTheDocument();
    expect(screen.getAllByText('Required')).toHaveLength(2);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
