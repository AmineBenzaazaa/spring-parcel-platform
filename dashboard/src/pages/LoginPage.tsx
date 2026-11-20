import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { ArrowRight, Languages, ShieldCheck } from 'lucide-react';
import { ApiError } from '@/lib/api';
import { NotOpsError, useAuth } from '@/lib/auth';
import { useI18n } from '@/lib/i18n';
import { Logo } from '@/components/Logo';
import { Button } from '@/components/ui/Button';

const DEMO_USERS = [
  { username: 'ops', role: 'OPS' },
  { username: 'merchant.atlas', role: 'MERCHANT' },
  { username: 'courier.casa1', role: 'COURIER' },
];

export function LoginPage() {
  const { session, login } = useAuth();
  const { t, lang, setLang } = useI18n();
  const navigate = useNavigate();
  const [username, setUsername] = useState('ops');
  const [password, setPassword] = useState('');
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

  return (
    <div className="relative flex min-h-full items-center justify-center overflow-hidden px-4 py-12">
      <div aria-hidden className="pointer-events-none absolute inset-0 [background-image:radial-gradient(rgb(var(--line))_1px,transparent_1px)] [background-size:22px_22px] [mask-image:radial-gradient(ellipse_at_center,black_30%,transparent_75%)]" />
      <div aria-hidden className="pointer-events-none absolute left-1/2 top-[18%] h-72 w-72 -translate-x-1/2 rounded-full bg-accent/20 blur-3xl" />
      <button onClick={() => setLang(lang === 'en' ? 'fr' : 'en')}
        className="focus-ring absolute right-4 top-4 inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[12px] font-medium text-muted hover:bg-subtle hover:text-fg">
        <Languages className="h-3.5 w-3.5" />{t('lang.switch')}
      </button>

      <div className="relative w-full max-w-[380px]">
        <div className="mb-6 flex flex-col items-center text-center">
          <Logo size={40} />
          <h1 className="mt-4 text-xl font-semibold tracking-tight">{t('login.title')}</h1>
          <p className="mt-1 text-[13.5px] text-muted">{t('login.subtitle')}</p>
        </div>

        <form onSubmit={submit} className="panel space-y-4 p-6" data-testid="login-form">
          <div className="space-y-1.5">
            <label htmlFor="username" className="text-[12.5px] font-medium">{t('login.username')}</label>
            <input id="username" className={input} value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="password" className="text-[12.5px] font-medium">{t('login.password')}</label>
            <input id="password" type="password" className={input} value={password} onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password" placeholder="••••••••" />
          </div>
          {error && <p role="alert" className="rounded-md bg-rose-500/10 px-3 py-2 text-[12.5px] text-rose-700 dark:text-rose-300">{error}</p>}
          <Button type="submit" variant="primary" className="w-full" loading={loading}>
            {t('login.submit')}{!loading && <ArrowRight className="h-4 w-4" />}
          </Button>
        </form>

        <div className="mt-5 rounded-xl border border-dashed border-line p-4">
          <p className="label mb-2.5 flex items-center gap-1.5"><ShieldCheck className="h-3.5 w-3.5" />{t('login.demo')}</p>
          <div className="flex flex-wrap gap-1.5">
            {DEMO_USERS.map((u) => (
              <button key={u.username} type="button" onClick={() => { setUsername(u.username); setPassword('Passw0rd!'); }}
                className="focus-ring inline-flex items-center gap-1.5 rounded-md border border-line bg-panel px-2 py-1 text-[12px] hover:bg-subtle">
                <span className="font-mono">{u.username}</span>
                <span className="text-[10px] font-semibold text-faint">{u.role}</span>
              </button>
            ))}
          </div>
          <p className="mt-2.5 text-[12px] text-faint">{t('login.demoHint')}</p>
        </div>
      </div>
    </div>
  );
}
