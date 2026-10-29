# Current setup

The project uses Spring Boot 3.5 / Java 21, Vue 3 + Vite, MySQL 8, and ECharts. The WeChat mini program is in `miniprogram/`; the admin frontend is in `admin/`.

## Database

For a fresh database, run `db/schema.sql` once with a MySQL account allowed to create the `book_keeping` database. The script creates tables and the initial system category templates.

For an existing database, apply migrations in numerical order. If it predates `001_java_server.sql`, apply that migration first; otherwise apply `002_admin_session.sql` through `008_transaction_comments.sql`. Back up the database before applying migrations. `006_feedback_categories.sql` adds active default categories to existing ledgers without replacing their existing categories.

## Backend

Set these environment variables before starting `server/` with `mvn spring-boot:run`. `DB_URL`, `DB_USER`, and `DB_PASSWORD` are required; `application.properties` contains references to them rather than connection credentials. For the local MySQL instance, run `server/load-local-env.ps1` in the same PowerShell session before starting the backend. It reads the ignored `server/.env` file created during local setup:

```powershell
& .\server\load-local-env.ps1
mvn -f server/pom.xml spring-boot:run
```

The local `book_keeping` database has a dedicated `bookkeeping` account. Its generated password is stored only in the ignored `server/.env`. That file already contains the mini-program AppID and an empty `MINI_APP_SECRET` entry. Fill in the secret locally before testing silent WeChat login; the loader accepts the other application environment variables listed below too.

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | MySQL connection |
| `MINI_APP_ID`, `MINI_APP_SECRET` | Mini program identity and subscription messages |
| `OFFICIAL_APP_ID`, `OFFICIAL_APP_SECRET` | Official account bookkeeping |
| `WECHAT_TOKEN`, `WECHAT_ENCODING_AES_KEY`, `BIND_SECRET` | Official account callback and binding |
| `ADMIN_BOOTSTRAP_USER`, `ADMIN_BOOTSTRAP_PASSWORD` | Creates the first admin account only when `admin_user` is empty; password must be at least 12 characters |

The backend listens on port 3000 by default. Check `http://127.0.0.1:3000/health` after startup. Remove bootstrap credentials from the runtime environment once the first admin account exists. Admin passwords are stored as salted PBKDF2 hashes, and session tokens are stored only as SHA-256 digests.

## Admin frontend

From `admin/`, run `npm ci` and `npm run dev`. Open the URL printed by Vite. Its development proxy sends `/api` requests to `http://127.0.0.1:3000`. For a production build run `npm run build`; serve `admin/dist/` on the same origin as the backend API or configure the host to route `/api` to the backend.

The sidebar contains dashboard, user center, ledger management, bill statistics, budgets and reminders, operations, and system permissions. An `ADMIN` may access financial records and manage accounts, user status, content, and default categories. Until a narrower operator policy is specified, an `OPERATOR` sees only aggregate dashboard statistics. Bill corrections and deletion are recorded in `admin_audit_log`.

## Mini program

Import `miniprogram/` into WeChat Developer Tools. The local simulator uses `http://127.0.0.1:3000` from `miniprogram/config.js`; project settings disable request-domain validation for this local development setup. For real devices or release, replace that URL with your reachable HTTPS backend, register its host as a WeChat request domain, and re-enable URL validation. The mini program supports ledgers, accounts, categories, income, expense, transfers, quick templates, shared ledger invitations, reports, help content, and feedback.

The backend was started on port 3000 during local setup. Restart it after filling in `MINI_APP_SECRET` or changing any other environment setting. A separate temporary test instance was verified on port 3001 and stopped afterward.

## Backend packages

Spring Boot classes are grouped under `admin`, `finance`, `planning`, `content`, `wechat`, `config`, and `web` beneath `com.example.bookkeeping`. The application entry point remains in the parent package so component scanning discovers all feature packages.

## WeChat subscription reminders

Set `MINI_REMINDER_TEMPLATE_ID` to a template configured for the mini program in WeChat. The default field keys are `thing1` (title), `time2` (time), and `thing3` (content). If the chosen template uses different keys, set `MINI_REMINDER_TITLE_KEY`, `MINI_REMINDER_TIME_KEY`, and `MINI_REMINDER_CONTENT_KEY` to match. `MINI_REMINDER_PAGE` defaults to `pages/me/index`. These three keys must be distinct, and the selected template must accept the value types and lengths sent by the backend.

The user taps **Subscribe once** in the mini program's Me page. Each accepted one-time subscription permits one message; the user can repeat the action to authorize more. The scheduler scans every 15 minutes for budget overspending and due reminders. If no matching permission or template is configured, the event stays unsent and appears in the admin message log. A template ID and live WeChat account are required for end-to-end verification. See the [WeChat subscription message API](https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/mp-message-management/subscribe-message/sendMessage.html).

Recurring bill templates generate pending monthly occurrences. A ledger owner or an admin must approve each occurrence before it becomes a ledger record. The scheduler runs only while the Spring Boot process is running.

## Verification

Run `mvn test` in `server/`, `npm run build` in `admin/`, and `node --check` on mini program JavaScript files. Database migrations, live WeChat delivery, and mini program UI behavior require a local MySQL instance and WeChat Developer Tools; these are not exercised by the unit tests.
