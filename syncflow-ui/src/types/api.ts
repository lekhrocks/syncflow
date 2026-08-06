export interface ConnectionResponse {
  id: string;
  name: string;
  connectionType: string;
  host: string;
  port: number;
  database: string;
  status: string;
  databaseVersion: string;
  driverName: string;
  lastLatencyMs: number;
  lastChecked: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConnectionRequest {
  name: string;
  connectionType: 'POSTGRESQL' | 'MYSQL' | 'MONGODB' | 'REDIS';
  host: string;
  port: number;
  database: string;
  username: string;
  password: string;
}

export interface TestConnectionRequest {
  connectionType: string;
  host: string;
  port: number;
  database: string;
  username: string;
  password: string;
}

export interface TestConnectionResponse {
  success: boolean;
  latencyMs: number;
  databaseVersion: string | null;
  driverName: string | null;
  errorMessage: string | null;
}

export interface ConnectionHealthResponse {
  status: string;
  latencyMs: number;
  databaseVersion: string;
  lastChecked: string;
}

export interface SchemaMetadata {
  name: string;
  tables: TableMetadata[];
}

export interface TableMetadata {
  name: string;
  type: string;
  schema: string;
  statistics: TableStatistics;
}

export interface TableStatistics {
  rowCountEstimate: number;
  totalSizeBytes: number;
}

export interface ColumnMetadata {
  name: string;
  ordinalPosition: number;
  dataType: DataType;
  primaryKey: boolean;
  foreignKey: boolean;
}

export interface DataType {
  jdbcType: string;
  nativeType: string;
  columnSize: number | null;
  nullable: boolean;
}

export interface IndexMetadata {
  name: string;
  columnNames: string[];
  unique: boolean;
  indexType: string;
}

export interface ConstraintMetadata {
  name: string;
  type: string;
  definition: string;
}

export interface MetadataResponse<T> {
  connectionId: string;
  type: string;
  data: T[];
  totalCount: number;
  discoveryTimeMs: number;
  cached: boolean;
  error: string | null;
}

export interface DashboardOverview {
  pipelines: { total: number; draft: number; validated: number };
  connections: { total: number; postgresql: number; mysql: number; mongodb: number; redis: number };
  connectors: number;
  syncJobs: { total: number; running: number };
  snapshots: { total: number; running: number; completed: number; failed: number };
  alerts: number;
  dlq: number;
  auditEvents: number;
}

export interface AuditEvent {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
  correlationId: string;
  success: boolean;
  timestamp: string;
}

export interface AlertEvent {
  id: string;
  name: string;
  message: string;
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  source: string;
  timestamp: string;
  acknowledged: boolean;
}

export interface SystemDiagnostics {
  jvm: {
    availableProcessors: number;
    freeMemory: number;
    totalMemory: number;
    maxMemory: number;
    heapMemoryUsage: number;
    nonHeapMemoryUsage: number;
    threadCount: number;
    peakThreadCount: number;
    virtualThreadCount: number;
  };
  os: { name: string; version: string; arch: string };
  java: { version: string; vendor: string; vm: string };
}

// --- Auth & Users ---
export interface UserResponse {
  id: string;
  username: string;
  email: string;
  roles: string;
  enabled: boolean;
  mustChangePassword: boolean;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  email?: string;
  roles?: string;
}

export interface UpdateUserRequest {
  email?: string;
  roles?: string;
  enabled?: boolean;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  mustChangePassword: boolean;
}

