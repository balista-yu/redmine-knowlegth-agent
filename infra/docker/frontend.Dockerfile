# =============================================================================
# Frontend (React + Vite + TypeScript) - 開発用 Dockerfile
# =============================================================================
# - 想定用途: docker compose 上で Vite dev server を起動 (npm run dev)
# - 本番ビルド (vite build → nginx) は別タスクで対応する
# - WORKDIR /app 配下は compose.yaml が ./frontend を bind mount する前提
#
# package.json / vite.config.ts は T-3-1 (React + Vite 雛形) で追加される。
# それまでは `docker compose up frontend` は failed health で停止する。
# =============================================================================
FROM node:24-bookworm-slim

WORKDIR /app

# slim 版には wget が無いため healthcheck (compose.yaml) で使う wget を追加
# ca-certificates も npm install (HTTPS) のため明示インストール
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Vite dev server からホストマシンの 5173 で待ち受けるため
ENV HOST=0.0.0.0

EXPOSE 5173

CMD ["sh", "-c", "npm install && npm run dev -- --host 0.0.0.0"]
