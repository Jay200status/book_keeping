package com.example.bookkeeping;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlanningScheduler {
    private static final Logger log = LoggerFactory.getLogger(PlanningScheduler.class);
    private final JdbcTemplate jdbc;
    private final WechatClient wechat;
    private final AppConfig config;
    public PlanningScheduler(JdbcTemplate jdbc, WechatClient wechat, AppConfig config) {
        this.jdbc = jdbc; this.wechat = wechat; this.config = config;
    }
    private static Timestamp stamp(LocalDateTime time) { return Timestamp.valueOf(time); }
    static LocalDate scheduledDay(YearMonth month, int day) { return month.atDay(Math.min(day, month.lengthOfMonth())); }
    static boolean dueToGenerate(LocalDate scheduled, LocalDate created, LocalDate today) {
        return !scheduled.isBefore(created) && !scheduled.isAfter(today);
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 900000)
    public void scan() {
        try {
            generateRecurring();
            generateBudgetAlerts();
            generateReminders();
            dispatch();
        } catch (Exception error) { log.error("预算与提醒扫描失败", error); }
    }

    private void generateRecurring() {
        YearMonth current = YearMonth.from(PlanningService.now());
        List<Map<String, Object>> templates = jdbc.queryForList("SELECT id,start_month,end_month,created_at,day_of_month FROM recurring_bill_template WHERE is_active=TRUE");
        for (Map<String, Object> row : templates) {
            long id = ((Number) row.get("id")).longValue();
            YearMonth start = YearMonth.from(((Date) row.get("start_month")).toLocalDate());
            LocalDate createdDate = ((Timestamp) row.get("created_at")).toLocalDateTime().toLocalDate();
            YearMonth created = YearMonth.from(createdDate);
            if (start.isBefore(created)) start = created;
            YearMonth end = row.get("end_month") == null ? current : YearMonth.from(((Date) row.get("end_month")).toLocalDate());
            if (end.isAfter(current)) end = current;
            int day = ((Number) row.get("day_of_month")).intValue();
            for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
                LocalDate scheduled = scheduledDay(month, day);
                if (!dueToGenerate(scheduled, createdDate, PlanningService.now().toLocalDate())) continue;
                jdbc.update("INSERT IGNORE INTO recurring_bill_occurrence(template_id,month,scheduled_on) VALUES (?,?,?)",
                        id, Date.valueOf(month.atDay(1)), Date.valueOf(scheduled));
            }
        }
    }

    private void event(long userId, String key, String title, String content, LocalDateTime trigger, LocalDateTime expiry) {
        LocalDateTime now = PlanningService.now();
        if (now.isBefore(trigger) || !now.isBefore(expiry)) return;
        jdbc.update("INSERT IGNORE INTO notification_event(user_id,event_key,title,content,trigger_at,expires_at) VALUES (?,?,?,?,?,?)",
                userId, key, title, content, stamp(trigger), stamp(expiry));
    }

    private void generateBudgetAlerts() {
        YearMonth month = YearMonth.from(PlanningService.now());
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT b.id,u.id user_id,b.amount,c.name category_name,"
                + "COALESCE((SELECT SUM(r.amount) FROM bookkeeping_record r WHERE r.book_id=b.book_id AND r.type='EXPENSE' AND r.deleted_at IS NULL AND r.occurred_at>=? AND r.occurred_at<? AND (b.category_id IS NULL OR r.category_id=b.category_id)),0) spent "
                + "FROM budget_rule b JOIN account_book a ON a.id=b.book_id JOIN app_user u ON u.id=a.owner_user_id LEFT JOIN category c ON c.id=b.category_id "
                + "WHERE b.month=? AND u.status='ACTIVE'", stamp(start), stamp(end), Date.valueOf(month.atDay(1)));
        for (Map<String, Object> row : rows) {
            BigDecimal spent = (BigDecimal) row.get("spent"), limit = (BigDecimal) row.get("amount");
            if (spent.compareTo(limit) > 0) {
                String name = row.get("category_name") == null ? "本月总支出" : row.get("category_name").toString();
                event(((Number) row.get("user_id")).longValue(), "budget:" + row.get("id") + ":" + month,
                        "预算超支提醒", name + "已超出预算", PlanningService.now(), end);
            }
        }
    }

    private void generateReminders() {
        LocalDateTime now = PlanningService.now();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.id,r.user_id,r.kind,r.title,r.due_at,r.recurrence,r.lead_days FROM reminder_rule r "
                + "JOIN app_user u ON u.id=r.user_id WHERE r.is_active=TRUE AND u.status='ACTIVE'");
        for (Map<String, Object> row : rows) {
            LocalDateTime base = ((Timestamp) row.get("due_at")).toLocalDateTime();
            String recurrence = (String) row.get("recurrence");
            int lead = ((Number) row.get("lead_days")).intValue();
            if (recurrence.equals("ONCE")) remind(row, base, lead);
            if (recurrence.equals("DAILY") && !now.toLocalDate().isBefore(base.toLocalDate()))
                remind(row, now.toLocalDate().atTime(base.toLocalTime()), 0);
            if (recurrence.equals("MONTHLY")) {
                YearMonth current = YearMonth.from(now);
                for (YearMonth month : List.of(current, current.plusMonths(1))) {
                    LocalDateTime due = scheduledDay(month, base.getDayOfMonth()).atTime(base.toLocalTime());
                    if (!due.isBefore(base)) remind(row, due, lead);
                }
            }
        }
    }
    private void remind(Map<String, Object> row, LocalDateTime due, int lead) {
        long userId = ((Number) row.get("user_id")).longValue();
        String title = row.get("title").toString();
        String content = row.get("kind").equals("BOOKKEEPING") ? "记得记录今天的收支" : "账单即将到期，请及时处理";
        event(userId, "reminder:" + row.get("id") + ":" + due.toLocalDate(), title, content,
                due.minusDays(lead), due.plusDays(1));
    }

    private void dispatch() {
        LocalDateTime now = PlanningService.now();
        jdbc.update("UPDATE notification_event SET status='NO_PERMISSION',error_message='订阅授权未取得或已过期' WHERE status='PENDING' AND expires_at<=?", stamp(now));
        if (config.miniReminderTemplateId.isBlank()) return;
        List<Map<String, Object>> events = jdbc.queryForList("SELECT e.id,e.user_id,e.title,e.content,e.trigger_at,w.open_id "
                + "FROM notification_event e JOIN subscription_permission p ON p.user_id=e.user_id AND p.template_id=? AND p.credits>0 "
                + "JOIN wechat_identity w ON w.user_id=e.user_id AND w.app_type='MINI_PROGRAM' AND w.app_id=? "
                + "WHERE e.status='PENDING' AND e.trigger_at<=? AND e.expires_at>? ORDER BY e.id LIMIT 30",
                config.miniReminderTemplateId, config.miniAppId, stamp(now), stamp(now));
        for (Map<String, Object> row : events) {
            long id = ((Number) row.get("id")).longValue(), userId = ((Number) row.get("user_id")).longValue();
            if (jdbc.update("UPDATE notification_event SET status='SENDING' WHERE id=? AND status='PENDING'", id) == 0) continue;
            try {
                WechatClient.SendResult result = wechat.sendMiniSubscription((String) row.get("open_id"),
                        (String) row.get("title"), ((Timestamp) row.get("trigger_at")).toLocalDateTime(), (String) row.get("content"));
                if (result.code() == 0) {
                    jdbc.update("UPDATE subscription_permission SET credits=credits-1 WHERE user_id=? AND template_id=? AND credits>0", userId, config.miniReminderTemplateId);
                    jdbc.update("UPDATE notification_event SET status='SENT',sent_at=? WHERE id=?", stamp(PlanningService.now()), id);
                } else if (result.code() == 43101) {
                    jdbc.update("UPDATE subscription_permission SET credits=0 WHERE user_id=? AND template_id=?", userId, config.miniReminderTemplateId);
                    jdbc.update("UPDATE notification_event SET status='NO_PERMISSION',error_message=? WHERE id=?", "微信拒绝发送订阅消息", id);
                } else {
                    jdbc.update("UPDATE notification_event SET status='FAILED',error_message=? WHERE id=?", "微信错误码 " + result.code(), id);
                }
            } catch (Exception error) {
                log.warn("订阅消息发送失败，事件 ID={}", id, error);
                jdbc.update("UPDATE notification_event SET status='FAILED',error_message=? WHERE id=?", "发送失败", id);
            }
        }
    }
}
