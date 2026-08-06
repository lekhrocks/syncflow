import axios from 'axios';
import type {
  ConnectionResponse, CreateConnectionRequest, TestConnectionRequest,
  TestConnectionResponse, ConnectionHealthResponse, MetadataResponse,
  SchemaMetadata, ColumnMetadata, IndexMetadata, ConstraintMetadata,
  DashboardOverview, AuditEvent, AlertEvent, SystemDiagnostics,
  UserResponse, CreateUserRequest, UpdateUserRequest, LoginResponse,
} from '../types/api';

const api = axios.create({ baseURL: '/api' });

let authToken: string | null = null;

/** Set/clear the bearer token attached to all /api requests. */
export function setAuthToken(token: string | null) {
  authToken = token;
}

/** Default tenant sent on authenticated requests (single-tenant default). */
const TENANT_HEADER = 'X-Tenant-Id';

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`;
    // Authenticated requests must identify their tenant; use the single-tenant
    // default unless overridden per request.
    config.headers[TENANT_HEADER] = config.headers[TENANT_HEADER] ?? 'default';
  }
  return config;
});

// --- Connections ---
export const connectionApi = {
  list: () => api.get<ConnectionResponse[]>('/connections').then(r => r.data),
  get: (id: string) => api.get<ConnectionResponse>(`/connections/${id}`).then(r => r.data),
  create: (req: CreateConnectionRequest) => api.post<ConnectionResponse>('/connections', req).then(r => r.data),
  update: (id: string, req: Partial<CreateConnectionRequest>) => api.put<ConnectionResponse>(`/connections/${id}`, req).then(r => r.data),
  delete: (id: string) => api.delete(`/connections/${id}`),
  test: (req: TestConnectionRequest) => api.post<TestConnectionResponse>('/connections/test', req).then(r => r.data),
  health: (id: string) => api.get<ConnectionHealthResponse>(`/connections/${id}/health`).then(r => r.data),
};

// --- Metadata ---
export const metadataApi = {
  schemas: (id: string) => api.get<MetadataResponse<SchemaMetadata>>(`/connections/${id}/metadata`).then(r => r.data),
  tables: (id: string, schema: string) => api.get<MetadataResponse<SchemaMetadata>>(`/connections/${id}/schemas/${schema}/tables`).then(r => r.data),
  columns: (id: string, schema: string, table: string) =>
    api.get<MetadataResponse<ColumnMetadata>>(`/connections/${id}/schemas/${schema}/tables/${table}/columns`).then(r => r.data),
  indexes: (id: string, schema: string, table: string) =>
    api.get<MetadataResponse<IndexMetadata>>(`/connections/${id}/schemas/${schema}/tables/${table}/indexes`).then(r => r.data),
  constraints: (id: string, schema: string, table: string) =>
    api.get<MetadataResponse<ConstraintMetadata>>(`/connections/${id}/schemas/${schema}/tables/${table}/constraints`).then(r => r.data),
  refresh: (id: string) => api.post(`/connections/${id}/metadata/refresh`),
};

// --- Dashboard ---
export const dashboardApi = {
  overview: () => api.get<DashboardOverview>('/dashboard/overview').then(r => r.data),
};

// --- Audit ---
export const auditApi = {
  list: (entityType?: string, entityId?: string) =>
    api.get<AuditEvent[]>('/audit', { params: { entityType, entityId } }).then(r => r.data),
};

// --- Alerts ---
export const alertApi = {
  list: () => api.get<AlertEvent[]>('/alerts').then(r => r.data),
  acknowledge: (id: string) => api.post(`/alerts/${id}/acknowledge`),
};

// --- Diagnostics ---
export const diagnosticsApi = {
  system: () => api.get<SystemDiagnostics>('/diagnostics/system').then(r => r.data),
};

// --- Auth ---
export const authApi = {
  login: (username: string, password: string) =>
    api.post<LoginResponse>('/auth/login', { username, password }).then(r => r.data),
  me: () => api.get<UserResponse>('/auth/me').then(r => r.data),
  changePassword: (newPassword: string) =>
    api.post('/auth/change-password', { newPassword }).then(r => r.data),
};

// --- Users ---
export const userApi = {
  list: () => api.get<UserResponse[]>('/users').then(r => r.data),
  get: (id: string) => api.get<UserResponse>(`/users/${id}`).then(r => r.data),
  create: (req: CreateUserRequest) => api.post<UserResponse>('/users', req).then(r => r.data),
  update: (id: string, req: UpdateUserRequest) => api.put<UserResponse>(`/users/${id}`, req).then(r => r.data),
  setRoles: (id: string, roles: string) => api.post<UserResponse>(`/users/${id}/roles`, { roles }).then(r => r.data),
  delete: (id: string) => api.delete(`/users/${id}`),
};

export default api;
