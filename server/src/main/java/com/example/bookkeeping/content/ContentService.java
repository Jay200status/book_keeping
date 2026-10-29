package com.example.bookkeeping.content;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContentService {
    private final JdbcTemplate jdbc;
    public ContentService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static String value(Object value, int max, String label) {
        String text = value == null ? "" : value.toString().trim();
        if (text.isBlank() || text.length() > max) throw new IllegalArgumentException(label + "长度不正确");
        return text;
    }
    private static String kind(Object value) {
        String text = value(value, 20, "内容类型");
        if (!List.of("BANNER", "ANNOUNCEMENT", "FAQ", "VERSION", "PRIVACY", "TERMS").contains(text))
            throw new IllegalArgumentException("内容类型不正确");
        return text;
    }
    private static String link(Object value) {
        String text = value == null ? "" : value.toString().trim();
        if (!text.isBlank() && (!text.startsWith("pages/") || text.length() > 255))
            throw new IllegalArgumentException("链接只能使用小程序内的页面路径");
        return text;
    }
    public List<Map<String, Object>> publicItems(String selectedKind) {
        String filter = selectedKind == null || selectedKind.isBlank() ? "" : kind(selectedKind);
        return jdbc.query("SELECT id,kind,title,body,link_path,updated_at FROM content_item WHERE is_active=TRUE AND (?='' OR kind=?) ORDER BY sort_order,id DESC LIMIT 100",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "kind", rs.getString("kind"),
                        "title", rs.getString("title"), "body", rs.getString("body"),
                        "linkPath", rs.getString("link_path") == null ? "" : rs.getString("link_path"),
                        "updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString().replace('T', ' ')), filter, filter);
    }
    public List<Map<String, Object>> all() {
        return jdbc.query("SELECT id,kind,title,body,link_path,sort_order,is_active,updated_at FROM content_item ORDER BY kind,sort_order,id DESC LIMIT 300",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "kind", rs.getString("kind"),
                        "title", rs.getString("title"), "body", rs.getString("body"),
                        "linkPath", rs.getString("link_path") == null ? "" : rs.getString("link_path"),
                        "sortOrder", rs.getInt("sort_order"), "active", rs.getBoolean("is_active"),
                        "updatedAt", rs.getTimestamp("updated_at").toLocalDateTime().toString().replace('T', ' ')));
    }
    public long add(Map<String, Object> body) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO content_item(kind,title,body,link_path,sort_order,is_active) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, kind(body.get("kind"))); statement.setString(2, value(body.get("title"), 100, "标题"));
            statement.setString(3, value(body.get("body"), 10000, "正文")); statement.setString(4, link(body.get("linkPath")));
            statement.setInt(5, Integer.parseInt(String.valueOf(body.getOrDefault("sortOrder", 0))));
            statement.setBoolean(6, Boolean.TRUE.equals(body.get("active")));
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }
    public void update(long id, Map<String, Object> body) {
        if (jdbc.update("UPDATE content_item SET kind=?,title=?,body=?,link_path=?,sort_order=?,is_active=? WHERE id=?",
                kind(body.get("kind")), value(body.get("title"), 100, "标题"), value(body.get("body"), 10000, "正文"),
                link(body.get("linkPath")), Integer.parseInt(String.valueOf(body.getOrDefault("sortOrder", 0))),
                Boolean.TRUE.equals(body.get("active")), id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "内容不存在");
    }
}
