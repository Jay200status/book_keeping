# Bookkeeping

A bookkeeping mini program and admin management system built with Spring Boot, Vue 3, MySQL, and ECharts. The admin interface uses a light-green sidebar and includes a dashboard, user and ledger management, bill statistics, budgets and reminders, operations content, and access controls.

The mini program supports silent WeChat login, income and expense entries, account transfers, shared ledgers, reports, budgets, reminders, and recurring bills that require confirmation before posting.

- [Setup, migrations, and configuration](docs/current-setup.md)
- [Implemented features and remaining work](docs/feature-status.md)
- [Database schema](db/schema.sql)
- [Spring Boot backend](server/)
- [Vue admin frontend](admin/)
- [WeChat mini program](miniprogram/)

The earlier Node.js/SQLite prototype is retained in [legacy/node-sqlite](legacy/node-sqlite/) for reference.
