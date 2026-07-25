FROM node:24-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd

ARG SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.revision=$SOURCE_COMMIT

WORKDIR /app
COPY collaboration/package.json ./collaboration/package.json
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
RUN corepack enable && pnpm install --frozen-lockfile --filter @colla/collaboration...
COPY collaboration ./collaboration

EXPOSE 1234
CMD ["pnpm", "--dir", "collaboration", "start"]
