FROM node:24-alpine@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd

ARG SOURCE_COMMIT=unknown
LABEL org.opencontainers.image.revision=$SOURCE_COMMIT

WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@9.4.0 --activate

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY tools/capacity/package.json tools/capacity/package.json
RUN pnpm install --frozen-lockfile --filter @colla/capacity...

COPY tools/capacity tools/capacity
WORKDIR /workspace/tools/capacity

USER node
ENTRYPOINT ["node", "src/cli.mjs"]
CMD ["contract", "validate"]
