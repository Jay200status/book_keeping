# Feature status against the expanded requirements

## Implemented in this change

- Mini program: silent WeChat login; income, expense, and transfer entry; multiple ledgers; asset and liability accounts with calculated balances and a net asset overview; custom categories with icons and sorting; quick templates; ledger member invitations and viewer/editor roles; creator-only edits and deletion; transaction comments; time, category, account, member and keyword filters; monthly and annual reports with a daily calendar; budget status and budget setup; reminder rules and recurring bill approval; feedback and help content.
- Admin: light-green sidebar dashboard with ECharts; user list and status; ledger list; bill search, add, edit, soft delete and CSV export; duplicate-transaction review; monthly/category budgets and overspending notifications; bookkeeping and bill due reminders; recurring bill templates with pending approval; feedback handling; system default categories; banners, announcements, FAQ, version prompts, privacy policy and terms content; admin accounts and roles; login history, rate limiting and operation audit.
- WeChat subscription messages: user-initiated one-time authorization, configurable template keys, scheduled dispatch, and delivery status logging. Live template configuration is still required.

## Still needs product rules, external service setup, or further implementation

- Receipt OCR, voice bookkeeping, reimbursement workflows, multi-currency and user-side Excel export were explicitly marked **advanced** and deferred.
- Phone/SMS login, membership plans, campaigns, coupons and invitation rewards were explicitly skipped in favor of silent WeChat login and the core bookkeeping flow.
- User-facing backup/export, account cancellation requests, and privacy consent tracking are not yet implemented.
- User report moderation, feature-use and retention metrics, full data masking policies, database backup/restore controls, and general API rate limits need further work. Duplicate-transaction review is the first abnormal-transaction check and is not a complete fraud detector.
- Migration SQL and live WeChat delivery require integration testing in an environment with MySQL and actual AppID, secret and message template.
