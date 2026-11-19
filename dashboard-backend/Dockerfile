FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run verify && npm prune --omit=dev
FROM node:22-alpine AS runtime
WORKDIR /app
COPY --from=builder --chown=node:node /app /app
USER node
ENV NODE_ENV=production NODE_OPTIONS=--max-old-space-size=128
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=10 CMD node -e "fetch('http://localhost:8080/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "dashboard-backend/index.mjs"]