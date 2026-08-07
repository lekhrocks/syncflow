import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router';
import { Center, Loader } from '@mantine/core';
import type { ReactNode } from 'react';
import { AppLayout } from './components/layout/AppLayout';
import { ErrorBoundary } from './components/ErrorBoundary';
import { LoginPage } from './pages/LoginPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { useAuth } from './auth/AuthContext';
import { AnimatePresence } from 'framer-motion';

// Route-level code splitting: each page is its own chunk, loaded on demand.
// Login/ChangePassword stay eager (small, needed before auth resolves).
// Pages export named components, so resolve the named export for lazy().
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(m => ({ default: m.DashboardPage })));
const ConnectionsPage = lazy(() => import('./pages/ConnectionsPage').then(m => ({ default: m.ConnectionsPage })));
const ConnectionDetailPage = lazy(() => import('./pages/ConnectionDetailPage').then(m => ({ default: m.ConnectionDetailPage })));
const MetadataPage = lazy(() => import('./pages/MetadataPage').then(m => ({ default: m.MetadataPage })));
const PipelinesPage = lazy(() => import('./pages/PipelinesPage').then(m => ({ default: m.PipelinesPage })));
const PipelineDesignPage = lazy(() => import('./pages/PipelineDesignPage').then(m => ({ default: m.PipelineDesignPage })));
const ExecutionPage = lazy(() => import('./pages/ExecutionPage').then(m => ({ default: m.ExecutionPage })));
const MonitoringPage = lazy(() => import('./pages/MonitoringPage').then(m => ({ default: m.MonitoringPage })));
const AuditPage = lazy(() => import('./pages/AuditPage').then(m => ({ default: m.AuditPage })));
const UsersPage = lazy(() => import('./pages/UsersPage').then(m => ({ default: m.UsersPage })));
const DiagnosticsPage = lazy(() => import('./pages/DiagnosticsPage').then(m => ({ default: m.DiagnosticsPage })));
const AdminPage = lazy(() => import('./pages/admin/AdminPage').then(m => ({ default: m.AdminPage })));
const AgentFleetPage = lazy(() => import('./pages/admin/AgentFleetPage').then(m => ({ default: m.AgentFleetPage })));
const MarketplacePage = lazy(() => import('./pages/marketplace/MarketplacePage').then(m => ({ default: m.MarketplacePage })));
const WorkflowPage = lazy(() => import('./pages/workflow/WorkflowPage').then(m => ({ default: m.WorkflowPage })));
const DlqPage = lazy(() => import('./pages/DlqPage').then(m => ({ default: m.DlqPage })));

/** Wraps admin-only routes; non-admins are redirected to the dashboard. */
function AdminRoute({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth();
  return isAdmin ? children : <Navigate to="/dashboard" replace />;
}

/** Suspense boundary so lazy routes render a loader while their chunk loads. */
function Page({ children }: { children: ReactNode }) {
  return <Suspense fallback={<Center h="100vh"><Loader /></Center>}>{children}</Suspense>;
}

export default function App() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <Center h="100vh">
        <Loader />
      </Center>
    );
  }

  if (!user) {
    return <LoginPage />;
  }

  if (user.mustChangePassword) {
    return <ChangePasswordPage />;
  }

  return (
    <ErrorBoundary>
      <AnimatePresence mode="wait">
        <Routes>
          <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Page><DashboardPage /></Page>} />
          <Route path="/connections" element={<Page><ConnectionsPage /></Page>} />
          <Route path="/connections/:id" element={<Page><ConnectionDetailPage /></Page>} />
          <Route path="/connections/:id/metadata" element={<Page><MetadataPage /></Page>} />
          <Route path="/pipelines" element={<Page><PipelinesPage /></Page>} />
          <Route path="/pipelines/:id" element={<Page><PipelineDesignPage /></Page>} />
          <Route path="/pipelines/:id/edit" element={<Page><PipelineDesignPage edit /></Page>} />
          <Route path="/execution" element={<Page><ExecutionPage /></Page>} />
          <Route path="/monitoring" element={<Page><MonitoringPage /></Page>} />
          <Route path="/audit" element={<Page><AuditPage /></Page>} />
          <Route path="/users" element={<AdminRoute><Page><UsersPage /></Page></AdminRoute>} />
          <Route path="/diagnostics" element={<Page><DiagnosticsPage /></Page>} />
          <Route path="/agents" element={<AdminRoute><Page><AgentFleetPage /></Page></AdminRoute>} />
          <Route path="/admin" element={<AdminRoute><Page><AdminPage /></Page></AdminRoute>} />
          <Route path="/marketplace" element={<Page><MarketplacePage /></Page>} />
          <Route path="/workflows" element={<Page><WorkflowPage /></Page>} />
          <Route path="/dlq" element={<Page><DlqPage /></Page>} />
          </Route>
        </Routes>
      </AnimatePresence>
    </ErrorBoundary>
  );
}
