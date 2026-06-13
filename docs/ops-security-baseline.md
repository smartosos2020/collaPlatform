# Ops Security Baseline

## Runtime

- Spring Actuator exposes `health`, `info`, `metrics`, and `prometheus`.
- Logs are JSON-shaped and split into app/error rolling files under `LOG_PATH`.
- Hibernate slow query logging starts at 500 ms.

## Security

- CORS origins are configured through `CORS_ALLOWED_ORIGINS`.
- Password policy defaults to 8+ characters with at least one letter and one digit.
- Refresh tokens rotate on refresh because the old session is revoked before issuing a new one.
- Upload size is bounded by `MAX_UPLOAD_SIZE_BYTES` and reverse proxy `client_max_body_size`.

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
