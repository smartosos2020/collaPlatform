# Deploy

Single-node production deployment is based on Docker Compose:

```powershell
Copy-Item deploy/.env.prod.example deploy/.env.prod
docker compose --env-file deploy/.env.prod -f deploy/docker-compose.prod.yml up -d --build
```

Required setup:

- Replace every secret in `deploy/.env.prod`.
- Put TLS certs under `deploy/certs` and extend `deploy/nginx/colla.conf` for `listen 443 ssl`.
- Set `CORS_ALLOWED_ORIGINS` to the public web origin.

Backup:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/backup.ps1
```

Restore:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/scripts/restore.ps1 -BackupPath .local-backups/<timestamp>
```

Current targets:

- Single-node Docker Compose deployment.
- Nginx reverse proxy with API and WebSocket forwarding.
- PostgreSQL and MinIO backup/restore drill scripts.
- Future Kubernetes manifests after MVP traffic patterns are known.
