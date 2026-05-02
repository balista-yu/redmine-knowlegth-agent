# scripts/

開発・検証用の補助スクリプト集。

## seed-redmine.sh

Redmine に動作確認用テストプロジェクト + 12 件のサンプルチケットを投入する。

### 前提

1. `task up` (= `docker compose up -d`) で全コンテナが起動済み
2. Redmine が `http://localhost:3000` で応答する状態

### 初回セットアップ手順

Redmine 5 の admin ユーザーは初期パスワードが `admin` だが、初回ログイン時に
変更を強制される。半自動化のため以下の手順を 1 度だけ実施する。

1. **ブラウザで `http://localhost:3000` を開く**

2. **`admin` / `admin` でサインイン**
   → パスワード変更画面に遷移するので任意の新パスワードを設定

3. **API キーの取得**
   - 右上「個人設定」 (`/my/account`) をクリック
   - 右ペイン「APIアクセスキー」の [表示] をクリック
   - 40 桁の英数字をコピー

4. **環境変数として export**

   ```bash
   export REDMINE_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   ```

   永続化したい場合は `.env` の `REDMINE_API_KEY=` 行に書き、`task up` 時に
   backend コンテナ環境変数として注入される (本スクリプト自体はホスト OS から
   `localhost:3000` を直接叩くため、シェルの env だけでも動作可)。

5. **seed 実行**

   ```bash
   task seed-redmine
   # または直接:
   # bash scripts/seed-redmine.sh
   ```

   12 件 (Bug 5 / Feature 4 / Support 3) の issue が `ai-agent-test` プロジェクトに
   作成される。一部の issue には journal (notes) も追加される。

6. **確認**

   ```bash
   curl http://localhost:3000/projects/ai-agent-test/issues.json \
     -H "X-Redmine-API-Key: $REDMINE_API_KEY" | jq '.issues | length'
   # → 12 (またはそれ以上) と表示されれば OK
   ```

   または UI で:
   `http://localhost:3000/projects/ai-agent-test/issues`

### 冪等性

- プロジェクト `ai-agent-test` が既に存在する場合は作成をスキップする
- issue は毎回追加される (重複防止はしない)。再実行する場合は事前に Redmine UI から
  バルク削除するか、テスト用なら `docker compose down -v` で `redmine-files` /
  `redmine-db` の volume を消して `task up` から再実行するのが確実

### 関連タスク (Taskfile.yaml)

| コマンド               | 内容                                       |
| ---------------------- | ------------------------------------------ |
| `task seed-redmine`    | 本スクリプト実行                           |
| `task sync`            | seed 後の手動差分同期 (`POST /api/sync`)    |
| `task sync-status`     | 同期状態確認 (`GET /api/sync/status`)       |
