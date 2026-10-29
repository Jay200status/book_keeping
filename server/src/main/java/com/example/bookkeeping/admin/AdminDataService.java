package com.example.bookkeeping.admin;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.PreparedStatement;
import java.sql.Statement;

@Service
public class AdminDataService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;
    private final AdminAuditService audit;
    public AdminDataService(JdbcTemplate jdbc, AdminAuditService audit) { this.jdbc = jdbc; this.audit = audit; }
    private static Timestamp stamp(LocalDateTime value) { return Timestamp.valueOf(value); }
    private static String money(BigDecimal value) { return value == null ? "0.00" : value.setScale(2).toPlainString(); }
    private static String string(Object value) { return value == null ? "" : value.toString().trim(); }
    private static String keyword(String value) { return "%" + string(value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
    private static int page(Integer value) { return value == null ? 1 : Math.max(1, Math.min(value, 100000)); }
    private static int size(Integer value) { return value == null ? 10 : Math.max(1, Math.min(value, 100)); }
    private static Map<String, Object> bill(ResultSet rs, int row) throws SQLException {
        Map<String, Object> item = new HashMap<>();
        item.put("id", rs.getLong("id")); item.put("userId", rs.getLong("user_id")); item.put("bookId", rs.getLong("book_id"));
        item.put("userName", rs.getString("display_name") == null ? "记账用户" : rs.getString("display_name"));
        item.put("type", rs.getString("type")); item.put("amount", money(rs.getBigDecimal("amount")));
        item.put("purpose", rs.getString("purpose")); item.put("categoryId", rs.getLong("category_id"));
        item.put("category", rs.getString("category_name")); item.put("note", rs.getString("note") == null ? "" : rs.getString("note"));
        item.put("occurredAt", rs.getTimestamp("occurred_at").toLocalDateTime().format(DATETIME));
        item.put("source", rs.getString("source"));
        return item;
    }

    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now(SHANGHAI);
        Timestamp start = stamp(today.atStartOfDay());
        Timestamp end = stamp(today.plusDays(1).atStartOfDay());
        Timestamp monthStart = stamp(today.withDayOfMonth(1).atStartOfDay());
        BigDecimal todaySpend = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM bookkeeping_record WHERE type='EXPENSE' AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<?", BigDecimal.class, start, end);
        BigDecimal monthSpend = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM bookkeeping_record WHERE type='EXPENSE' AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<?", BigDecimal.class, monthStart, end);
        Integer todayCount = jdbc.queryForObject("SELECT COUNT(*) FROM bookkeeping_record WHERE type='EXPENSE' AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<?", Integer.class, start, end);
        Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE status='ACTIVE'", Integer.class);
        List<Map<String, Object>> purpose = jdbc.query("SELECT purpose,SUM(amount) total FROM bookkeeping_record WHERE type='EXPENSE' AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<? GROUP BY purpose ORDER BY total DESC LIMIT 8",
                (rs, row) -> Map.of("name", rs.getString("purpose"), "amount", money(rs.getBigDecimal("total"))), monthStart, end);
        List<Map<String, Object>> categories = jdbc.query("SELECT c.name,SUM(r.amount) total FROM bookkeeping_record r JOIN category c ON c.id=r.category_id WHERE r.type='EXPENSE' AND r.deleted_at IS NULL AND r.occurred_at>=? AND r.occurred_at<? GROUP BY c.id,c.name ORDER BY total DESC LIMIT 8",
                (rs, row) -> Map.of("name", rs.getString("name"), "amount", money(rs.getBigDecimal("total"))), monthStart, end);
        List<Map<String, Object>> daily = jdbc.query("SELECT DATE(occurred_at) day,SUM(amount) total FROM bookkeeping_record WHERE type='EXPENSE' AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<? GROUP BY DATE(occurred_at) ORDER BY day",
                (rs, row) -> Map.of("date", rs.getDate("day").toLocalDate().toString(), "amount", money(rs.getBigDecimal("total"))), stamp(today.minusDays(6).atStartOfDay()), end);
        BigDecimal monthIncome = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM bookkeeping_record WHERE type='INCOME' AND deleted_at IS NULL AND occurred_at>=? AND occurred_at<?", BigDecimal.class, monthStart, end);
        Integer newUsersToday = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE created_at>=? AND created_at<?", Integer.class, start, end);
        Integer activeUsers7 = jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM app_login_log WHERE created_at>=? AND created_at<?", Integer.class,
                stamp(today.minusDays(6).atStartOfDay()), end);
        Integer bookkeepingUsersMonth = jdbc.queryForObject("SELECT COUNT(DISTINCT created_by_user_id) FROM bookkeeping_record WHERE deleted_at IS NULL AND occurred_at>=? AND occurred_at<?", Integer.class, monthStart, end);
        Integer entriesMonth = jdbc.queryForObject("SELECT COUNT(*) FROM bookkeeping_record WHERE deleted_at IS NULL AND occurred_at>=? AND occurred_at<?", Integer.class, monthStart, end);
        Map<String, Object> result = new HashMap<>();
        result.put("todaySpend", money(todaySpend)); result.put("monthSpend", money(monthSpend)); result.put("monthIncome", money(monthIncome));
        result.put("todayCount", todayCount); result.put("activeUsers", userCount); result.put("newUsersToday", newUsersToday);
        result.put("activeUsers7", activeUsers7); result.put("bookkeepingUsersMonth", bookkeepingUsersMonth); result.put("entriesMonth", entriesMonth);
        result.put("purpose", purpose); result.put("categories", categories); result.put("daily", daily);
        return result;
    }

    public List<Map<String, Object>> anomalies() {
        return jdbc.query("SELECT r.id,r.book_id,r.purpose,r.amount,r.occurred_at,u.id user_id,COALESCE(u.display_name,'记账用户') user_name "
                + "FROM bookkeeping_record r JOIN account_book b ON b.id=r.book_id JOIN app_user u ON u.id=b.owner_user_id "
                + "WHERE r.deleted_at IS NULL AND EXISTS (SELECT 1 FROM bookkeeping_record p WHERE p.book_id=r.book_id "
                + "AND p.id<r.id AND p.type=r.type AND p.amount=r.amount AND p.purpose=r.purpose AND p.deleted_at IS NULL "
                + "AND ABS(TIMESTAMPDIFF(SECOND,p.occurred_at,r.occurred_at))<=300) "
                + "ORDER BY r.occurred_at DESC,r.id DESC LIMIT 30",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "bookId", rs.getLong("book_id"),
                        "purpose", rs.getString("purpose"), "amount", money(rs.getBigDecimal("amount")),
                        "occurredAt", rs.getTimestamp("occurred_at").toLocalDateTime().format(DATETIME),
                        "userId", rs.getLong("user_id"), "userName", rs.getString("user_name")));
    }

    public Map<String, Object> users(String search, Integer pageNumber, Integer pageSize) {
        int p = page(pageNumber), s = size(pageSize);
        String where = " FROM app_user u WHERE (? = '' OR u.display_name LIKE ? ESCAPE '\\\\' OR CAST(u.id AS CHAR) LIKE ? ESCAPE '\\\\')";
        String term = string(search);
        Object[] args = {term, keyword(term), keyword(term)};
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args);
        List<Map<String, Object>> items = jdbc.query("SELECT u.id,u.display_name,u.status,u.created_at,COUNT(DISTINCT r.id) bill_count,COALESCE(SUM(CASE WHEN r.type='EXPENSE' THEN r.amount ELSE 0 END),0) spend"
                + " FROM app_user u LEFT JOIN account_book b ON b.owner_user_id=u.id LEFT JOIN bookkeeping_record r ON r.book_id=b.id AND r.deleted_at IS NULL"
                + " WHERE (? = '' OR u.display_name LIKE ? ESCAPE '\\\\' OR CAST(u.id AS CHAR) LIKE ? ESCAPE '\\\\') GROUP BY u.id,u.display_name,u.status,u.created_at ORDER BY u.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> Map.of("id", rs.getLong("id"), "displayName", rs.getString("display_name") == null ? "记账用户" : rs.getString("display_name"),
                        "status", rs.getString("status"), "createdAt", rs.getTimestamp("created_at").toLocalDateTime().format(DATETIME),
                        "billCount", rs.getLong("bill_count"), "spend", money(rs.getBigDecimal("spend"))), term, keyword(term), keyword(term), s, (p - 1) * s);
        return Map.of("items", items, "total", total, "page", p, "size", s);
    }
    public List<Map<String, Object>> userLogins(long userId) {
        return jdbc.query("SELECT id,device_info,ip_address,created_at FROM app_login_log WHERE user_id=? ORDER BY id DESC LIMIT 30",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"),
                        "device", rs.getString("device_info") == null ? "" : rs.getString("device_info"),
                        "ip", maskIp(rs.getString("ip_address")),
                        "createdAt", rs.getTimestamp("created_at").toLocalDateTime().format(DATETIME)), userId);
    }
    public List<Map<String, Object>> ledgers(String search, Long userId) {
        String term = string(search);
        return jdbc.query("SELECT b.id,b.name,u.id user_id,COALESCE(u.display_name,'记账用户') user_name,"
                + "(SELECT COUNT(*) FROM book_member m WHERE m.book_id=b.id) member_count,"
                + "(SELECT COUNT(*) FROM bookkeeping_record r WHERE r.book_id=b.id AND r.deleted_at IS NULL) bill_count "
                + "FROM account_book b JOIN app_user u ON u.id=b.owner_user_id WHERE (?='' OR b.name LIKE ? OR u.display_name LIKE ?) "
                + "AND (? IS NULL OR u.id=?) ORDER BY b.id DESC LIMIT 200",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "name", rs.getString("name"),
                        "userId", rs.getLong("user_id"), "userName", rs.getString("user_name"),
                        "memberCount", rs.getLong("member_count"), "billCount", rs.getLong("bill_count")),
                term, "%" + term + "%", "%" + term + "%", userId, userId);
    }
    private static String maskIp(String address) {
        if (address == null) return "";
        int dot = address.lastIndexOf('.');
        return dot > 0 ? address.substring(0, dot) + ".*" : address.length() > 8 ? address.substring(0, 8) + "…" : address;
    }

    @Transactional
    public void updateUser(long adminId, long id, Map<String, Object> input) {
        List<Map<String, Object>> before = jdbc.queryForList("SELECT display_name,status FROM app_user WHERE id=? FOR UPDATE", id);
        if (before.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        if (input.containsKey("displayName")) {
            String name = string(input.get("displayName"));
            if (name.length() > 30) throw new IllegalArgumentException("昵称不能超过 30 字");
            if (jdbc.update("UPDATE app_user SET display_name=? WHERE id=?", name.isBlank() ? null : name, id) == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (input.containsKey("status")) {
            String status = string(input.get("status"));
            if (!status.equals("ACTIVE") && !status.equals("DISABLED")) throw new IllegalArgumentException("用户状态不正确");
            if (jdbc.update("UPDATE app_user SET status=? WHERE id=?", status, id) == 0)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (!input.containsKey("displayName") && !input.containsKey("status")) throw new IllegalArgumentException("没有可更新的字段");
        audit.record(adminId, "UPDATE_USER", "USER", id, before.get(0), input);
    }

    public Map<String, Object> bills(String search, String type, String from, String to, Long bookId, Integer pageNumber, Integer pageSize) {
        int p = page(pageNumber), s = size(pageSize);
        StringBuilder where = new StringBuilder(" FROM bookkeeping_record r JOIN account_book b ON b.id=r.book_id JOIN app_user u ON u.id=b.owner_user_id JOIN category c ON c.id=r.category_id WHERE r.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (string(search).matches("#[0-9]{1,18}")) {
            where.append(" AND r.id=?"); args.add(Long.parseLong(string(search).substring(1)));
        } else if (!string(search).isBlank()) {
            where.append(" AND (r.purpose LIKE ? ESCAPE '\\\\' OR r.note LIKE ? ESCAPE '\\\\' OR u.display_name LIKE ? ESCAPE '\\\\' OR CAST(r.id AS CHAR) LIKE ? ESCAPE '\\\\')");
            for (int i = 0; i < 4; i++) args.add(keyword(search));
        }
        if (!string(type).isBlank()) { if (!type.equals("EXPENSE") && !type.equals("INCOME")) throw new IllegalArgumentException("收支类型不正确"); where.append(" AND r.type=?"); args.add(type); }
        if (bookId != null) { where.append(" AND r.book_id=?"); args.add(bookId); }
        if (!string(from).isBlank()) { where.append(" AND r.occurred_at>=?"); args.add(stamp(LocalDate.parse(from).atStartOfDay())); }
        if (!string(to).isBlank()) { where.append(" AND r.occurred_at<?"); args.add(stamp(LocalDate.parse(to).plusDays(1).atStartOfDay())); }
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + where, Integer.class, args.toArray());
        args.add(s); args.add((p - 1) * s);
        List<Map<String, Object>> items = jdbc.query("SELECT r.id,r.book_id,u.id user_id,u.display_name,r.type,r.amount,r.purpose,r.category_id,c.name category_name,r.note,r.occurred_at,r.source"
                + where + " ORDER BY r.occurred_at DESC,r.id DESC LIMIT ? OFFSET ?", (rs, row) -> bill(rs, row), args.toArray());
        return Map.of("items", items, "total", total, "page", p, "size", s);
    }
    private static String csv(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    @SuppressWarnings("unchecked")
    public String exportBills(String search, String type, String from, String to, Long bookId) {
        Map<String, Object> first = bills(search, type, from, to, bookId, 1, 100);
        int total = ((Number) first.get("total")).intValue();
        if (total > 10000) throw new IllegalArgumentException("导出结果超过 10000 条，请缩小筛选范围");
        StringBuilder result = new StringBuilder("\uFEFF账单ID,用户ID,用户昵称,类型,金额,用途,分类,发生时间,来源,备注\r\n");
        for (int page = 1; page <= Math.max(1, (total + 99) / 100); page++) {
            Map<String, Object> data = page == 1 ? first : bills(search, type, from, to, bookId, page, 100);
            for (Map<String, Object> row : (List<Map<String, Object>>) data.get("items")) {
                for (String key : List.of("id", "userId", "userName", "type", "amount", "purpose", "category", "occurredAt", "source", "note")) {
                    if (!key.equals("id")) result.append(',');
                    result.append(csv(row.get(key)));
                }
                result.append("\r\n");
            }
        }
        return result.toString();
    }

    public List<Map<String, Object>> categories(long userId, Long bookId, String type) {
        if (!type.equals("EXPENSE") && !type.equals("INCOME")) throw new IllegalArgumentException("收支类型不正确");
        return jdbc.query("SELECT c.id,c.name FROM category c JOIN account_book b ON b.id=c.book_id WHERE b.owner_user_id=? AND (? IS NULL OR b.id=?) AND c.type=? AND c.is_active=TRUE ORDER BY c.sort_order,c.id",
                (rs, row) -> Map.of("id", rs.getLong("id"), "name", rs.getString("name")), userId, bookId, bookId, type);
    }

    private long book(long userId, Object selectedBook) {
        Long bookId = selectedBook == null || string(selectedBook).isBlank() ? null : Long.parseLong(string(selectedBook));
        List<Long> books = jdbc.query("SELECT b.id FROM account_book b JOIN app_user u ON u.id=b.owner_user_id WHERE u.id=? AND u.status='ACTIVE' AND (? IS NULL OR b.id=?) ORDER BY b.id LIMIT 1", (rs, row) -> rs.getLong(1), userId, bookId, bookId);
        if (books.isEmpty()) throw new IllegalArgumentException("用户不存在或已停用");
        return books.get(0);
    }
    private long category(long bookId, String type, Object categoryValue) {
        if (!type.equals("EXPENSE") && !type.equals("INCOME")) throw new IllegalArgumentException("收支类型不正确");
        if (categoryValue != null && !string(categoryValue).isBlank()) {
            long id = Long.parseLong(string(categoryValue));
            List<Long> ids = jdbc.query("SELECT id FROM category WHERE id=? AND book_id=? AND type=? AND is_active=TRUE", (rs, row) -> rs.getLong(1), id, bookId, type);
            if (ids.isEmpty()) throw new IllegalArgumentException("分类不属于该用户或已停用");
            return id;
        }
        List<Long> ids = jdbc.query("SELECT id FROM category WHERE book_id=? AND type=? AND name='其他'", (rs, row) -> rs.getLong(1), bookId, type);
        if (!ids.isEmpty()) return ids.get(0);
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO category(book_id,type,name) VALUES (?,?,'其他')", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, bookId); statement.setString(2, type); return statement;
        }, holder);
        return holder.getKey().longValue();
    }
    private static BigDecimal amount(Object input) {
        try {
            BigDecimal value = new BigDecimal(string(input));
            if (value.signum() <= 0 || value.scale() > 2 || value.precision() > 18) throw new IllegalArgumentException("金额必须是正数，最多两位小数");
            return value;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("金额格式不正确"); }
    }
    private static String purpose(Object input) {
        String value = string(input);
        if (value.isBlank() || value.length() > 40) throw new IllegalArgumentException("用途长度须为 1–40 字");
        return value;
    }
    private static String note(Object input) {
        String value = string(input);
        if (value.length() > 500) throw new IllegalArgumentException("备注不能超过 500 字");
        return value;
    }
    private static LocalDateTime occurredAt(Object input) {
        String value = string(input);
        return value.isBlank() ? LocalDateTime.now(SHANGHAI) : LocalDateTime.parse(value.replace('T', ' '), DATETIME);
    }

    @Transactional
    public long addBill(long adminId, Map<String, Object> input) {
        long userId;
        try { userId = Long.parseLong(string(input.get("userId"))); } catch (NumberFormatException error) { throw new IllegalArgumentException("请选择用户"); }
        long bookId = book(userId, input.get("bookId"));
        String type = string(input.get("type"));
        long categoryId = category(bookId, type, input.get("categoryId"));
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO bookkeeping_record(book_id,type,amount,purpose,category_id,occurred_at,note,source) VALUES (?,?,?,?,?,?,?,'ADMIN')", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, bookId); statement.setString(2, type); statement.setBigDecimal(3, amount(input.get("amount")));
            statement.setString(4, purpose(input.get("purpose"))); statement.setLong(5, categoryId);
            statement.setTimestamp(6, stamp(occurredAt(input.get("occurredAt")))); statement.setString(7, note(input.get("note")));
            return statement;
        }, holder);
        long id = holder.getKey().longValue();
        audit.record(adminId, "ADD_BILL", "BILL", id, null, input);
        return id;
    }

    @Transactional
    public void updateBill(long adminId, long id, Map<String, Object> input) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT book_id,type,amount,purpose,category_id,occurred_at,note FROM bookkeeping_record WHERE id=? AND deleted_at IS NULL FOR UPDATE", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在");
        long bookId = ((Number) rows.get(0).get("book_id")).longValue();
        String type = string(input.get("type"));
        long categoryId = category(bookId, type, input.get("categoryId"));
        jdbc.update("UPDATE bookkeeping_record SET type=?,amount=?,purpose=?,category_id=?,occurred_at=?,note=? WHERE id=? AND deleted_at IS NULL",
                type, amount(input.get("amount")), purpose(input.get("purpose")), categoryId,
                stamp(occurredAt(input.get("occurredAt"))), note(input.get("note")), id);
        audit.record(adminId, "UPDATE_BILL", "BILL", id, rows.get(0), input);
    }

    @Transactional
    public void deleteBill(long adminId, long id) {
        List<Map<String, Object>> before = jdbc.queryForList("SELECT book_id,type,amount,purpose,category_id,occurred_at,note FROM bookkeeping_record WHERE id=? AND deleted_at IS NULL FOR UPDATE", id);
        if (before.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在");
        if (jdbc.update("UPDATE bookkeeping_record SET deleted_at=? WHERE id=? AND deleted_at IS NULL", stamp(LocalDateTime.now(SHANGHAI)), id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在");
        audit.record(adminId, "DELETE_BILL", "BILL", id, before.get(0), null);
    }
}
