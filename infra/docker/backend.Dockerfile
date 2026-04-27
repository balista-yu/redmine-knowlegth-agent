# =============================================================================
# Backend (Spring Boot + Kotlin) - 開発用 Dockerfile
# =============================================================================
# - 想定用途: docker compose 上で Gradle wrapper を使った開発実行 (bootRun)
# - 本番ビルド用 multi-stage 化は別タスクで対応する
# - WORKDIR /app 配下は compose.yaml が ./backend を bind mount する前提
#   (mount 前の段階では /app は空で、bootRun は失敗する)
#
# Gradle wrapper / build.gradle.kts / src/ は T-1-1 (Backend プロジェクト初期化) で
# 追加される。それまでは `docker compose up backend` は failed health で停止する。
# =============================================================================
FROM eclipse-temurin:25-jdk

WORKDIR /app

# Gradle daemon が起動できるよう最低限のツールだけ入れる
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8080

# Spring Boot Actuator の /actuator/health を healthcheck で叩く前提 (compose 側で定義)
CMD ["./gradlew", "bootRun", "--no-daemon"]
