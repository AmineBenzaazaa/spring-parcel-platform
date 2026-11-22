import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '@/lib/i18n';
import { DEMO_PASSWORD, LoginPage } from './LoginPage';

const login = vi.fn(() => Promise.resolve());
vi.mock('@/lib/auth', () => ({
  useAuth: () => ({ session: null, login }),
  NotOpsError: class NotOpsError extends Error {},
}));

describe('LoginPage', () => {
  it('is prefilled with a working account, so the first sign-in succeeds', async () => {
    render(<MemoryRouter><I18nProvider initial="en"><LoginPage /></I18nProvider></MemoryRouter>);
    expect(screen.getByLabelText('Username')).toHaveValue('ops');
    expect(screen.getByLabelText('Password')).toHaveValue(DEMO_PASSWORD);

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(login).toHaveBeenCalledWith('ops', 'Passw0rd!');
  });
});
