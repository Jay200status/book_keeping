package com.example.bookkeeping.planning;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanningService {
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;
    public PlanningService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    static LocalDateTime now() { return LocalDateTime.now(SHANGHAI); }
    private static String str(Object value) { return value == null ? "" : value.toString().trim(); }
    private static long number(Object value, String label) {
        try { long result = Long.parseLong(str(value)); if (result < 1) throw new NumberFormatException(); return result; }
        catch (NumberFormatException error) { throw new IllegalArgumentException("请选择有效的" + label); }
    }
    private static BigDecimal amount(Object value) {
        try {
            BigDecimal result = new BigDecimal(str(value));
            if (result.signum() <= 0 || result.scale() > 2 || result.precision() > 18) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) { throw new IllegalArgumentException("金额必须为正数，最多两位小数"); }
    }
    private static String limit(Object value, int max, String label) {
        String result = str(value);
        if (result.isBlank() || result.length() > max) throw new IllegalArgumentException(label + "长度不正确");
        return result;
    }
    private long book(long userId, Object selectedBook) {
        Long bookId = selectedBook == null || str(selectedBook).isBlank() ? null : number(selectedBook, "账本");
        List<Long> rows = jdbc.query("SELECT b.id FROM account_book b JOIN app_user u ON u.id=b.owner_user_id WHERE u.id=? AND u.status='ACTIVE' AND (? IS NULL OR b.id=?) ORDER BY b.id LIMIT 1", (rs, row) -> rs.getLong(1), userId, bookId, bookId);
        if (rows.isEmpty()) throw new IllegalArgumentException("用户不存在或已停用");
        return rows.get(0);
    }
    private Long category(long bookId, Object categoryValue, String type) {
        if (categoryValue == null || str(categoryValue).isBlank()) return null;
        long id = number(categoryValue, "分类");
        List<Long> rows = jdbc.query("SELECT id FROM category WHERE id=? AND book_id=? AND type=? AND is_active=TRUE", (rs, row) -> rs.getLong(1), id, bookId, type);
        if (rows.isEmpty()) throw new IllegalArgumentException("分类不属于该用户或已停用");
        return id;
    }

    public List<Map<String, Object>> budgets(String month) {
        LocalDate start = month == null || month.isBlank() ? YearMonth.now(SHANGHAI).atDay(1) : YearMonth.parse(month).atDay(1);
        LocalDate end = start.plusMonths(1);
        return jdbc.query("SELECT b.id,b.book_id bookId,u.id userId,COALESCE(u.display_name,'记账用户') userName,b.month,b.category_id categoryId,COALESCE(c.name,'月度总预算') category,b.amount,"
                + "COALESCE((SELECT SUM(r.amount) FROM bookkeeping_record r WHERE r.book_id=b.book_id AND r.type='EXPENSE' AND r.deleted_at IS NULL AND r.occurred_at>=? AND r.occurred_at<? AND (b.category_id IS NULL OR r.category_id=b.category_id)),0) spent "
                + "FROM budget_rule b JOIN account_book a ON a.id=b.book_id JOIN app_user u ON u.id=a.owner_user_id LEFT JOIN category c ON c.id=b.category_id WHERE b.month=? ORDER BY u.id,b.category_id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "bookId", rs.getLong("bookId"), "userId", rs.getLong("userId"), "userName", rs.getString("userName"),
                        "month", rs.getDate("month").toLocalDate().toString().substring(0, 7), "categoryId", rs.getObject("categoryId") == null ? "" : rs.getLong("categoryId"),
                        "category", rs.getString("category"), "amount", rs.getBigDecimal("amount").toPlainString(), "spent", rs.getBigDecimal("spent").toPlainString()),
                Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(end.atStartOfDay()), Date.valueOf(start));
    }
    @Transactional
    public void saveBudget(Map<String, Object> input) {
        long bookId = book(number(input.get("userId"), "用户"), input.get("bookId"));
        LocalDate month = YearMonth.parse(limit(input.get("month"), 7, "月份")).atDay(1);
        Long categoryId = category(bookId, input.get("categoryId"), "EXPENSE");
        jdbc.update("INSERT INTO budget_rule(book_id,month,category_id,amount) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE amount=VALUES(amount)",
                bookId, Date.valueOf(month), categoryId, amount(input.get("amount")));
    }
    public void deleteBudget(long id) {
        if (jdbc.update("DELETE FROM budget_rule WHERE id=?", id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预算不存在");
    }
    public void deleteBudget(long id, long bookId) {
        if (jdbc.update("DELETE FROM budget_rule WHERE id=? AND book_id=?", id, bookId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预算不存在");
    }

    public List<Map<String, Object>> reminders() {
        return jdbc.query("SELECT r.id,r.user_id userId,COALESCE(u.display_name,'记账用户') userName,r.kind,r.title,r.due_at,r.recurrence,r.lead_days leadDays,r.is_active active "
                + "FROM reminder_rule r JOIN app_user u ON u.id=r.user_id ORDER BY r.is_active DESC,r.id DESC",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "userId", rs.getLong("userId"), "userName", rs.getString("userName"),
                        "kind", rs.getString("kind"), "title", rs.getString("title"), "dueAt", rs.getTimestamp("due_at").toLocalDateTime().format(DATETIME),
                        "recurrence", rs.getString("recurrence"), "leadDays", rs.getInt("leadDays"), "active", rs.getBoolean("active")));
    }
    public void addReminder(Map<String, Object> input) {
        long userId = number(input.get("userId"), "用户"); book(userId, null);
        String kind = str(input.get("kind")), recurrence = str(input.get("recurrence"));
        if (!kind.equals("BOOKKEEPING") && !kind.equals("DUE")) throw new IllegalArgumentException("提醒类型不正确");
        if (!recurrence.equals("ONCE") && !recurrence.equals("DAILY") && !recurrence.equals("MONTHLY")) throw new IllegalArgumentException("重复周期不正确");
        int lead;
        try { lead = Integer.parseInt(str(input.get("leadDays"))); } catch (NumberFormatException error) { throw new IllegalArgumentException("提前天数不正确"); }
        if (lead < 0 || lead > 30) throw new IllegalArgumentException("提前天数须为 0–30");
        LocalDateTime dueAt = LocalDateTime.parse(limit(input.get("dueAt"), 19, "提醒时间").replace('T', ' '),
                str(input.get("dueAt")).length() == 16 ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") : DATETIME);
        jdbc.update("INSERT INTO reminder_rule(user_id,kind,title,due_at,recurrence,lead_days) VALUES (?,?,?,?,?,?)",
                userId, kind, limit(input.get("title"), 80, "提醒标题"), Timestamp.valueOf(dueAt), recurrence, lead);
    }
    public void setReminderActive(long id, boolean active) {
        if (jdbc.update("UPDATE reminder_rule SET is_active=? WHERE id=?", active, id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "提醒不存在");
    }
    public List<Map<String, Object>> remindersForUser(long userId) {
        return reminders().stream().filter(item -> ((Number) item.get("userId")).longValue() == userId).toList();
    }
    public void setReminderActiveForUser(long userId, long id, boolean active) {
        if (jdbc.update("UPDATE reminder_rule SET is_active=? WHERE id=? AND user_id=?", active, id, userId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "提醒不存在");
    }
    public void deleteReminderForUser(long userId, long id) {
        if (jdbc.update("DELETE FROM reminder_rule WHERE id=? AND user_id=?", id, userId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "提醒不存在");
    }
    public void deleteReminder(long id) {
        if (jdbc.update("DELETE FROM reminder_rule WHERE id=?", id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "提醒不存在");
    }

    public List<Map<String, Object>> recurring() {
        return jdbc.query("SELECT t.id,t.book_id bookId,u.id userId,COALESCE(u.display_name,'记账用户') userName,t.type,t.amount,t.purpose,c.name category,t.day_of_month dayOfMonth,t.start_month startMonth,t.end_month endMonth,t.is_active active "
                + "FROM recurring_bill_template t JOIN account_book b ON b.id=t.book_id JOIN app_user u ON u.id=b.owner_user_id JOIN category c ON c.id=t.category_id ORDER BY t.id DESC",
                (rs, row) -> {
                    java.util.Map<String, Object> item = new java.util.HashMap<>();
                    item.put("id", rs.getLong("id")); item.put("bookId", rs.getLong("bookId")); item.put("userId", rs.getLong("userId")); item.put("userName", rs.getString("userName"));
                    item.put("type", rs.getString("type")); item.put("amount", rs.getBigDecimal("amount").toPlainString());
                    item.put("purpose", rs.getString("purpose")); item.put("category", rs.getString("category"));
                    item.put("dayOfMonth", rs.getInt("dayOfMonth")); item.put("startMonth", rs.getDate("startMonth").toLocalDate().toString().substring(0, 7));
                    item.put("endMonth", rs.getDate("endMonth") == null ? "" : rs.getDate("endMonth").toLocalDate().toString().substring(0, 7));
                    item.put("active", rs.getBoolean("active")); return item;
                });
    }
    public List<Map<String, Object>> recurringForBook(long bookId) {
        return recurring().stream().filter(item -> ((Number) item.get("bookId")).longValue() == bookId).toList();
    }
    @Transactional
    public void addRecurring(Map<String, Object> input) {
        long bookId = book(number(input.get("userId"), "用户"), input.get("bookId"));
        String type = str(input.get("type"));
        if (!type.equals("EXPENSE") && !type.equals("INCOME")) throw new IllegalArgumentException("收支类型不正确");
        Long categoryId = category(bookId, input.get("categoryId"), type);
        if (categoryId == null) {
            List<Long> ids = jdbc.query("SELECT id FROM category WHERE book_id=? AND type=? AND name='其他'", (rs, row) -> rs.getLong(1), bookId, type);
            if (ids.isEmpty()) {
                GeneratedKeyHolder holder = new GeneratedKeyHolder();
                jdbc.update(connection -> {
                    PreparedStatement statement = connection.prepareStatement("INSERT INTO category(book_id,type,name) VALUES (?,?,'其他')", Statement.RETURN_GENERATED_KEYS);
                    statement.setLong(1, bookId); statement.setString(2, type); return statement;
                }, holder);
                categoryId = holder.getKey().longValue();
            } else categoryId = ids.get(0);
        }
        int day;
        try { day = Integer.parseInt(str(input.get("dayOfMonth"))); } catch (NumberFormatException error) { throw new IllegalArgumentException("每月日期不正确"); }
        if (day < 1 || day > 31) throw new IllegalArgumentException("每月日期须为 1–31");
        LocalDate start = YearMonth.parse(limit(input.get("startMonth"), 7, "开始月份")).atDay(1);
        String endText = str(input.get("endMonth"));
        LocalDate end = endText.isBlank() ? null : YearMonth.parse(endText).atDay(1);
        if (end != null && end.isBefore(start)) throw new IllegalArgumentException("结束月份不能早于开始月份");
        String note = str(input.get("note"));
        if (note.length() > 500) throw new IllegalArgumentException("备注不能超过 500 字");
        jdbc.update("INSERT INTO recurring_bill_template(book_id,type,amount,purpose,category_id,note,day_of_month,start_month,end_month) VALUES (?,?,?,?,?,?,?,?,?)",
                bookId, type, amount(input.get("amount")), limit(input.get("purpose"), 40, "用途"), categoryId,
                note, day, Date.valueOf(start), end == null ? null : Date.valueOf(end));
    }
    public void setRecurringActive(long id, boolean active) {
        if (jdbc.update("UPDATE recurring_bill_template SET is_active=? WHERE id=?", active, id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "周期账单不存在");
    }
    public void setRecurringActiveForBook(long id, long bookId, boolean active) {
        if (jdbc.update("UPDATE recurring_bill_template SET is_active=? WHERE id=? AND book_id=?", active, id, bookId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "周期账单不存在");
    }
    public List<Map<String, Object>> pendingBills() {
        return jdbc.query("SELECT o.id,t.id templateId,u.id userId,COALESCE(u.display_name,'记账用户') userName,t.type,t.amount,t.purpose,o.scheduled_on scheduledOn "
                + "FROM recurring_bill_occurrence o JOIN recurring_bill_template t ON t.id=o.template_id JOIN account_book b ON b.id=t.book_id JOIN app_user u ON u.id=b.owner_user_id WHERE o.status='PENDING' ORDER BY o.scheduled_on,o.id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "templateId", rs.getLong("templateId"), "userId", rs.getLong("userId"),
                        "userName", rs.getString("userName"), "type", rs.getString("type"), "amount", rs.getBigDecimal("amount").toPlainString(),
                        "purpose", rs.getString("purpose"), "scheduledOn", rs.getDate("scheduledOn").toLocalDate().toString()));
    }
    public List<Map<String, Object>> pendingBillsForBook(long bookId) {
        return jdbc.query("SELECT o.id,t.id templateId,t.type,t.amount,t.purpose,o.scheduled_on scheduledOn FROM recurring_bill_occurrence o "
                + "JOIN recurring_bill_template t ON t.id=o.template_id WHERE t.book_id=? AND o.status='PENDING' ORDER BY o.scheduled_on,o.id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "templateId", rs.getLong("templateId"),
                        "type", rs.getString("type"), "amount", rs.getBigDecimal("amount").toPlainString(),
                        "purpose", rs.getString("purpose"), "scheduledOn", rs.getDate("scheduledOn").toLocalDate().toString()), bookId);
    }
    @Transactional
    public void decidePendingForBook(long id, long bookId, boolean approve) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM recurring_bill_occurrence o JOIN recurring_bill_template t ON t.id=o.template_id WHERE o.id=? AND t.book_id=?", Integer.class, id, bookId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待确认账单不存在");
        decidePending(id, approve);
    }
    public List<Map<String, Object>> notifications() {
        return jdbc.query("SELECT e.id,e.title,e.content,e.status,e.error_message,e.trigger_at,u.id userId,COALESCE(u.display_name,'记账用户') userName "
                + "FROM notification_event e JOIN app_user u ON u.id=e.user_id ORDER BY e.id DESC LIMIT 50",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "title", rs.getString("title"), "content", rs.getString("content"),
                        "status", rs.getString("status"), "error", rs.getString("error_message") == null ? "" : rs.getString("error_message"),
                        "triggerAt", rs.getTimestamp("trigger_at").toLocalDateTime().format(DATETIME), "userId", rs.getLong("userId"), "userName", rs.getString("userName")));
    }
    @Transactional
    public void decidePending(long id, boolean approve) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.id,o.scheduled_on,t.book_id,t.type,t.amount,t.purpose,t.category_id,t.note FROM recurring_bill_occurrence o "
                + "JOIN recurring_bill_template t ON t.id=o.template_id WHERE o.id=? AND o.status='PENDING' FOR UPDATE", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待确认账单不存在");
        if (!approve) { jdbc.update("UPDATE recurring_bill_occurrence SET status='DISMISSED' WHERE id=?", id); return; }
        Map<String, Object> row = rows.get(0);
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO bookkeeping_record(book_id,type,amount,purpose,category_id,occurred_at,note,source) VALUES (?,?,?,?,?,?,?,'ADMIN')", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ((Number) row.get("book_id")).longValue()); statement.setString(2, (String) row.get("type"));
            statement.setBigDecimal(3, (BigDecimal) row.get("amount")); statement.setString(4, (String) row.get("purpose"));
            statement.setLong(5, ((Number) row.get("category_id")).longValue());
            statement.setTimestamp(6, Timestamp.valueOf(((Date) row.get("scheduled_on")).toLocalDate().atTime(9, 0)));
            statement.setString(7, (String) row.get("note")); return statement;
        }, holder);
        jdbc.update("UPDATE recurring_bill_occurrence SET status='POSTED',record_id=? WHERE id=?", holder.getKey().longValue(), id);
    }
}
