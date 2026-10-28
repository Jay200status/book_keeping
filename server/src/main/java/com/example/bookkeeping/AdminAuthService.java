package com.example.bookkeeping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int ITERATIONS = 210_000;
    private final JdbcTemplate jdbc;

    @Value("${app.admin-bootstrap-user}") private String bootstrapUser;
    @Value("${app.admin-bootstrap-password}") private String bootstrapPassword;

    public AdminAuthService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static LocalDateTime now() { return LocalDateTime.now(SHANGHAI); }
    private static Timestamp stamp(LocalDateTime time) { return Timestamp.valueOf(time); }
    private static String digest(String value) {
        try { return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception error) { throw new IllegalStateException(error); }
        finally { spec.clearPassword(); }
    }
    static String hashPassword(String password) {
        byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + HEX.formatHex(salt) + "$" + HEX.formatHex(derive(password, salt, ITERATIONS));
    }
    static boolean verifyPassword(String password, String encoded) {
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 1_000_000) return false;
            return MessageDigest.isEqual(derive(password, HEX.parseHex(parts[2]), iterations), HEX.parseHex(parts[3]));
        } catch (RuntimeException error) { return false; }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (bootstrapUser.isBlank() && bootstrapPassword.isBlank()) return;
        if (bootstrapUser.isBlank() || bootstrapPassword.length() < 12)
            throw new IllegalStateException("ADMIN_BOOTSTRAP_USER 和至少 12 位的 ADMIN_BOOTSTRAP_PASSWORD 必须一起设置");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_user", Integer.class);
        if (count != null && count == 0) {
            jdbc.update("INSERT INTO admin_user(username,password_hash,role) VALUES (?,?,'ADMIN')",
                    bootstrapUser.trim(), hashPassword(bootstrapPassword));
        }
    }

    public Map<String, Object> login(String username, String password, String ip, String userAgent) {
        if (username == null || password == null || username.length() > 64 || password.length() > 1024)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        String cleaned = username.trim();
        String address = ip == null ? "unknown" : ip.substring(0, Math.min(ip.length(), 45));
        String agent = userAgent == null ? "" : userAgent.substring(0, Math.min(userAgent.length(), 255));
        Integer failed = jdbc.queryForObject("SELECT COUNT(*) FROM admin_login_log WHERE username=? AND ip_address=? AND success=FALSE AND created_at>?",
                Integer.class, cleaned, address, stamp(now().minusMinutes(15)));
        if (failed != null && failed >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "登录尝试过多，请稍后再试");
        List<Map<String, Object>> admins = jdbc.queryForList(
                "SELECT id,username,password_hash,role FROM admin_user WHERE username = ? AND is_active = TRUE", cleaned);
        if (admins.isEmpty() || !verifyPassword(password, (String) admins.get(0).get("password_hash"))) {
            jdbc.update("INSERT INTO admin_login_log(username,success,ip_address,user_agent) VALUES (?,FALSE,?,?)", cleaned, address, agent);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        byte[] random = new byte[32]; RANDOM.nextBytes(random);
        String token = HEX.formatHex(random);
        long id = ((Number) admins.get(0).get("id")).longValue();
        jdbc.update("INSERT INTO admin_session(token_digest,admin_user_id,expires_at) VALUES (?,?,?)",
                digest(token), id, stamp(now().plusHours(12)));
        jdbc.update("INSERT INTO admin_login_log(admin_user_id,username,success,ip_address,user_agent) VALUES (?,?,TRUE,?,?)",
                id, cleaned, address, agent);
        return Map.of("token", token, "username", admins.get(0).get("username"), "role", admins.get(0).get("role"));
    }

    public List<Map<String, Object>> admins() {
        return jdbc.query("SELECT id,username,role,is_active,created_at FROM admin_user ORDER BY id",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "username", rs.getString("username"),
                        "role", rs.getString("role"), "active", rs.getBoolean("is_active"),
                        "createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString().replace('T', ' ')));
    }
    public long createAdmin(String username, String password, String role) {
        if (username == null || !username.matches("[A-Za-z0-9_.-]{3,64}")) throw new IllegalArgumentException("管理员账号须为 3–64 位字母、数字或 ._- 字符");
        if (password == null || password.length() < 12) throw new IllegalArgumentException("管理员密码至少 12 位");
        if (!role.equals("ADMIN") && !role.equals("OPERATOR")) throw new IllegalArgumentException("管理员角色不正确");
        jdbc.update("INSERT INTO admin_user(username,password_hash,role) VALUES (?,?,?)", username, hashPassword(password), role);
        return jdbc.queryForObject("SELECT id FROM admin_user WHERE username=?", Long.class, username);
    }
    public void updateAdmin(long actorId, long id, String role, boolean active) {
        if (!role.equals("ADMIN") && !role.equals("OPERATOR")) throw new IllegalArgumentException("管理员角色不正确");
        if (actorId == id && (!role.equals("ADMIN") || !active)) throw new IllegalArgumentException("不能停用或降级当前管理员");
        if (jdbc.update("UPDATE admin_user SET role=?,is_active=? WHERE id=?", role, active, id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "管理员不存在");
    }
    public List<Map<String, Object>> loginLogs() {
        return jdbc.query("SELECT id,username,success,ip_address,user_agent,created_at FROM admin_login_log ORDER BY id DESC LIMIT 100",
                (rs, row) -> Map.<String, Object>of("id", rs.getLong("id"), "username", rs.getString("username"),
                        "success", rs.getBoolean("success"), "ip", rs.getString("ip_address"), "userAgent", rs.getString("user_agent") == null ? "" : rs.getString("user_agent"),
                        "createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString().replace('T', ' ')));
    }

    public Map<String, Object> require(String authorization) {
        if (authorization == null || !authorization.matches("Bearer [a-fA-F0-9]{64}"))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录管理端");
        List<Map<String, Object>> admins = jdbc.queryForList("SELECT a.id,a.username,a.role FROM admin_session s "
                + "JOIN admin_user a ON a.id = s.admin_user_id WHERE s.token_digest = ? AND s.expires_at > ? AND a.is_active = TRUE",
                digest(authorization.substring(7)), stamp(now()));
        if (admins.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录管理端");
        return admins.get(0);
    }

    public void logout(String authorization) {
        require(authorization);
        jdbc.update("DELETE FROM admin_session WHERE token_digest = ?", digest(authorization.substring(7)));
    }
}
