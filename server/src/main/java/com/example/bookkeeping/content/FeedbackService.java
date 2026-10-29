package com.example.bookkeeping.content;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FeedbackService {
    private final JdbcTemplate jdbc;
    public FeedbackService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static String required(Object value, int max, String label) {
        String text = value == null ? "" : value.toString().trim();
        if (text.isBlank() || text.length() > max) throw new IllegalArgumentException(label + "长度不正确");
        return text;
    }
    public List<Map<String, Object>> mine(long userId) {
        return jdbc.query("SELECT id,subject,content,status,admin_response,created_at FROM user_feedback WHERE user_id=? ORDER BY id DESC LIMIT 50",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "subject", rs.getString("subject"),
                        "content", rs.getString("content"), "status", rs.getString("status"),
                        "response", rs.getString("admin_response") == null ? "" : rs.getString("admin_response"),
                        "createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString().replace('T', ' ')), userId);
    }
    public void add(long userId, Map<String, Object> body) {
        jdbc.update("INSERT INTO user_feedback(user_id,subject,content) VALUES (?,?,?)", userId,
                required(body.get("subject"), 80, "反馈标题"), required(body.get("content"), 1000, "反馈内容"));
    }
    public List<Map<String, Object>> all() {
        return jdbc.query("SELECT f.id,f.user_id,COALESCE(u.display_name,'记账用户') display_name,f.subject,f.content,f.status,f.admin_response,f.created_at "
                + "FROM user_feedback f JOIN app_user u ON u.id=f.user_id ORDER BY (f.status='OPEN') DESC,f.id DESC LIMIT 200",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "userId", rs.getLong("user_id"),
                        "userName", rs.getString("display_name"), "subject", rs.getString("subject"), "content", rs.getString("content"),
                        "status", rs.getString("status"), "response", rs.getString("admin_response") == null ? "" : rs.getString("admin_response"),
                        "createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString().replace('T', ' ')));
    }
    public void resolve(long id, String response) {
        if (jdbc.update("UPDATE user_feedback SET status='RESOLVED',admin_response=? WHERE id=?",
                required(response, 1000, "回复"), id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "反馈不存在");
    }
    public List<Map<String, Object>> systemCategories() {
        return jdbc.query("SELECT id,type,name,icon,sort_order,is_active FROM system_category_template ORDER BY type,sort_order,id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "type", rs.getString("type"), "name", rs.getString("name"),
                        "icon", rs.getString("icon"), "sortOrder", rs.getInt("sort_order"), "active", rs.getBoolean("is_active")));
    }
    public void addSystemCategory(Map<String, Object> body) {
        String type = required(body.get("type"), 8, "类型");
        if (!type.equals("EXPENSE") && !type.equals("INCOME")) throw new IllegalArgumentException("分类类型不正确");
        jdbc.update("INSERT INTO system_category_template(type,name,icon,sort_order) VALUES (?,?,?,?)",
                type, required(body.get("name"), 50, "名称"), required(body.get("icon"), 32, "图标"), Integer.parseInt(String.valueOf(body.getOrDefault("sortOrder", 0))));
    }
    public void updateSystemCategory(long id, Map<String, Object> body) {
        if (jdbc.update("UPDATE system_category_template SET name=?,icon=?,sort_order=?,is_active=? WHERE id=?",
                required(body.get("name"), 50, "名称"), required(body.get("icon"), 32, "图标"),
                Integer.parseInt(String.valueOf(body.getOrDefault("sortOrder", 0))), Boolean.TRUE.equals(body.get("active")), id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "系统分类不存在");
    }
}
