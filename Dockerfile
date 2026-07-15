FROM node:22-alpine

WORKDIR /app
COPY collaboration/package.json ./collaboration/package.json
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
RUN corepack enable && pnpm install --frozen-lockfile --filter @colla/collaboration...
COPY collaboration ./collaboration

EXPOSE 1234
CMD ["pnpm", "--dir", "collaboration", "start"]
