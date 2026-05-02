#!/usr/bin/env bash
# =============================================================================
# scripts/seed-redmine.sh
# =============================================================================
# Redmine に AI エージェントの動作確認用テストプロジェクトと 12 件のチケットを投入する。
# 一部チケットには journal (notes) も追加し、F-01 (差分同期) でチャンク化される
# description + journals が混在するデータセットを再現する。
#
# 前提:
#  - `task up` で Redmine が起動済み (http://localhost:3000 へアクセス可能)
#  - 初回 admin ログイン (admin / admin) → パスワード変更を済ませる
#  - 環境変数 REDMINE_API_KEY に admin ユーザーの API キーを設定 (取得手順は scripts/README.md)
#
# 使い方:
#  $ task seed-redmine
#    または
#  $ REDMINE_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx bash scripts/seed-redmine.sh
#
# DoD: 実行後 Redmine UI ([プロジェクト] → "AI Agent Test") で 10 件以上のチケットが見える
# =============================================================================
set -euo pipefail

REDMINE_BASE_URL="${REDMINE_BASE_URL_HOST:-http://localhost:3000}"
PROJECT_IDENTIFIER="ai-agent-test"
PROJECT_NAME="AI Agent Test"

if [ -z "${REDMINE_API_KEY:-}" ]; then
  cat >&2 <<'EOF'
[seed-redmine] REDMINE_API_KEY が設定されていません。
  1. ブラウザで http://localhost:3000 を開く
  2. admin / admin でログイン → パスワード変更
  3. 右上「個人設定」→ 右ペイン「APIアクセスキー」→ [表示] をクリック
  4. 表示された 40 桁のキーを `export REDMINE_API_KEY=xxxx...` で設定
  5. 再度 `task seed-redmine` を実行
EOF
  exit 1
fi

# REST API 呼び出しのラッパー (jq で .errors を抽出して可読出力)
api() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -X "$method" \
      -H "X-Redmine-API-Key: ${REDMINE_API_KEY}" \
      -H "Content-Type: application/json" \
      -d "$body" \
      "${REDMINE_BASE_URL}${path}"
  else
    curl -sS -X "$method" \
      -H "X-Redmine-API-Key: ${REDMINE_API_KEY}" \
      "${REDMINE_BASE_URL}${path}"
  fi
}

# 既存 project 確認 + なければ作成
ensure_project() {
  local existing
  existing=$(api GET "/projects/${PROJECT_IDENTIFIER}.json" 2>/dev/null || true)
  if echo "$existing" | grep -q '"identifier":"'"${PROJECT_IDENTIFIER}"'"'; then
    echo "[seed-redmine] project '${PROJECT_IDENTIFIER}' already exists, skipping creation"
    return 0
  fi
  echo "[seed-redmine] creating project '${PROJECT_IDENTIFIER}'..."
  api POST "/projects.json" "$(cat <<EOF
{"project":{"identifier":"${PROJECT_IDENTIFIER}","name":"${PROJECT_NAME}","description":"Redmine Knowledge Agent の動作確認用 seed データ。Bug / Task / Feature が混在し、journal 付き issue も含まれる。","is_public":true}}
EOF
)" >/dev/null
}

# tracker / status / priority の id を解決 (Redmine の標準 seed に依存)
# tracker:  1=Bug 2=Feature 3=Support
# status:   1=New 2=InProgress 5=Closed
# priority: 2=Normal 3=High 4=Urgent
create_issue() {
  local subject="$1"
  local tracker_id="$2"
  local priority_id="$3"
  local description="$4"
  local note="${5:-}"
  local response issue_id

  response=$(api POST "/issues.json" "$(cat <<EOF
{"issue":{"project_id":"${PROJECT_IDENTIFIER}","tracker_id":${tracker_id},"priority_id":${priority_id},"subject":"${subject}","description":${description}}}
EOF
)")
  issue_id=$(echo "$response" | sed -n 's/.*"issue":{"id":\([0-9]*\).*/\1/p')

  if [ -z "$issue_id" ]; then
    echo "[seed-redmine] WARN: failed to create issue '${subject}': $response" >&2
    return 0
  fi

  echo "  - #${issue_id} ${subject}"

  # オプション: journal (notes) を追加
  if [ -n "$note" ]; then
    api PUT "/issues/${issue_id}.json" "$(cat <<EOF
{"issue":{"notes":${note}}}
EOF
)" >/dev/null
  fi
}

main() {
  echo "[seed-redmine] target: ${REDMINE_BASE_URL}"

  ensure_project

  echo "[seed-redmine] creating sample issues..."

  # ---- Bug × 5 ----
  create_issue "本番Webで証明書期限切れ" 1 4 \
    '"Let'"'"'s Encrypt の自動更新 cron が止まっていた。手動で certbot renew → nginx reload で復旧。"' \
    '"暫定対応: cron job を再有効化。恒久対応として Renewal hook の死活監視を追加予定。"'

  create_issue "PostgreSQL connection pool が枯渇する" 1 3 \
    '"HikariCP の maximum-pool-size が 10 のままだった。負荷時に Connection is not available タイムアウト多発。\n暫定で 30 に引き上げ、長期的にはアプリ側のスロットル + 監視ダッシュボード追加が必要。"' \
    '"pool size を 30 に変更後 24h 観測。タイムアウトは 0 件で推移。"'

  create_issue "Vite dev server が file watch limit で落ちる" 1 2 \
    '"Linux ホストの inotify watcher の上限 (8192) に到達して dev server が起動失敗。\n対処: /etc/sysctl.conf に fs.inotify.max_user_watches=524288 を追記。"' \
    ''

  create_issue "Docker compose down 時に volume が消える" 1 2 \
    '"`docker compose down -v` を CI で誤実行していた。-v なしの down に修正。"' \
    ''

  create_issue "Spring Boot の actuator/health が UNKNOWN を返す" 1 3 \
    '"Flyway migration 失敗時に health 全体が UNKNOWN になる。再現条件: V1 のチェックサム不一致。\n対処: baseline-on-migrate を環境変数で切り替え可能にした。"' \
    '"docker compose の environment に SPRING_FLYWAY_BASELINE_ON_MIGRATE を追加。"'

  # ---- Feature × 4 ----
  create_issue "Qdrant コレクション payload に subject を追加" 2 3 \
    '"RAG 検索結果のカードに subject + URL を出すため、point の payload にチケット表示用フィールドを同梱する。T-2-2 で実装。"' \
    '"完了。RagSearchTool が直接 payload から subject を読めるようになり余計な lookup が消えた。"'

  create_issue "Sync ダッシュボードに「今すぐ Reconcile」ボタンを追加" 2 2 \
    '"管理画面で手動 Reconcile を発火できるようにする。POST /api/reconcile を呼び、走行中なら 409 を表示。"' \
    ''

  create_issue "Embedding model を multilingual-e5 に変更可能にする" 2 2 \
    '"日英混在のチケットで nomic-embed-text の recall が下がるため、env で切替できるようにした。OllamaConfig に分岐追加済み。"' \
    ''

  create_issue "ChatPage に会話履歴のクリアボタン" 2 2 \
    '"long-running セッションでコンテキストが膨らむ問題。conversationId をリセットするボタンを追加する。"' \
    ''

  # ---- Support × 3 ----
  create_issue "ローカル開発で Ollama に接続できない" 3 2 \
    '"OLLAMA_BASE_URL=http://host.docker.internal:11434 を docker compose の extra_hosts に追加してください (Linux のみ host-gateway が必要)。"' \
    ''

  create_issue "task lint が遅い (約 1 分)" 3 2 \
    '"spotless + detekt で 60 秒前後。Gradle daemon を有効化すれば 10 秒台に短縮可能。docs に追記予定。"' \
    '"gradle.properties に org.gradle.daemon=true を追加するワークアラウンドで暫定対応中。"'

  create_issue "Frontend の MSW テストが Node 24 で落ちる" 3 3 \
    '"jsdom + undici fetch の AbortSignal instanceof チェックが失敗する件。\nstreamChat から AbortController を取り除いて回避。"' \
    '"後日 Kotest 6 + Spring extension が出たら再評価。"'

  echo "[seed-redmine] done. Verify on http://localhost:3000/projects/${PROJECT_IDENTIFIER}/issues"
}

main "$@"
