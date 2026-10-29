package com.example.bookkeeping.content;

import com.example.bookkeeping.admin.AdminAuditService;
import com.example.bookkeeping.admin.AdminAuthService;
import com.example.bookkeeping.finance.LedgerService;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ContentController {
    private final LedgerService ledger;
    private final AdminAuthService auth;
    private final AdminAuditService audit;
    private final ContentService content;
    public ContentController(LedgerService ledger, AdminAuthService auth, AdminAuditService audit, ContentService content) {
        this.ledger = ledger; this.auth = auth; this.audit = audit; this.content = content;
    }
    private void user(String token) { if (ledger.sessionUser(token) == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新打开小程序"); }
    private long admin(String token) {
        Map<String, Object> account = auth.require(token);
        if (!"ADMIN".equals(account.get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        return ((Number) account.get("id")).longValue();
    }
    @GetMapping("/api/content")
    public List<Map<String, Object>> publicItems(@RequestHeader(value="Authorization",required=false) String token,
            @RequestParam(required=false) String kind) { user(token); return content.publicItems(kind); }
    @GetMapping("/api/admin/content")
    public List<Map<String, Object>> all(@RequestHeader(value="Authorization",required=false) String token) { admin(token); return content.all(); }
    @PostMapping("/api/admin/content")
    public ResponseEntity<Map<String, Long>> add(@RequestHeader(value="Authorization",required=false) String token, @RequestBody Map<String, Object> body) {
        long actor = admin(token), id = content.add(body); audit.record(actor, "ADD_CONTENT", "CONTENT", id, null, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }
    @PutMapping("/api/admin/content/{id}")
    public ResponseEntity<Void> update(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        long actor = admin(token); content.update(id, body); audit.record(actor, "UPDATE_CONTENT", "CONTENT", id, null, body);
        return ResponseEntity.noContent().build();
    }
}
