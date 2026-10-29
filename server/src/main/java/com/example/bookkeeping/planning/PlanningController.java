package com.example.bookkeeping.planning;

import com.example.bookkeeping.admin.AdminAuthService;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class PlanningController {
    private final AdminAuthService auth;
    private final PlanningService planning;
    public PlanningController(AdminAuthService auth, PlanningService planning) { this.auth = auth; this.planning = planning; }
    private void check(String token) {
        if (!"ADMIN".equals(auth.require(token).get("role")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
    }

    @GetMapping("/budgets")
    public List<Map<String, Object>> budgets(@RequestHeader(value="Authorization", required=false) String token, @RequestParam(required=false) String month) {
        check(token); return planning.budgets(month);
    }
    @PostMapping("/budgets")
    public ResponseEntity<Void> budget(@RequestHeader(value="Authorization", required=false) String token, @RequestBody Map<String, Object> body) {
        check(token); planning.saveBudget(body); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/budgets/{id}")
    public ResponseEntity<Void> deleteBudget(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) {
        check(token); planning.deleteBudget(id); return ResponseEntity.noContent().build();
    }
    @GetMapping("/reminders")
    public List<Map<String, Object>> reminders(@RequestHeader(value="Authorization", required=false) String token) {
        check(token); return planning.reminders();
    }
    @PostMapping("/reminders")
    public ResponseEntity<Void> reminder(@RequestHeader(value="Authorization", required=false) String token, @RequestBody Map<String, Object> body) {
        check(token); planning.addReminder(body); return ResponseEntity.noContent().build();
    }
    @PatchMapping("/reminders/{id}")
    public ResponseEntity<Void> reminderActive(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        check(token); planning.setReminderActive(id, Boolean.TRUE.equals(body.get("active"))); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<Void> deleteReminder(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) {
        check(token); planning.deleteReminder(id); return ResponseEntity.noContent().build();
    }
    @GetMapping("/recurring-bills")
    public List<Map<String, Object>> recurring(@RequestHeader(value="Authorization", required=false) String token) {
        check(token); return planning.recurring();
    }
    @PostMapping("/recurring-bills")
    public ResponseEntity<Void> addRecurring(@RequestHeader(value="Authorization", required=false) String token, @RequestBody Map<String, Object> body) {
        check(token); planning.addRecurring(body); return ResponseEntity.noContent().build();
    }
    @PatchMapping("/recurring-bills/{id}")
    public ResponseEntity<Void> recurringActive(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id, @RequestBody Map<String, Object> body) {
        check(token); planning.setRecurringActive(id, Boolean.TRUE.equals(body.get("active"))); return ResponseEntity.noContent().build();
    }
    @GetMapping("/pending-bills")
    public List<Map<String, Object>> pending(@RequestHeader(value="Authorization", required=false) String token) {
        check(token); return planning.pendingBills();
    }
    @GetMapping("/notifications")
    public List<Map<String, Object>> notifications(@RequestHeader(value="Authorization", required=false) String token) {
        check(token); return planning.notifications();
    }
    @PostMapping("/pending-bills/{id}/approve")
    public ResponseEntity<Void> approve(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) {
        check(token); planning.decidePending(id, true); return ResponseEntity.noContent().build();
    }
    @PostMapping("/pending-bills/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@RequestHeader(value="Authorization", required=false) String token, @PathVariable long id) {
        check(token); planning.decidePending(id, false); return ResponseEntity.noContent().build();
    }
}
