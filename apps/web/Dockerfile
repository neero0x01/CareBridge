# Dependencies
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# Build
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# Runtime (Next.js standalone)
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup -S carebridge && adduser -S carebridge -G carebridge

COPY --from=build /app/public ./public
COPY --from=build --chown=carebridge:carebridge /app/.next/standalone ./
COPY --from=build --chown=carebridge:carebridge /app/.next/static ./.next/static

USER carebridge
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "server.js"]
