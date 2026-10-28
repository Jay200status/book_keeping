# 记账小程序最小版

> 本文保留第一版最小功能说明。当前管理端、预算提醒、多账本等功能的运行方式请看 [current-setup.md](current-setup.md)。

## 用户会看到什么

- **账单**是首页。顶部独立卡片可以按年、月、日切换总支出和时间；下面显示同一时间范围内的每笔支出，按发生时间倒序。点击一笔可看金额、用途、时间、来源及备注（可为空）。
- 底部是**账单 / 添加 / 我的**三个入口，中间的绿色圆形添加按钮更醒目。
- **添加**填写金额和用途，备注选填；保存后回到账单。
- **我的**顶部是个人信息卡片。默认显示“记账用户”，用户可以选择填写昵称，无需上传头像或填写手机号。下方可查看公众号连接状态并生成一次性绑定码。

## 不强制填写个人资料，怎样看到自己的账单

小程序第一次打开时调用 `wx.login`，服务端拿临时代码换取**该小程序下的 OpenID**，建立内部用户和账本会话；这个过程无需用户填写头像、昵称、手机号。后续请求携带服务端生成的随机会话令牌，服务端只返回该用户的账单。客户端不保存 OpenID 或微信密钥。

公众号和小程序的 OpenID 不同，不能直接当作同一个人。若两者绑定在同一个微信开放平台账号下，且接口能取得同一个 UnionID，服务端自动关联。否则用户在“我的”生成绑定码，向公众号发送 `绑定 8位码`；只需做一次。若用户先发送了 `午饭 20元`，服务端会保留待绑定消息，绑定后再入账。不能识别归属时绝不展示给其他用户。

> “无需填写资料”和“完全没有身份识别”是两回事。要在另一台手机或重新打开后仍看到私有账单，服务端必须识别用户；这里使用微信提供的静默身份能力。

## 本地运行服务端

需要 Java 21、Maven 3.6.3+ 和 MySQL 8.0.16+。Spring Boot 配置在 [application.properties](../server/src/main/resources/application.properties)，其中的密钥从环境变量读取。先执行 [建表脚本](../db/schema.sql)。若已执行过之前版本的建表脚本，改用 [增量迁移](../db/migrations/001_java_server.sql)。PowerShell 示例：

```powershell
cd server
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/book_keeping?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai'
$env:DB_USER = 'root' # 换成有 book_keeping 库读写权限的账号
$env:DB_PASSWORD = '你的数据库密码'
$env:MINI_APP_ID = '你的小程序AppID'
$env:MINI_APP_SECRET = '你的小程序AppSecret'
$env:OFFICIAL_APP_ID = '你的公众号AppID'
$env:OFFICIAL_APP_SECRET = '你的公众号AppSecret'
$env:WECHAT_TOKEN = '自定义随机回调Token'
$env:WECHAT_ENCODING_AES_KEY = '公众号安全模式EncodingAESKey'
$env:BIND_SECRET = '自定义长随机绑定密钥'
mvn spring-boot:run
```

访问 `http://127.0.0.1:3000/health` 应返回 `{"ok":true}`。`mvn test` 运行消息解析和微信消息解密单元测试。小程序身份接口现在直接请求微信，联调需要真实 AppID/Secret。

在 IntelliJ IDEA 中，请打开 `server/pom.xml` 并作为 Maven 项目导入。启动类是 `server/src/main/java/com/example/bookkeeping/Application.java`，其中的 `main` 方法可直接运行；配置文件是 `server/src/main/resources/application.properties`。

在微信开发者工具中导入 `miniprogram/`。将 `miniprogram/config.js` 的 `API_BASE` 改成服务端地址。本机联调可以暂时使用 `http://127.0.0.1:3000`，并在开发者工具关闭“校验合法域名”；真机及发布必须用在小程序后台配置的 HTTPS request 合法域名。把 `project.config.json` 的 `appid` 换成自己的小程序 AppID。

## 接入真实公众号

1. 准备公众号、小程序账号。需要自动 UnionID 关联时，将两者绑定到同一微信开放平台账号；即使无法取得 UnionID，绑定码仍可工作。
2. 为 API 部署 Java 21、MySQL 和 HTTPS 反向代理。把服务端环境变量中的数据库连接、小程序和公众号 AppID/Secret、随机 `WECHAT_TOKEN`、随机 `BIND_SECRET` 填好。MySQL 数据应定期备份，密钥只放服务端。
3. 在公众号开发设置中，将服务器 URL 指向 `https://你的域名/wechat/callback`，Token 与 `WECHAT_TOKEN` 一致。使用安全模式时，EncodingAESKey 与 `WECHAT_ENCODING_AES_KEY` 一致；服务端也支持已签名的明文模式。
4. 在小程序后台设置 HTTPS request 合法域名，并将 `miniprogram/config.js` 指向同一 API 域名。公众号发送 `午饭 20元`；可以再接可选备注，如 `打车 18元 回家`。

公众号消息用 `MsgId` 去重。无法解析的文字不会变成金额为零的假账；它会以 `NEEDS_REVIEW` 状态留在服务端。当前最小版没有管理页面，后续可以按已有的 [数据库设计](database-design.md)补管理功能。

## 接口与数据

小程序使用 `POST /api/session`、`GET /api/records?period=year|month|day&date=YYYY-MM-DD`、`GET /api/records/:id`、`POST /api/records`、`GET/POST /api/me` 和 `POST /api/binding-code`。公众号使用 `GET/POST /wechat/callback`。所有账单接口都按服务端会话的用户 ID 过滤，不接受客户端传入用户 ID。

当前 Java 服务端使用 MySQL 的 `DECIMAL(18,2)` 保存金额；API 把总支出换算为整数分返回小程序，避免前端浮点求和。身份映射、消息去重、绑定状态和账单所有权校验均在服务端处理。现阶段不含收入、账单编辑、共享账本和管理端。之前 Node/SQLite 原型已移到 `legacy/node-sqlite/`，其数据库数据不会自动迁入 MySQL。
