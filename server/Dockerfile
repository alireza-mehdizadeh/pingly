# Pingly backend. Node 24 runs the TypeScript sources directly (type stripping), so there is
# no build step: install production deps, copy src, run.
FROM node:24-bookworm-slim

ENV NODE_ENV=production \
    DATA_DIR=/data \
    PORT=3000

WORKDIR /app

# Coolify's rolling-update healthcheck probes the container with wget/curl.
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/*

COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund && npm cache clean --force

COPY src ./src

# /data holds the SQLite database, the server secret and encrypted media. Mount a persistent
# volume here; a fresh named volume inherits this ownership.
RUN mkdir -p /data/media && chown -R node:node /data /app
USER node
VOLUME ["/data"]

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:3000/health').then(r => process.exit(r.ok ? 0 : 1)).catch(() => process.exit(1))"

CMD ["node", "--disable-warning=ExperimentalWarning", "src/index.ts"]
