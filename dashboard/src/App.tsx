import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from '@/lib/auth';
import { LoginPage } from '@/pages/LoginPage';
import { OpsPage } from '@/pages/OpsPage';

function RequireOps({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  return session ? <>{children}</> : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<RequireOps><OpsPage /></RequireOps>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
