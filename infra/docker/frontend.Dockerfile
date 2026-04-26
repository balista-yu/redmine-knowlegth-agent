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
FROM node:20-alpine

WORKDIR /app

# Vite dev server からホストマシンの 5173 で待ち受けるため
ENV HOST=0.0.0.0

# wget は alpine の busybox に同梱されているので healthcheck で利用可能
EXPOSE 5173

CMD ["sh", "-c", "npm install && npm run dev -- --host 0.0.0.0"]
