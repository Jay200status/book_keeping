package com.example.bookkeeping.finance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Date;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MiniFinanceService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    public MiniFinanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static String str(Object value) { return value == null ? "" : value.toString().trim(); }
    private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.now(SHANGHAI)); }
    private static String text(Object value, int max, String name) {
        String result = str(value);
        if (result.isBlank() || result.length() > max) throw new IllegalArgumentException(name + "长度不正确");
        return result;
    }
    private static String optional(Object value, int max, String name) {
        String result = str(value);
        if (result.length() > max) throw new IllegalArgumentException(name + "过长");
        return result;
    }
    private static long id(Object value, String name) {
        try { long result = Long.parseLong(str(value)); if (result <= 0) throw new NumberFormatException(); return result; }
        catch (NumberFormatException error) { throw new IllegalArgumentException(name + "不正确"); }
    }
    private static BigDecimal amount(Object value) {
        try {
            BigDecimal result = new BigDecimal(str(value));
            if (result.signum() <= 0 || result.scale() > 2 || result.precision() > 18) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("金额必须为正数，最多两位小数"); }
    }
    private static BigDecimal signedAmount(Object value) {
        try {
            BigDecimal result = new BigDecimal(str(value));
            if (result.scale() > 2 || result.precision() > 18) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("期初余额不正确"); }
    }
    private static Timestamp time(Object value) {
        String text = str(value);
        return text.isBlank() ? now() : Timestamp.valueOf(LocalDateTime.parse(text.replace('T', ' '),
                text.length() == 16 ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") : DATETIME));
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private long insert(String sql, Object... args) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }
    private String role(long userId, long bookId) {
        List<String> roles = jdbc.query("SELECT CASE WHEN b.owner_user_id=? THEN 'OWNER' ELSE m.role END role FROM account_book b "
                + "LEFT JOIN book_member m ON m.book_id=b.id AND m.user_id=? WHERE b.id=? AND (b.owner_user_id=? OR m.user_id=?)",
                (rs, row) -> rs.getString(1), userId, userId, bookId, userId, userId);
        if (roles.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该账本");
        return roles.get(0);
    }
    private void access(long userId, long bookId, boolean write) {
        String role = role(userId, bookId);
        if (write && role.equals("VIEWER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该账本只有查看权限");
    }
    public void owner(long userId, long bookId) {
        if (!role(userId, bookId).equals("OWNER")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有账本创建者可管理此设置");
    }
    private static String type(Object value) {
        String result = str(value);
        if (!result.equals("EXPENSE") && !result.equals("INCOME")) throw new IllegalArgumentException("收支类型不正确");
        return result;
    }
    private long category(long bookId, String type, Object value) {
        if (value == null || str(value).isBlank()) {
            List<Long> other = jdbc.query("SELECT id FROM category WHERE book_id=? AND type=? AND name='其他'", (rs, row) -> rs.getLong(1), bookId, type);
            return other.isEmpty() ? insert("INSERT INTO category(book_id,type,name) VALUES (?,?,'其他')", bookId, type) : other.get(0);
        }
        long categoryId = id(value, "分类");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE id=? AND book_id=? AND type=? AND is_active=TRUE", Integer.class, categoryId, bookId, type);
        if (count == null || count == 0) throw new IllegalArgumentException("分类不存在或已停用");
        return categoryId;
    }
    private Long account(long bookId, Object value) {
        if (value == null || str(value).isBlank()) return null;
        long accountId = id(value, "账户");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM finance_account WHERE id=? AND book_id=? AND is_active=TRUE", Integer.class, accountId, bookId);
        if (count == null || count == 0) throw new IllegalArgumentException("账户不存在或已停用");
        return accountId;
    }

    public List<Map<String, Object>> books(long userId) {
        return jdbc.query("SELECT b.id,b.name,b.owner_user_id,CASE WHEN b.owner_user_id=? THEN 'OWNER' ELSE m.role END role "
                + "FROM account_book b LEFT JOIN book_member m ON m.book_id=b.id AND m.user_id=? WHERE b.owner_user_id=? OR m.user_id=? ORDER BY b.id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "name", rs.getString("name"),
                        "ownerUserId", rs.getLong("owner_user_id"), "role", rs.getString("role")), userId, userId, userId, userId);
    }
    @Transactional
    public long addBook(long userId, Map<String, Object> input) {
        long bookId = insert("INSERT INTO account_book(owner_user_id,name) VALUES (?,?)", userId, text(input.get("name"), 100, "账本名称"));
        jdbc.update("INSERT INTO category(book_id,type,name,icon,sort_order) SELECT ?,type,name,icon,sort_order FROM system_category_template WHERE is_active=TRUE", bookId);
        return bookId;
    }
    public void renameBook(long userId, long bookId, Map<String, Object> input) {
        owner(userId, bookId);
        jdbc.update("UPDATE account_book SET name=? WHERE id=?", text(input.get("name"), 100, "账本名称"), bookId);
    }
    public List<Map<String, Object>> members(long userId, long bookId) {
        access(userId, bookId, false);
        return jdbc.query("SELECT u.id,COALESCE(u.display_name,'记账用户') display_name,'OWNER' role FROM account_book b JOIN app_user u ON u.id=b.owner_user_id WHERE b.id=? "
                + "UNION ALL SELECT u.id,COALESCE(u.display_name,'记账用户'),m.role FROM book_member m JOIN app_user u ON u.id=m.user_id WHERE m.book_id=?",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "displayName", rs.getString("display_name"), "role", rs.getString("role")), bookId, bookId);
    }
    public String invitation(long userId, long bookId, String role) {
        owner(userId, bookId);
        if (!role.equals("EDITOR") && !role.equals("VIEWER")) throw new IllegalArgumentException("成员权限不正确");
        byte[] bytes = new byte[6]; RANDOM.nextBytes(bytes);
        String code = HexFormat.of().formatHex(bytes).toUpperCase();
        jdbc.update("INSERT INTO book_invitation(code_digest,book_id,role,expires_at,created_by_user_id) VALUES (?,?,?,?,?)",
                digest(code), bookId, role, Timestamp.valueOf(LocalDateTime.now(SHANGHAI).plusDays(1)), userId);
        return code;
    }
    @Transactional
    public long join(long userId, String code) {
        if (code == null || !code.matches("[0-9a-fA-F]{12}")) throw new IllegalArgumentException("邀请码格式不正确");
        List<Map<String, Object>> invitations = jdbc.queryForList("SELECT book_id,role FROM book_invitation WHERE code_digest=? AND expires_at>? FOR UPDATE", digest(code.toUpperCase()), now());
        if (invitations.isEmpty()) throw new IllegalArgumentException("邀请码无效或已过期");
        Map<String, Object> invite = invitations.get(0);
        long bookId = ((Number) invite.get("book_id")).longValue();
        Integer own = jdbc.queryForObject("SELECT COUNT(*) FROM account_book WHERE id=? AND owner_user_id=?", Integer.class, bookId, userId);
        if (own != null && own > 0) throw new IllegalArgumentException("不能加入自己创建的账本");
        jdbc.update("INSERT INTO book_member(book_id,user_id,role) VALUES (?,?,?) ON DUPLICATE KEY UPDATE role=VALUES(role)", bookId, userId, invite.get("role"));
        jdbc.update("DELETE FROM book_invitation WHERE code_digest=?", digest(code.toUpperCase()));
        return bookId;
    }
    public void setMember(long userId, long bookId, long memberId, String newRole) {
        owner(userId, bookId);
        if (newRole.equals("REMOVE")) jdbc.update("DELETE FROM book_member WHERE book_id=? AND user_id=?", bookId, memberId);
        else {
            if (!newRole.equals("EDITOR") && !newRole.equals("VIEWER")) throw new IllegalArgumentException("成员权限不正确");
            if (jdbc.update("UPDATE book_member SET role=? WHERE book_id=? AND user_id=?", newRole, bookId, memberId) == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在");
        }
    }

    public List<Map<String, Object>> categories(long userId, long bookId, String selectedType) {
        access(userId, bookId, false);
        String filter = selectedType == null || selectedType.isBlank() ? "" : type(selectedType);
        return jdbc.query("SELECT id,type,name,icon,sort_order,is_active FROM category WHERE book_id=? AND (?='' OR type=?) ORDER BY type,sort_order,id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "type", rs.getString("type"), "name", rs.getString("name"),
                        "icon", rs.getString("icon"), "sortOrder", rs.getInt("sort_order"), "active", rs.getBoolean("is_active")), bookId, filter, filter);
    }
    public long addCategory(long userId, long bookId, Map<String, Object> input) {
        owner(userId, bookId);
        return insert("INSERT INTO category(book_id,type,name,icon,sort_order) VALUES (?,?,?,?,?)", bookId, type(input.get("type")),
                text(input.get("name"), 50, "分类名称"), optional(input.get("icon"), 32, "图标"), intValue(input.get("sortOrder"), 0));
    }
    public void updateCategory(long userId, long bookId, long categoryId, Map<String, Object> input) {
        owner(userId, bookId);
        if (jdbc.update("UPDATE category SET name=?,icon=?,sort_order=?,is_active=? WHERE id=? AND book_id=?",
                text(input.get("name"), 50, "分类名称"), optional(input.get("icon"), 32, "图标"), intValue(input.get("sortOrder"), 0),
                Boolean.TRUE.equals(input.get("active")), categoryId, bookId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分类不存在");
    }
    private static int intValue(Object value, int fallback) {
        if (value == null || str(value).isBlank()) return fallback;
        try { return Integer.parseInt(str(value)); } catch (NumberFormatException error) { throw new IllegalArgumentException("排序数值不正确"); }
    }

    public List<Map<String, Object>> accounts(long userId, long bookId) {
        access(userId, bookId, false);
        return jdbc.query("SELECT a.id,a.name,a.kind,a.opening_balance,a.is_active,"
                + "COALESCE((SELECT SUM(CASE WHEN r.type='INCOME' THEN r.amount ELSE -r.amount END) FROM bookkeeping_record r WHERE r.account_id=a.id AND r.deleted_at IS NULL),0) record_delta,"
                + "COALESCE((SELECT SUM(t.amount) FROM account_transfer t WHERE t.to_account_id=a.id AND t.deleted_at IS NULL),0) transfer_in,"
                + "COALESCE((SELECT SUM(t.amount) FROM account_transfer t WHERE t.from_account_id=a.id AND t.deleted_at IS NULL),0) transfer_out "
                + "FROM finance_account a WHERE a.book_id=? ORDER BY a.id",
                (rs, row) -> {
                    BigDecimal opening = rs.getBigDecimal("opening_balance"), records = rs.getBigDecimal("record_delta");
                    BigDecimal incoming = rs.getBigDecimal("transfer_in"), outgoing = rs.getBigDecimal("transfer_out");
                    boolean liability = rs.getString("kind").equals("LIABILITY");
                    BigDecimal balance = liability ? opening.subtract(records).subtract(incoming).add(outgoing)
                            : opening.add(records).add(incoming).subtract(outgoing);
                    return Map.<String, Object>of("id", rs.getLong("id"), "name", rs.getString("name"), "kind", rs.getString("kind"),
                            "openingBalance", opening.toPlainString(), "balance", balance.toPlainString(), "active", rs.getBoolean("is_active"));
                }, bookId);
    }
    public Map<String, String> accountOverview(long userId, long bookId) {
        BigDecimal assets = BigDecimal.ZERO, liabilities = BigDecimal.ZERO;
        for (Map<String, Object> account : accounts(userId, bookId)) {
            BigDecimal balance = new BigDecimal(account.get("balance").toString());
            if ("LIABILITY".equals(account.get("kind"))) liabilities = liabilities.add(balance);
            else assets = assets.add(balance);
        }
        return Map.of("assets", assets.setScale(2).toPlainString(), "liabilities", liabilities.setScale(2).toPlainString(),
                "netAssets", assets.subtract(liabilities).setScale(2).toPlainString());
    }
    public long addAccount(long userId, long bookId, Map<String, Object> input) {
        owner(userId, bookId);
        String kind = str(input.get("kind"));
        if (!kind.equals("ASSET") && !kind.equals("LIABILITY")) throw new IllegalArgumentException("账户类型不正确");
        return insert("INSERT INTO finance_account(book_id,name,kind,opening_balance) VALUES (?,?,?,?)", bookId,
                text(input.get("name"), 50, "账户名称"), kind, signedAmount(input.get("openingBalance")));
    }
    public void updateAccount(long userId, long bookId, long accountId, Map<String, Object> input) {
        owner(userId, bookId);
        String kind = str(input.get("kind"));
        if (!kind.equals("ASSET") && !kind.equals("LIABILITY")) throw new IllegalArgumentException("账户类型不正确");
        if (jdbc.update("UPDATE finance_account SET name=?,kind=?,opening_balance=?,is_active=? WHERE id=? AND book_id=?",
                text(input.get("name"), 50, "账户名称"), kind, signedAmount(input.get("openingBalance")),
                Boolean.TRUE.equals(input.get("active")), accountId, bookId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在");
    }

    public Map<String, Object> transactions(long userId, long bookId, String from, String to, String search,
            Long categoryId, Long accountId, Long memberId) {
        access(userId, bookId, false);
        StringBuilder where = new StringBuilder(" WHERE r.book_id=? AND r.deleted_at IS NULL");
        List<Object> args = new ArrayList<>(); args.add(bookId);
        if (from != null && !from.isBlank()) { where.append(" AND r.occurred_at>=?"); args.add(Timestamp.valueOf(LocalDate.parse(from).atStartOfDay())); }
        if (to != null && !to.isBlank()) { where.append(" AND r.occurred_at<?"); args.add(Timestamp.valueOf(LocalDate.parse(to).plusDays(1).atStartOfDay())); }
        if (search != null && !search.isBlank()) { where.append(" AND (r.purpose LIKE ? OR r.note LIKE ?)"); args.add("%" + search.trim() + "%"); args.add("%" + search.trim() + "%"); }
        if (categoryId != null) { where.append(" AND r.category_id=?"); args.add(categoryId); }
        if (accountId != null) { where.append(" AND r.account_id=?"); args.add(accountId); }
        if (memberId != null) { where.append(" AND r.created_by_user_id=?"); args.add(memberId); }
        List<Map<String, Object>> records = jdbc.query("SELECT r.id,r.type,r.amount,r.purpose,r.note,r.occurred_at,r.source,r.category_id,r.account_id,r.created_by_user_id,c.name category_name,a.name account_name "
                + "FROM bookkeeping_record r JOIN category c ON c.id=r.category_id LEFT JOIN finance_account a ON a.id=r.account_id"
                + where + " ORDER BY r.occurred_at DESC,r.id DESC LIMIT 200", (rs, row) -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getLong("id")); item.put("type", rs.getString("type")); item.put("amount", rs.getBigDecimal("amount").toPlainString());
                    item.put("purpose", rs.getString("purpose")); item.put("note", rs.getString("note") == null ? "" : rs.getString("note"));
                    item.put("source", rs.getString("source"));
                    item.put("occurredAt", rs.getTimestamp("occurred_at").toLocalDateTime().format(DATETIME));
                    item.put("categoryId", rs.getLong("category_id")); item.put("category", rs.getString("category_name"));
                    item.put("accountId", rs.getObject("account_id") == null ? "" : rs.getLong("account_id"));
                    item.put("account", rs.getString("account_name") == null ? "" : rs.getString("account_name"));
                    item.put("createdBy", rs.getObject("created_by_user_id") == null ? "" : rs.getLong("created_by_user_id"));
                    item.put("canEdit", rs.getObject("created_by_user_id") != null && rs.getLong("created_by_user_id") == userId);
                    return item;
                }, args.toArray());
        StringBuilder transferWhere = new StringBuilder(" WHERE t.book_id=? AND t.deleted_at IS NULL");
        List<Object> transferArgs = new ArrayList<>(); transferArgs.add(bookId);
        if (from != null && !from.isBlank()) { transferWhere.append(" AND t.occurred_at>=?"); transferArgs.add(Timestamp.valueOf(LocalDate.parse(from).atStartOfDay())); }
        if (to != null && !to.isBlank()) { transferWhere.append(" AND t.occurred_at<?"); transferArgs.add(Timestamp.valueOf(LocalDate.parse(to).plusDays(1).atStartOfDay())); }
        if (memberId != null) { transferWhere.append(" AND t.created_by_user_id=?"); transferArgs.add(memberId); }
        if (accountId != null) { transferWhere.append(" AND (t.from_account_id=? OR t.to_account_id=?)"); transferArgs.add(accountId); transferArgs.add(accountId); }
        if (search != null && !search.isBlank()) { transferWhere.append(" AND (f.name LIKE ? OR a.name LIKE ? OR t.note LIKE ?)"); for (int i = 0; i < 3; i++) transferArgs.add("%" + search.trim() + "%"); }
        List<Map<String, Object>> transfers = categoryId == null ? jdbc.query("SELECT t.id,t.amount,t.note,t.occurred_at,t.from_account_id,t.to_account_id,t.created_by_user_id,f.name from_name,a.name to_name "
                + "FROM account_transfer t JOIN finance_account f ON f.id=t.from_account_id JOIN finance_account a ON a.id=t.to_account_id"
                + transferWhere + " ORDER BY t.occurred_at DESC,t.id DESC LIMIT 200", (rs, row) -> Map.<String, Object>of(
                        "id", rs.getLong("id"), "amount", rs.getBigDecimal("amount").toPlainString(),
                        "note", rs.getString("note") == null ? "" : rs.getString("note"),
                        "occurredAt", rs.getTimestamp("occurred_at").toLocalDateTime().format(DATETIME),
                        "fromAccountId", rs.getLong("from_account_id"), "toAccountId", rs.getLong("to_account_id"),
                        "fromName", rs.getString("from_name"), "toName", rs.getString("to_name"),
                        "createdBy", rs.getObject("created_by_user_id") == null ? "" : rs.getLong("created_by_user_id"),
                        "canEdit", rs.getObject("created_by_user_id") != null && rs.getLong("created_by_user_id") == userId), transferArgs.toArray()) : List.of();
        return Map.of("records", records, "transfers", transfers);
    }

    @Transactional
    public long addRecord(long userId, long bookId, Map<String, Object> input) {
        access(userId, bookId, true);
        String type = type(input.get("type"));
        long categoryId = category(bookId, type, input.get("categoryId"));
        Long accountId = account(bookId, input.get("accountId"));
        return insert("INSERT INTO bookkeeping_record(book_id,type,amount,purpose,category_id,account_id,occurred_at,note,source,created_by_user_id) VALUES (?,?,?,?,?,?,?,?,'MINI_PROGRAM',?)",
                bookId, type, amount(input.get("amount")), text(input.get("purpose"), 40, "用途"), categoryId, accountId,
                time(input.get("occurredAt")), optional(input.get("note"), 500, "备注"), userId);
    }
    @Transactional
    public void updateRecord(long userId, long bookId, long recordId, Map<String, Object> input) {
        access(userId, bookId, true);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bookkeeping_record WHERE id=? AND book_id=? AND created_by_user_id=? AND deleted_at IS NULL", Integer.class, recordId, bookId, userId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能修改自己创建的账单");
        String type = type(input.get("type"));
        jdbc.update("UPDATE bookkeeping_record SET type=?,amount=?,purpose=?,category_id=?,account_id=?,occurred_at=?,note=? WHERE id=?",
                type, amount(input.get("amount")), text(input.get("purpose"), 40, "用途"), category(bookId, type, input.get("categoryId")),
                account(bookId, input.get("accountId")), time(input.get("occurredAt")), optional(input.get("note"), 500, "备注"), recordId);
    }
    public void deleteRecord(long userId, long bookId, long recordId) {
        access(userId, bookId, true);
        if (jdbc.update("UPDATE bookkeeping_record SET deleted_at=? WHERE id=? AND book_id=? AND created_by_user_id=? AND deleted_at IS NULL",
                now(), recordId, bookId, userId) == 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能删除自己创建的账单");
    }
    public long addTransfer(long userId, long bookId, Map<String, Object> input) {
        access(userId, bookId, true);
        Long from = account(bookId, input.get("fromAccountId")), to = account(bookId, input.get("toAccountId"));
        if (from == null || to == null || from.equals(to)) throw new IllegalArgumentException("请选择两个不同的账户");
        return insert("INSERT INTO account_transfer(book_id,from_account_id,to_account_id,amount,occurred_at,note,created_by_user_id) VALUES (?,?,?,?,?,?,?)",
                bookId, from, to, amount(input.get("amount")), time(input.get("occurredAt")), optional(input.get("note"), 500, "备注"), userId);
    }
    public void deleteTransfer(long userId, long bookId, long transferId) {
        access(userId, bookId, true);
        if (jdbc.update("UPDATE account_transfer SET deleted_at=? WHERE id=? AND book_id=? AND created_by_user_id=? AND deleted_at IS NULL",
                now(), transferId, bookId, userId) == 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能删除自己创建的转账");
    }

    public List<Map<String, Object>> templates(long userId, long bookId) {
        access(userId, bookId, false);
        return jdbc.query("SELECT id,type,name,amount,purpose,category_id,account_id,note,sort_order FROM quick_template WHERE book_id=? ORDER BY sort_order,id",
                (rs, row) -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", rs.getLong("id")); item.put("type", rs.getString("type")); item.put("name", rs.getString("name"));
                    item.put("amount", rs.getBigDecimal("amount") == null ? "" : rs.getBigDecimal("amount").toPlainString());
                    item.put("purpose", rs.getString("purpose")); item.put("categoryId", rs.getLong("category_id"));
                    item.put("accountId", rs.getObject("account_id") == null ? "" : rs.getLong("account_id"));
                    item.put("note", rs.getString("note") == null ? "" : rs.getString("note")); item.put("sortOrder", rs.getInt("sort_order"));
                    return item;
                }, bookId);
    }
    @Transactional
    public long addTemplate(long userId, long bookId, Map<String, Object> input) {
        access(userId, bookId, true);
        String type = type(input.get("type"));
        return insert("INSERT INTO quick_template(book_id,type,name,amount,purpose,category_id,account_id,note,sort_order) VALUES (?,?,?,?,?,?,?,?,?)",
                bookId, type, text(input.get("name"), 50, "模板名称"), str(input.get("amount")).isBlank() ? null : amount(input.get("amount")),
                text(input.get("purpose"), 40, "用途"), category(bookId, type, input.get("categoryId")), account(bookId, input.get("accountId")),
                optional(input.get("note"), 500, "备注"), intValue(input.get("sortOrder"), 0));
    }
    public void deleteTemplate(long userId, long bookId, long templateId) {
        access(userId, bookId, true);
        if (jdbc.update("DELETE FROM quick_template WHERE id=? AND book_id=?", templateId, bookId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    }

    public Map<String, Object> report(long userId, long bookId, String monthText) {
        access(userId, bookId, false);
        YearMonth month = monthText == null || monthText.isBlank() ? YearMonth.now(SHANGHAI) : YearMonth.parse(monthText);
        Timestamp from = Timestamp.valueOf(month.atDay(1).atStartOfDay()), to = Timestamp.valueOf(month.plusMonths(1).atDay(1).atStartOfDay());
        List<Map<String, Object>> totals = jdbc.query("SELECT type,COALESCE(SUM(amount),0) total,COUNT(*) count FROM bookkeeping_record WHERE book_id=? AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<? GROUP BY type",
                (rs, row) -> Map.<String, Object>of("type", rs.getString("type"), "amount", rs.getBigDecimal("total").toPlainString(), "count", rs.getLong("count")), bookId, from, to);
        List<Map<String, Object>> categories = jdbc.query("SELECT c.name,SUM(r.amount) total FROM bookkeeping_record r JOIN category c ON c.id=r.category_id WHERE r.book_id=? AND r.type='EXPENSE' AND r.deleted_at IS NULL AND r.occurred_at>=? AND r.occurred_at<? GROUP BY c.id,c.name ORDER BY total DESC",
                (rs, row) -> Map.<String, Object>of("name", rs.getString("name"), "amount", rs.getBigDecimal("total").toPlainString()), bookId, from, to);
        return Map.of("month", month.toString(), "totals", totals, "categories", categories);
    }
    public Map<String, Object> summary(long userId, long bookId, String period, String dateText) {
        access(userId, bookId, false);
        LocalDate date = dateText == null || dateText.isBlank() ? LocalDate.now(SHANGHAI) : LocalDate.parse(dateText);
        LocalDate start = switch (period) {
            case "year" -> date.withDayOfYear(1);
            case "month" -> date.withDayOfMonth(1);
            case "day" -> date;
            default -> throw new IllegalArgumentException("统计周期不正确");
        };
        LocalDate end = switch (period) { case "year" -> start.plusYears(1); case "month" -> start.plusMonths(1); default -> start.plusDays(1); };
        List<Map<String, Object>> totals = jdbc.query("SELECT type,COALESCE(SUM(amount),0) total,COUNT(*) count FROM bookkeeping_record "
                + "WHERE book_id=? AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<? GROUP BY type",
                (rs, row) -> Map.<String, Object>of("type", rs.getString("type"), "amount", rs.getBigDecimal("total").toPlainString(), "count", rs.getLong("count")),
                bookId, Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(end.atStartOfDay()));
        return Map.of("period", period, "date", date.toString(), "totals", totals);
    }
    public List<Map<String, Object>> budgetStatus(long userId, long bookId, String monthText) {
        access(userId, bookId, false);
        YearMonth month = monthText == null || monthText.isBlank() ? YearMonth.now(SHANGHAI) : YearMonth.parse(monthText);
        Timestamp start = Timestamp.valueOf(month.atDay(1).atStartOfDay()), end = Timestamp.valueOf(month.plusMonths(1).atDay(1).atStartOfDay());
        return jdbc.query("SELECT b.id,COALESCE(c.name,'月度总预算') category,b.amount,"
                + "COALESCE((SELECT SUM(r.amount) FROM bookkeeping_record r WHERE r.book_id=b.book_id AND r.type='EXPENSE' AND r.deleted_at IS NULL "
                + "AND r.occurred_at>=? AND r.occurred_at<? AND (b.category_id IS NULL OR r.category_id=b.category_id)),0) spent "
                + "FROM budget_rule b LEFT JOIN category c ON c.id=b.category_id WHERE b.book_id=? AND b.month=? ORDER BY b.category_id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "category", rs.getString("category"),
                        "amount", rs.getBigDecimal("amount").toPlainString(), "spent", rs.getBigDecimal("spent").toPlainString()),
                start, end, bookId, Date.valueOf(month.atDay(1)));
    }
    public Map<String, Object> annualReport(long userId, long bookId, int year) {
        access(userId, bookId, false);
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("年份不正确");
        Timestamp start = Timestamp.valueOf(LocalDate.of(year, 1, 1).atStartOfDay());
        Timestamp end = Timestamp.valueOf(LocalDate.of(year + 1, 1, 1).atStartOfDay());
        List<Map<String, Object>> months = jdbc.query("SELECT MONTH(occurred_at) month,type,SUM(amount) total FROM bookkeeping_record "
                + "WHERE book_id=? AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<? GROUP BY MONTH(occurred_at),type ORDER BY month,type",
                (rs, row) -> Map.<String, Object>of("month", rs.getInt("month"), "type", rs.getString("type"),
                        "amount", rs.getBigDecimal("total").toPlainString()), bookId, start, end);
        List<Map<String, Object>> categories = jdbc.query("SELECT c.name,SUM(r.amount) total FROM bookkeeping_record r JOIN category c ON c.id=r.category_id "
                + "WHERE r.book_id=? AND r.type='EXPENSE' AND r.deleted_at IS NULL AND r.occurred_at>=? AND r.occurred_at<? GROUP BY c.id,c.name ORDER BY total DESC",
                (rs, row) -> Map.<String, Object>of("name", rs.getString("name"), "amount", rs.getBigDecimal("total").toPlainString()), bookId, start, end);
        return Map.of("year", year, "months", months, "categories", categories);
    }
    public List<Map<String, Object>> calendar(long userId, long bookId, String monthText) {
        access(userId, bookId, false);
        YearMonth month = YearMonth.parse(monthText);
        return jdbc.query("SELECT DATE(occurred_at) day,type,SUM(amount) total FROM bookkeeping_record WHERE book_id=? AND deleted_at IS NULL "
                + "AND occurred_at>=? AND occurred_at<? GROUP BY DATE(occurred_at),type ORDER BY day,type",
                (rs, row) -> Map.<String, Object>of("date", rs.getDate("day").toLocalDate().toString(),
                        "type", rs.getString("type"), "amount", rs.getBigDecimal("total").toPlainString()),
                bookId, Timestamp.valueOf(month.atDay(1).atStartOfDay()), Timestamp.valueOf(month.plusMonths(1).atDay(1).atStartOfDay()));
    }
    public List<Map<String, Object>> comments(long userId, long bookId, long recordId) {
        access(userId, bookId, false);
        requireRecord(bookId, recordId);
        return jdbc.query("SELECT c.id,c.user_id,COALESCE(u.display_name,'记账用户') display_name,c.body,c.created_at FROM transaction_comment c "
                + "JOIN app_user u ON u.id=c.user_id WHERE c.book_id=? AND c.record_id=? ORDER BY c.created_at,c.id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "userId", rs.getLong("user_id"),
                        "displayName", rs.getString("display_name"), "body", rs.getString("body"), "createdAt", rs.getTimestamp("created_at").toString()),
                bookId, recordId);
    }
    public long addComment(long userId, long bookId, long recordId, Map<String, Object> input) {
        access(userId, bookId, true);
        requireRecord(bookId, recordId);
        return insert("INSERT INTO transaction_comment(book_id,record_id,user_id,body) VALUES (?,?,?,?)",
                bookId, recordId, userId, text(input.get("body"), 500, "留言"));
    }
    private void requireRecord(long bookId, long recordId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM bookkeeping_record WHERE id=? AND book_id=? AND deleted_at IS NULL",
                Integer.class, recordId, bookId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在");
    }
}
