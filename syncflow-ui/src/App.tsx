import { Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { DashboardPage } from './pages/DashboardPage';
import { ConnectionsPage } from './pages/ConnectionsPage';
import { ConnectionDetailPage } from './pages/ConnectionDetailPage';
import { MetadataPage } from './pages/MetadataPage';
import { PipelinesPage } from './pages/PipelinesPage';
import { PipelineDesignPage } from './pages/PipelineDesignPage';
import { ExecutionPage } from './pages/ExecutionPage';
import { MonitoringPage } from './pages/MonitoringPage';
import { AuditPage } from './pages/AuditPage';
import { DiagnosticsPage } from './pages/DiagnosticsPage';
import { AdminPage } from './pages/admin/AdminPage';
import { AgentFleetPage } from './pages/admin/AgentFleetPage';
import { MarketplacePage } from './pages/marketplace/MarketplacePage';
import { WorkflowPage } from './pages/workflow/WorkflowPage';
import { AnimatePresence } from 'framer-motion';

export default function App() {
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
          <Route path="/diagnostics" element={<DiagnosticsPage />} />
          <Route path="/agents" element={<AgentFleetPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/marketplace" element={<MarketplacePage />} />
          <Route path="/workflows" element={<WorkflowPage />} />
        </Route>
      </Routes>
    </AnimatePresence>
  );
}
