package com.example.bookkeeping;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern BIND = Pattern.compile("^绑定\\s+([A-Fa-f0-9]{8})$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final AppConfig config;
    private final WechatClient wechat;

    public LedgerService(JdbcTemplate jdbc, AppConfig config, WechatClient wechat) {
        this.jdbc = jdbc; this.config = config; this.wechat = wechat;
    }

    private static LocalDateTime now() { return LocalDateTime.now(SHANGHAI); }
    private static Timestamp stamp(LocalDateTime value) { return Timestamp.valueOf(value); }
    private static String format(LocalDateTime value) { return value.format(DATETIME); }
    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes]; RANDOM.nextBytes(value); return HexFormat.of().formatHex(value);
    }
    private static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private String bindingDigest(String code) {
        if (config.bindSecret.isBlank()) throw new IllegalStateException("尚未配置绑定密钥");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.bindSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.toUpperCase().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private Long oneLong(String sql, Object... args) {
        List<Long> rows = jdbc.query(sql, (rs, row) -> rs.getLong(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private String oneString(String sql, Object... args) {
        List<String> rows = jdbc.query(sql, (rs, row) -> rs.getString(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private long insert(String sql, Object... args) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        if (holder.getKey() == null) throw new IllegalStateException("数据库未返回 ID");
        return holder.getKey().longValue();
    }

    private long createUser(String unionId) {
        long userId = insert("INSERT INTO app_user(union_id) VALUES (?)", unionId);
        long bookId = insert("INSERT INTO account_book(owner_user_id,name) VALUES (?,?)", userId, "我的账本");
        jdbc.update("INSERT INTO category(book_id,type,name,icon,sort_order) SELECT ?,type,name,icon,sort_order FROM system_category_template WHERE is_active=TRUE", bookId);
        return userId;
    }

    private void mergeUsers(long sourceId, long targetId) {
        if (sourceId == targetId) return;
        String sourceUnion = oneString("SELECT union_id FROM app_user WHERE id = ? FOR UPDATE", sourceId);
        String targetUnion = oneString("SELECT union_id FROM app_user WHERE id = ? FOR UPDATE", targetId);
        if (sourceUnion != null && targetUnion != null && !sourceUnion.equals(targetUnion)) throw new IllegalArgumentException("微信身份关联冲突");
        if (sourceUnion != null && targetUnion == null) {
            jdbc.update("UPDATE app_user SET union_id = NULL WHERE id = ?", sourceId);
            jdbc.update("UPDATE app_user SET union_id = ? WHERE id = ?", sourceUnion, targetId);
        }
        // 保留来源账本及其分类和记录，合并后用户可看到名下所有账本的明细。
        jdbc.update("UPDATE account_book SET owner_user_id = ? WHERE owner_user_id = ?", targetId, sourceId);
        jdbc.update("UPDATE wechat_identity SET user_id = ? WHERE user_id = ?", targetId, sourceId);
        jdbc.update("UPDATE binding_code SET user_id = ? WHERE user_id = ?", targetId, sourceId);
        jdbc.update("UPDATE official_message SET user_id = ? WHERE user_id = ?", targetId, sourceId);
        jdbc.update("UPDATE api_session SET user_id = ? WHERE user_id = ?", targetId, sourceId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", sourceId);
    }

    private Long identityUser(String appId, String openId) {
        return oneLong("SELECT user_id FROM wechat_identity WHERE app_id = ? AND open_id = ? FOR UPDATE", appId, openId);
    }

    private long linkIdentity(String appType, String appId, String openId, String unionId) {
        Long known = identityUser(appId, openId);
        if (known != null) {
            if (unionId == null) return known;
            String oldUnion = oneString("SELECT union_id FROM app_user WHERE id = ? FOR UPDATE", known);
            if (oldUnion != null && !oldUnion.equals(unionId)) throw new IllegalArgumentException("微信身份关联冲突");
            Long unionUser = oneLong("SELECT id FROM app_user WHERE union_id = ? FOR UPDATE", unionId);
            if (unionUser != null && !unionUser.equals(known)) { mergeUsers(known, unionUser); return unionUser; }
            if (oldUnion == null) jdbc.update("UPDATE app_user SET union_id = ? WHERE id = ?", unionId, known);
            return known;
        }
        Long unionUser = unionId == null ? null : oneLong("SELECT id FROM app_user WHERE union_id = ? FOR UPDATE", unionId);
        long userId = unionUser == null ? createUser(unionId) : unionUser;
        jdbc.update("INSERT INTO wechat_identity(user_id,app_type,app_id,open_id) VALUES (?,?,?,?)", userId, appType, appId, openId);
        return userId;
    }

    @Transactional
    public Map<String, String> createSession(String code, String deviceInfo, String ipAddress) {
        if (code == null || code.length() < 2 || code.length() > 200) throw new IllegalArgumentException("缺少微信登录凭证");
        WechatClient.Identity identity = wechat.miniIdentity(code);
        long userId = linkIdentity("MINI_PROGRAM", config.miniAppId, identity.openId(), identity.unionId());
        String token = randomHex(32);
        jdbc.update("INSERT INTO api_session(token_digest,user_id,expires_at) VALUES (?,?,?)", sha256(token), userId, stamp(now().plusDays(30)));
        jdbc.update("INSERT INTO app_login_log(user_id,device_info,ip_address) VALUES (?,?,?)", userId,
                deviceInfo == null ? "" : deviceInfo.substring(0, Math.min(deviceInfo.length(), 255)),
                ipAddress == null ? "unknown" : ipAddress.substring(0, Math.min(ipAddress.length(), 45)));
        return Map.of("token", token);
    }

    public Long sessionUser(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [a-fA-F0-9]{64}")) return null;
        return oneLong("SELECT s.user_id FROM api_session s JOIN app_user u ON u.id = s.user_id "
                + "WHERE s.token_digest = ? AND s.expires_at > ? AND u.status = 'ACTIVE'", sha256(authorization.substring(7)), stamp(now()));
    }

    private long defaultBook(long userId) {
        Long bookId = oneLong("SELECT id FROM account_book WHERE owner_user_id = ? ORDER BY id LIMIT 1", userId);
        if (bookId == null) throw new IllegalStateException("用户没有账本");
        return bookId;
    }
    private long expenseCategory(long bookId) {
        Long categoryId = oneLong("SELECT id FROM category WHERE book_id = ? AND type = 'EXPENSE' AND name = '其他' LIMIT 1", bookId);
        if (categoryId == null) {
            categoryId = insert("INSERT INTO category(book_id,type,name) VALUES (?,'EXPENSE','其他')", bookId);
        }
        return categoryId;
    }
    private void addExpense(long userId, ExpenseParser.Expense expense, LocalDateTime occurredAt, String source, Long messageId) {
        long bookId = defaultBook(userId);
        long categoryId = expenseCategory(bookId);
        jdbc.update("INSERT INTO bookkeeping_record(book_id,type,amount,purpose,category_id,occurred_at,note,source,source_message_id,created_by_user_id) "
                + "VALUES (?,'EXPENSE',?,?,?,?,?,?,?,?)", bookId, expense.amount(), expense.purpose(), categoryId,
                stamp(occurredAt), expense.note(), source, messageId, userId);
    }

    @Transactional
    public Map<String, Long> addManual(long userId, String amount, String purpose, String note, String occurredAt) {
        if (purpose == null || purpose.isBlank() || purpose.trim().length() > 40) throw new IllegalArgumentException("用途长度不正确");
        if (note != null && note.trim().length() > 200) throw new IllegalArgumentException("备注过长");
        LocalDateTime time = occurredAt == null || occurredAt.isBlank() ? now() : LocalDateTime.parse(occurredAt, DATETIME);
        ExpenseParser.Expense expense = new ExpenseParser.Expense(purpose.trim(), ExpenseParser.amount(amount), note == null ? "" : note.trim());
        long bookId = defaultBook(userId);
        long categoryId = expenseCategory(bookId);
        long id = insert("INSERT INTO bookkeeping_record(book_id,type,amount,purpose,category_id,occurred_at,note,source,created_by_user_id) "
                + "VALUES (?,'EXPENSE',?,?,?,?,?,'MINI_PROGRAM',?)", bookId, expense.amount(), expense.purpose(), categoryId, stamp(time), expense.note(), userId);
        return Map.of("id", id);
    }

    private static Map<String, Object> record(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> result = new HashMap<>();
        result.put("id", rs.getLong("id"));
        result.put("amount", rs.getBigDecimal("amount").setScale(2).toPlainString());
        result.put("purpose", rs.getString("purpose"));
        result.put("note", rs.getString("note") == null ? "" : rs.getString("note"));
        result.put("occurredAt", format(rs.getTimestamp("occurred_at").toLocalDateTime()));
        result.put("source", rs.getString("source"));
        return result;
    }

    public Map<String, Object> records(long userId, String period, String dateText) {
        LocalDate date = dateText == null ? LocalDate.now(SHANGHAI) : LocalDate.parse(dateText);
        String selected = period == null ? "month" : period;
        LocalDate start = switch (selected) {
            case "year" -> date.withDayOfYear(1);
            case "month" -> date.withDayOfMonth(1);
            case "day" -> date;
            default -> throw new IllegalArgumentException("统计周期不正确");
        };
        LocalDate end = switch (selected) {
            case "year" -> start.plusYears(1);
            case "month" -> start.plusMonths(1);
            default -> start.plusDays(1);
        };
        String from = " FROM bookkeeping_record r JOIN account_book b ON b.id = r.book_id WHERE b.owner_user_id = ? "
                + "AND r.type = 'EXPENSE' AND r.deleted_at IS NULL AND r.occurred_at >= ? AND r.occurred_at < ?";
        Object[] args = { userId, stamp(start.atStartOfDay()), stamp(end.atStartOfDay()) };
        BigDecimal total = jdbc.queryForObject("SELECT COALESCE(SUM(r.amount),0)" + from, BigDecimal.class, args);
        List<Map<String, Object>> items = jdbc.query("SELECT r.id,r.amount,r.purpose,r.note,r.occurred_at,r.source" + from
                + " ORDER BY r.occurred_at DESC,r.id DESC", (rs, row) -> record(rs), args);
        return Map.of("period", selected, "date", date.toString(), "totalCents", total.movePointRight(2).longValueExact(), "records", items);
    }

    public Map<String, Object> detail(long userId, long recordId) {
        List<Map<String, Object>> rows = jdbc.query("SELECT r.id,r.amount,r.purpose,r.note,r.occurred_at,r.source "
                + "FROM bookkeeping_record r JOIN account_book b ON b.id = r.book_id "
                + "WHERE r.id = ? AND b.owner_user_id = ? AND r.deleted_at IS NULL", (rs, row) -> record(rs), recordId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> me(long userId) {
        String name = oneString("SELECT display_name FROM app_user WHERE id = ?", userId);
        Long bound = oneLong("SELECT id FROM wechat_identity WHERE user_id = ? AND app_id = ? LIMIT 1", userId, config.officialAppId);
        return Map.of("displayName", name == null || name.isBlank() ? "记账用户" : name, "officialBound", bound != null);
    }

    public Map<String, String> updateName(long userId, String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.length() > 30) throw new IllegalArgumentException("昵称不能超过 30 字");
        jdbc.update("UPDATE app_user SET display_name = ? WHERE id = ?", cleaned.isBlank() ? null : cleaned, userId);
        return Map.of("displayName", cleaned.isBlank() ? "记账用户" : cleaned);
    }

    public Map<String, Object> bindingCode(long userId) {
        String code = randomHex(4).toUpperCase();
        LocalDateTime expires = now().plusMinutes(10);
        jdbc.update("INSERT INTO binding_code(user_id,code_digest,expires_at) VALUES (?,?,?)", userId, bindingDigest(code), stamp(expires));
        long epochMillis = expires.atZone(SHANGHAI).toInstant().toEpochMilli();
        return Map.of("code", code, "expiresAt", epochMillis);
    }

    @Transactional
    public void officialMessage(String messageId, String openId, String content, String unionId) {
        if (messageId == null || !messageId.matches("[0-9]{1,64}") || openId == null || openId.isBlank()
                || content == null || content.isBlank() || content.length() > 2000) throw new IllegalArgumentException("公众号消息格式不正确");
        Long duplicate = oneLong("SELECT id FROM official_message WHERE official_app_id = ? AND wechat_msg_id = ?", config.officialAppId, messageId);
        if (duplicate != null) return;
        Long userId = identityUser(config.officialAppId, openId);
        if (unionId != null && !unionId.isBlank()) userId = linkIdentity("OFFICIAL_ACCOUNT", config.officialAppId, openId, unionId);
        Matcher binding = BIND.matcher(content.trim());
        boolean isBinding = binding.matches();
        boolean bound = false;
        if (isBinding) {
            String digest = bindingDigest(binding.group(1));
            Long codeUser = oneLong("SELECT user_id FROM binding_code WHERE code_digest = ? AND expires_at > ? "
                    + "AND consumed_at IS NULL FOR UPDATE", digest, stamp(now()));
            if (codeUser != null) {
                if (userId != null && !userId.equals(codeUser)) mergeUsers(userId, codeUser);
                if (userId == null) jdbc.update("INSERT INTO wechat_identity(user_id,app_type,app_id,open_id) VALUES (?,'OFFICIAL_ACCOUNT',?,?)",
                        codeUser, config.officialAppId, openId);
                jdbc.update("UPDATE binding_code SET consumed_at = ? WHERE code_digest = ?", stamp(now()), digest);
                userId = codeUser; bound = true;
            }
        }
        ExpenseParser.Expense expense = isBinding ? null : ExpenseParser.parse(content);
        String status = isBinding ? (bound ? "BOUND" : "NEEDS_REVIEW")
                : userId == null ? "NEEDS_BINDING" : expense == null ? "NEEDS_REVIEW" : "POSTED";
        long id = insert("INSERT INTO official_message(official_app_id,wechat_msg_id,sender_open_id,user_id,content,status,received_at,processed_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)", config.officialAppId, messageId, openId, userId, content, status, stamp(now()), stamp(now()));
        if (userId != null && expense != null) addExpense(userId, expense, now(), "OFFICIAL_ACCOUNT", id);
        if (userId != null) flushPending(openId, userId);
    }

    private void flushPending(String openId, long userId) {
        List<Map<String, Object>> pending = jdbc.query("SELECT id,content,received_at FROM official_message WHERE official_app_id = ? "
                + "AND sender_open_id = ? AND status = 'NEEDS_BINDING' ORDER BY received_at,id", (rs, row) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", rs.getLong("id")); item.put("content", rs.getString("content"));
            item.put("time", rs.getTimestamp("received_at").toLocalDateTime()); return item;
        }, config.officialAppId, openId);
        for (Map<String, Object> item : pending) {
            long messageId = (Long) item.get("id");
            ExpenseParser.Expense expense = ExpenseParser.parse((String) item.get("content"));
            jdbc.update("UPDATE official_message SET user_id = ?, status = ?, processed_at = ? WHERE id = ?",
                    userId, expense == null ? "NEEDS_REVIEW" : "POSTED", stamp(now()), messageId);
            if (expense != null) addExpense(userId, expense, (LocalDateTime) item.get("time"), "OFFICIAL_ACCOUNT", messageId);
        }
    }
}
