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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FeedbackController {
    private final LedgerService ledger;
    private final AdminAuthService auth;
    private final AdminAuditService audit;
    private final FeedbackService feedback;
    public FeedbackController(LedgerService ledger, AdminAuthService auth, AdminAuditService audit, FeedbackService feedback) {
        this.ledger = ledger; this.auth = auth; this.audit = audit; this.feedback = feedback;
    }
    private long user(String token) {
        Long id = ledger.sessionUser(token);
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新打开小程序");
        return id;
    }
    private long admin(String token) {
        Map<String, Object> account = auth.require(token);
        if (!"ADMIN".equals(account.get("role"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
        return ((Number) account.get("id")).longValue();
    }
    @GetMapping("/api/feedback")
    public List<Map<String, Object>> mine(@RequestHeader(value="Authorization",required=false) String token) { return feedback.mine(user(token)); }
    @PostMapping("/api/feedback")
    public ResponseEntity<Void> add(@RequestHeader(value="Authorization",required=false) String token, @RequestBody Map<String, Object> body) {
        feedback.add(user(token), body); return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @GetMapping("/api/admin/feedback")
    public List<Map<String, Object>> all(@RequestHeader(value="Authorization",required=false) String token) { admin(token); return feedback.all(); }
    @PutMapping("/api/admin/feedback/{id}")
    public ResponseEntity<Void> resolve(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        long actor = admin(token); String response = String.valueOf(body.get("response")); feedback.resolve(id, response);
        audit.record(actor, "RESOLVE_FEEDBACK", "FEEDBACK", id, null, Map.of("response", response)); return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/admin/system-categories")
    public List<Map<String, Object>> systemCategories(@RequestHeader(value="Authorization",required=false) String token) {
        admin(token); return feedback.systemCategories();
    }
    @PostMapping("/api/admin/system-categories")
    public ResponseEntity<Void> addSystemCategory(@RequestHeader(value="Authorization",required=false) String token, @RequestBody Map<String, Object> body) {
        long actor = admin(token); feedback.addSystemCategory(body); audit.record(actor, "ADD_SYSTEM_CATEGORY", "CATEGORY", 0, null, body);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @PutMapping("/api/admin/system-categories/{id}")
    public ResponseEntity<Void> updateSystemCategory(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long actor = admin(token); feedback.updateSystemCategory(id, body); audit.record(actor, "UPDATE_SYSTEM_CATEGORY", "CATEGORY", id, null, body);
        return ResponseEntity.noContent().build();
    }
}
