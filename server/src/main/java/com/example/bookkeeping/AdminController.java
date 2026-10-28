package com.example.bookkeeping;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAuthService auth;
    private final AdminDataService data;
    private final AdminAuditService audit;
    public AdminController(AdminAuthService auth, AdminDataService data, AdminAuditService audit) { this.auth = auth; this.data = data; this.audit = audit; }
    private static String value(Map<String, Object> body, String key) { return body.get(key) == null ? null : body.get(key).toString(); }
    private long admin(String token) {
        Map<String, Object> identity = auth.require(token);
        if (!"ADMIN".equals(identity.get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        return ((Number) identity.get("id")).longValue();
    }

    @PostMapping("/session")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return auth.login(value(body, "username"), value(body, "password"), request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
    @GetMapping("/session")
    public Map<String, Object> me(@RequestHeader(value="Authorization", required=false) String token) { return auth.require(token); }
    @DeleteMapping("/session")
    public ResponseEntity<Void> logout(@RequestHeader(value="Authorization", required=false) String token) { auth.logout(token); return ResponseEntity.noContent().build(); }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestHeader(value="Authorization", required=false) String token) {
        Map<String, Object> identity = auth.require(token);
        Map<String, Object> result = data.dashboard();
        if (!"ADMIN".equals(identity.get("role"))) {
            result.put("purpose", List.of());
            result.put("categories", List.of());
        }
        return result;
    }
    @GetMapping("/anomalies")
    public List<Map<String, Object>> anomalies(@RequestHeader(value="Authorization", required=false) String token) {
        admin(token); return data.anomalies();
    }

    @GetMapping("/users")
    public Map<String, Object> users(@RequestHeader(value="Authorization", required=false) String token,
            @RequestParam(required=false) String search, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        admin(token); return data.users(search, page, size);
    }
    @PatchMapping("/users/{id}")
    public ResponseEntity<Void> updateUser(@RequestHeader(value="Authorization", required=false) String token,
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        data.updateUser(admin(token), id, body); return ResponseEntity.noContent().build();
    }
    @GetMapping("/users/{id}/logins")
    public List<Map<String, Object>> userLogins(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) {
        admin(token); return data.userLogins(id);
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories(@RequestHeader(value="Authorization", required=false) String token,
            @RequestParam long userId, @RequestParam(required=false) Long bookId, @RequestParam(defaultValue="EXPENSE") String type) {
        admin(token); return data.categories(userId, bookId, type);
    }
    @GetMapping("/ledgers")
    public List<Map<String, Object>> ledgers(@RequestHeader(value="Authorization", required=false) String token,
            @RequestParam(required=false) String search, @RequestParam(required=false) Long userId) {
        admin(token); return data.ledgers(search, userId);
    }
    @GetMapping("/bills")
    public Map<String, Object> bills(@RequestHeader(value="Authorization", required=false) String token,
            @RequestParam(required=false) String search, @RequestParam(required=false) String type,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to, @RequestParam(required=false) Long bookId,
            @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
        admin(token); return data.bills(search, type, from, to, bookId, page, size);
    }
    @GetMapping("/bills/export")
    public ResponseEntity<byte[]> exportBills(@RequestHeader(value="Authorization", required=false) String token,
            @RequestParam(required=false) String search, @RequestParam(required=false) String type,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to, @RequestParam(required=false) Long bookId) {
        long id = admin(token);
        byte[] content = data.exportBills(search, type, from, to, bookId).getBytes(StandardCharsets.UTF_8);
        audit.record(id, "EXPORT_BILLS", "BILL", 0, null, Map.of("countBytes", content.length));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bills.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(content);
    }
    @PostMapping("/bills")
    public ResponseEntity<Map<String, Long>> addBill(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", data.addBill(admin(token), body)));
    }
    @PutMapping("/bills/{id}")
    public ResponseEntity<Void> updateBill(@RequestHeader(value="Authorization", required=false) String token,
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        data.updateBill(admin(token), id, body); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/bills/{id}")
    public ResponseEntity<Void> deleteBill(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) {
        data.deleteBill(admin(token), id); return ResponseEntity.noContent().build();
    }
    @GetMapping("/audit-logs")
    public List<Map<String, Object>> auditLogs(@RequestHeader(value="Authorization", required=false) String token) {
        admin(token); return audit.recent();
    }
    @GetMapping("/admin-users")
    public List<Map<String, Object>> admins(@RequestHeader(value="Authorization", required=false) String token) {
        admin(token); return auth.admins();
    }
    @PostMapping("/admin-users")
    public ResponseEntity<Map<String, Long>> addAdmin(@RequestHeader(value="Authorization", required=false) String token,
            @RequestBody Map<String, Object> body) {
        long actor = admin(token);
        long id = auth.createAdmin(value(body, "username"), value(body, "password"), value(body, "role"));
        audit.record(actor, "ADD_ADMIN", "ADMIN", id, null, Map.of("username", value(body, "username"), "role", value(body, "role")));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }
    @PatchMapping("/admin-users/{id}")
    public ResponseEntity<Void> updateAdmin(@RequestHeader(value="Authorization", required=false) String token,
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        long actor = admin(token);
        auth.updateAdmin(actor, id, value(body, "role"), Boolean.TRUE.equals(body.get("active")));
        audit.record(actor, "UPDATE_ADMIN", "ADMIN", id, null, body);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/login-logs")
    public List<Map<String, Object>> loginLogs(@RequestHeader(value="Authorization", required=false) String token) {
        admin(token); return auth.loginLogs();
    }
}
