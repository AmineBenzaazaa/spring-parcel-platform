import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ArrowRight, Check, Languages, ShieldCheck } from 'lucide-react';
import { BRAND, productTitle } from '@/brand/brand';
import { ApiError } from '@/lib/api';
import { NotOpsError, useAuth } from '@/lib/auth';
import { useI18n } from '@/lib/i18n';
import { Logo } from '@/components/Logo';
import { Button } from '@/components/ui/Button';

export const DEMO_PASSWORD = 'Passw0rd!';
export const DEMO_USERS = [
  { username: 'ops', role: 'OPS' },
  { username: 'merchant.atlas', role: 'MERCHANT' },
  { username: 'courier.casa1', role: 'COURIER' },
];

export function LoginPage() {
  const { session, login } = useAuth();
  const { t, lang, setLang } = useI18n();
  const navigate = useNavigate();
  const [username, setUsername] = useState('ops');
  // Prefilled with the working demo account, so the first click on "Sign in" succeeds.
  const [password, setPassword] = useState(DEMO_PASSWORD);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (session) return <Navigate to="/" replace />;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(username.trim(), password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof NotOpsError ? t('login.notOps')
        : err instanceof ApiError && (err.status === 401 || err.status === 400) ? t('login.invalid')
          : t('login.error'));
    } finally {
      setLoading(false);
    }
  };

  const input = 'focus-ring h-10 w-full rounded-lg border border-line bg-panel px-3 text-sm text-fg placeholder:text-faint hover:border-faint/60';
  const points = [t('login.point1'), t('login.point2'), t('login.point3')];

  return (
    <div className="grid min-h-full lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
      {/* brand panel */}
      <aside className="relative hidden overflow-hidden bg-accent text-accent-fg lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div aria-hidden className="pointer-events-none absolute inset-0 opacity-[0.12] [background-image:radial-gradient(currentColor_1px,transparent_1px)] [background-size:22px_22px]" />
        <div aria-hidden className="pointer-events-none absolute -bottom-40 -end-40 h-[28rem] w-[28rem] rounded-full bg-signal/25 blur-3xl" />
        <div className="relative flex items-center gap-2.5">
          <Logo size={34} onBrand />
          <span className="text-[17px] font-semibold tracking-tight">{BRAND.name} <span className="font-normal opacity-80">{BRAND.product}</span></span>
        </div>
        <div className="relative max-w-[460px]">
          <h2 className="text-[32px] font-semibold leading-[1.15] tracking-tight">{t('login.heroTitle')}</h2>
          <p className="mt-4 text-[15px] leading-relaxed opacity-85">{t('login.heroBody')}</p>
          <ul className="mt-7 space-y-2.5">
            {points.map((p) => (
              <li key={p} className="flex items-center gap-2.5 text-[14px]">
                <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-white/15"><Check className="h-3 w-3" aria-hidden /></span>{p}
              </li>
            ))}
          </ul>
        </div>
        <p className="relative text-[12px] opacity-70">{BRAND.suite} · Dispatch · Settle · Copilot</p>
      </aside>

      {/* form */}
      <main className="relative flex items-center justify-center px-4 py-12">
        <button onClick={() => setLang(lang === 'en' ? 'fr' : 'en')}
          className="focus-ring absolute end-4 top-4 inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[12px] font-medium text-muted hover:bg-subtle hover:text-fg">
          <Languages className="h-3.5 w-3.5" />{t('lang.switch')}
        </button>

        <div className="w-full max-w-[380px]">
          <div className="mb-7">
            <span className="lg:hidden"><Logo size={36} /></span>
            <h1 className="mt-4 text-[22px] font-semibold tracking-tight lg:mt-0">{t('login.title', { product: productTitle() })}</h1>
            <p className="mt-1.5 text-[13.5px] text-muted">{t('login.subtitle')}</p>
          </div>

          <form onSubmit={submit} className="space-y-4" data-testid="login-form">
            <div className="space-y-1.5">
              <label htmlFor="username" className="text-[12.5px] font-medium">{t('login.username')}</label>
              <input id="username" className={input} value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="password" className="text-[12.5px] font-medium">{t('login.password')}</label>
              <input id="password" type="password" className={input} value={password} onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password" placeholder="••••••••" />
            </div>
            {error && <p role="alert" className="rounded-md bg-danger/10 px-3 py-2 text-[12.5px] text-danger">{error}</p>}
            <Button type="submit" variant="primary" className="h-10 w-full" loading={loading}>
              {t('login.submit')}{!loading && <ArrowRight className="h-4 w-4" />}
            </Button>
          </form>

          <div className="mt-6 rounded-xl border border-line bg-subtle/60 p-4">
            <p className="label mb-2.5 flex items-center gap-1.5"><ShieldCheck className="h-3.5 w-3.5" />{t('login.demo')}</p>
            <div className="flex flex-wrap gap-1.5">
              {DEMO_USERS.map((u) => (
                <button key={u.username} type="button" data-testid={`demo-${u.username}`}
                  onClick={() => { setUsername(u.username); setPassword(DEMO_PASSWORD); setError(null); }}
                  className={`focus-ring inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-[12px] hover:bg-panel ${
                    username === u.username ? 'border-accent/60 bg-accent/[0.07]' : 'border-line bg-panel'}`}>
                  <span className="font-mono">{u.username}</span>
                  <span className="text-[10px] font-semibold text-faint">{u.role}</span>
                </button>
              ))}
            </div>
            <p className="mt-2.5 text-[12px] text-muted">{t('login.demoHint')}</p>
          </div>
        </div>
      </main>
    </div>
  );
}
