package com.example.bookkeeping.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AdminAuditService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    private String encode(Object value) {
        if (value == null) return null;
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException error) { throw new IllegalStateException("审计记录序列化失败", error); }
    }
    public void record(long adminId, String action, String targetType, long targetId, Object before, Object after) {
        jdbc.update("INSERT INTO admin_audit_log(admin_user_id,action,target_type,target_id,before_json,after_json) VALUES (?,?,?,?,?,?)",
                adminId, action, targetType, targetId, encode(before), encode(after));
    }
    public List<Map<String, Object>> recent() {
        return jdbc.query("SELECT l.id,a.username,l.action,l.target_type,l.target_id,l.created_at FROM admin_audit_log l "
                + "JOIN admin_user a ON a.id=l.admin_user_id ORDER BY l.id DESC LIMIT 100",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "admin", rs.getString("username"),
                        "action", rs.getString("action"), "targetType", rs.getString("target_type"), "targetId", rs.getLong("target_id"),
                        "createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString().replace('T', ' ')));
    }
}
