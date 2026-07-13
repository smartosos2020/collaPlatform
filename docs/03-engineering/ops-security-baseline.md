---
title: Ops Security Baseline
status: active
---

# Ops Security Baseline

## Runtime

- Spring Actuator exposes `health`, `info`, `metrics`, and `prometheus`.
- Production Nginx exposes `/actuator/health`; Prometheus is available only by direct backend/internal access for ops checks.
- `RequestLoggingFilter` logs request id, method, path, status, duration, client, and username while avoiding request bodies, tokens, and permission lists.
- Logs are JSON-shaped and split into app/error rolling files under `LOG_PATH`.
- Hibernate slow query logging starts at 500 ms.

## Security

- CORS origins are configured through `CORS_ALLOWED_ORIGINS`.
- Password policy defaults to 8+ characters with at least one letter and one digit.
- Refresh tokens rotate on refresh because the old session is revoked before issuing a new one.
- Upload size is bounded by `MAX_UPLOAD_SIZE_BYTES` and reverse proxy `client_max_body_size`.
- Local `scripts/security-audit-gate.ps1` is part of the AI quality gate and checks test isolation, production secret externalization, authenticated route defaults, admin-only audit queries, and key service audit calls. It is local governance tooling, not a remote CI dependency.

## Audit Coverage

- Login success/failure.
- User create/enable/disable/password reset.
- File download URL creation.
- Document/base permission grant.
- Approval start/approve/reject/transfer/withdraw.

## Operations

- Production compose lives in `deploy/docker-compose.prod.yml`.
- Reverse proxy and WebSocket forwarding live in `deploy/nginx/colla.conf`.
- Backup and restore scripts live in `deploy/scripts`.
- 旧性能基线脚本已归档；新的性能验收必须基于当前规范 API 建立任务级基线。
- `deploy/scripts/restore-drill.ps1` validates backup manifests and hashes in dry-run mode by default.
