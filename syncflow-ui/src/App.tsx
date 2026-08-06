import { Routes, Route, Navigate } from 'react-router';
import { Center, Loader } from '@mantine/core';
import type { ReactNode } from 'react';
import { AppLayout } from './components/layout/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { useAuth } from './auth/AuthContext';
import { DashboardPage } from './pages/DashboardPage';
import { ConnectionsPage } from './pages/ConnectionsPage';
import { ConnectionDetailPage } from './pages/ConnectionDetailPage';
import { MetadataPage } from './pages/MetadataPage';
import { PipelinesPage } from './pages/PipelinesPage';
import { PipelineDesignPage } from './pages/PipelineDesignPage';
import { ExecutionPage } from './pages/ExecutionPage';
import { MonitoringPage } from './pages/MonitoringPage';
import { AuditPage } from './pages/AuditPage';
import { UsersPage } from './pages/UsersPage';
import { DiagnosticsPage } from './pages/DiagnosticsPage';
import { AdminPage } from './pages/admin/AdminPage';
import { AgentFleetPage } from './pages/admin/AgentFleetPage';
import { MarketplacePage } from './pages/marketplace/MarketplacePage';
import { WorkflowPage } from './pages/workflow/WorkflowPage';
import { AnimatePresence } from 'framer-motion';

/** Wraps admin-only routes; non-admins are redirected to the dashboard. */
function AdminRoute({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth();
  return isAdmin ? children : <Navigate to="/dashboard" replace />;
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
    <AnimatePresence mode="wait">
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/connections" element={<ConnectionsPage />} />
          <Route path="/connections/:id" element={<ConnectionDetailPage />} />
          <Route path="/connections/:id/metadata" element={<MetadataPage />} />
          <Route path="/pipelines" element={<PipelinesPage />} />
          <Route path="/pipelines/:id" element={<PipelineDesignPage />} />
          <Route path="/execution" element={<ExecutionPage />} />
          <Route path="/monitoring" element={<MonitoringPage />} />
          <Route path="/audit" element={<AuditPage />} />
          <Route path="/users" element={<AdminRoute><UsersPage /></AdminRoute>} />
          <Route path="/diagnostics" element={<DiagnosticsPage />} />
          <Route path="/agents" element={<AdminRoute><AgentFleetPage /></AdminRoute>} />
          <Route path="/admin" element={<AdminRoute><AdminPage /></AdminRoute>} />
          <Route path="/marketplace" element={<MarketplacePage />} />
          <Route path="/workflows" element={<WorkflowPage />} />
        </Route>
      </Routes>
    </AnimatePresence>
  );
}
