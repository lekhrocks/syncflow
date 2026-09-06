# Deployment

## Docker Compose (Development)

```bash
# Minimal: PostgreSQL only
docker compose -f docker/docker-compose.yml up -d postgres

# Full stack with all services
docker compose -f docker/docker-compose.yml up --build
```

### Services

| Service | Port | Always | Profile |
|---------|------|--------|---------|
| PostgreSQL | 5432 | Yes | — |
| App | 8080 | Yes | — |
| MySQL | 3306 | No | `mysql` |
| MongoDB | 27017 | No | `mongodb` |
| Redis | 6379 | No | `redis` |
| Kafka | 9092 | No | `kafka` |
| Prometheus | 9090 | No | `monitoring` |
| Grafana | 3000 | No | `monitoring` |

## Kubernetes (Kustomize)

Base manifests in `k8s/base/`:

```bash
# Apply base configuration
kubectl apply -k k8s/base/

# Or with overlays
kubectl apply -k k8s/overlays/production/
```

### Resources

| Resource | Purpose |
|----------|---------|
| `deployment.yaml` | Application pods |
| `service.yaml` | ClusterIP service |
| `ingress.yaml` | NGINX ingress |
| `configmap.yaml` | Non-sensitive configuration |
| `externalsecret.yaml` | External Secrets for sensitive values |
| `hpa.yaml` | Horizontal Pod Autoscaler |
| `keda-scaledobject.yaml` | KEDA event-driven autoscaling |
| `pdb.yaml` | Pod Disruption Budget |
| `networkpolicy.yaml` | Network policies |
| `prometheusrule.yaml` | Prometheus alerting rules |

## Helm

```bash
helm install syncflow helm/syncflow/ \
  --set image.tag=latest \
  --set postgres.host=your-pg-host
```

## Terraform

Infrastructure provisioning in `terraform/`:

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

## ArgoCD

```bash
kubectl apply -f argocd/application.yaml
```

## Multi-Region

Single-primary, multi-replica architecture:

- **RTO:** 1-2 minutes (automatic failover)
- **RPO:** < 30 seconds (continuous logical replication)
- **Failover:** `RegionalFailoverManager` with Postgres advisory locks
- **DNS:** Route53 failover routing

See `docs/deployment/F17_MULTI_REGION_DEPLOYMENT.md` for details.

## Production Checklist

- [ ] Set `SYNCFLOW_ENCRYPTION_KEY` (AES-256)
- [ ] Set `SYNCFLOW_JWT_SECRET` (HMAC, >= 32 bytes)
- [ ] Change default admin password
- [ ] Enable TLS termination at ingress
- [ ] Configure `syncflow.region.replication-enabled` for multi-region
- [ ] Set appropriate HikariCP pool sizes
- [ ] Configure Prometheus scraping
- [ ] Set up Grafana dashboards
- [ ] Review NetworkPolicies
- [ ] Configure PodDisruptionBudget
