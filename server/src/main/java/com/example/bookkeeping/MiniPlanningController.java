package com.example.bookkeeping;

import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/finance")
public class MiniPlanningController {
    private final LedgerService ledger;
    private final MiniFinanceService finance;
    private final PlanningService planning;
    public MiniPlanningController(LedgerService ledger, MiniFinanceService finance, PlanningService planning) {
        this.ledger = ledger; this.finance = finance; this.planning = planning;
    }
    private long user(String token) {
        Long id = ledger.sessionUser(token);
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新打开小程序");
        return id;
    }
    private Map<String, Object> ownBody(long userId, long bookId, Map<String, Object> input) {
        finance.owner(userId, bookId);
        Map<String, Object> body = new HashMap<>(input); body.put("userId", userId); body.put("bookId", bookId);
        return body;
    }
    @PostMapping("/books/{bookId}/budgets")
    public ResponseEntity<Void> budget(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestBody Map<String, Object> body) {
        planning.saveBudget(ownBody(user(token), bookId, body)); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/books/{bookId}/budgets/{id}")
    public ResponseEntity<Void> deleteBudget(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable long bookId, @PathVariable long id) {
        finance.owner(user(token), bookId); planning.deleteBudget(id, bookId); return ResponseEntity.noContent().build();
    }
    @GetMapping("/reminders")
    public List<Map<String, Object>> reminders(@RequestHeader(value="Authorization",required=false) String token) { return planning.remindersForUser(user(token)); }
    @PostMapping("/reminders")
    public ResponseEntity<Void> reminder(@RequestHeader(value="Authorization",required=false) String token, @RequestBody Map<String, Object> input) {
        long id = user(token); Map<String, Object> body = new HashMap<>(input); body.put("userId", id);
        planning.addReminder(body); return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @PatchMapping("/reminders/{id}")
    public ResponseEntity<Void> reminderActive(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long id,
            @RequestBody Map<String, Object> body) {
        planning.setReminderActiveForUser(user(token), id, Boolean.TRUE.equals(body.get("active"))); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/reminders/{id}")
    public ResponseEntity<Void> deleteReminder(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long id) {
        planning.deleteReminderForUser(user(token), id); return ResponseEntity.noContent().build();
    }
    @GetMapping("/books/{bookId}/recurring-bills")
    public List<Map<String, Object>> recurring(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId) {
        finance.owner(user(token), bookId); return planning.recurringForBook(bookId);
    }
    @PostMapping("/books/{bookId}/recurring-bills")
    public ResponseEntity<Void> addRecurring(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestBody Map<String, Object> body) {
        planning.addRecurring(ownBody(user(token), bookId, body)); return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @PatchMapping("/books/{bookId}/recurring-bills/{id}")
    public ResponseEntity<Void> recurringActive(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        finance.owner(user(token), bookId); planning.setRecurringActiveForBook(id, bookId, Boolean.TRUE.equals(body.get("active")));
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/books/{bookId}/pending-bills")
    public List<Map<String, Object>> pending(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId) {
        finance.owner(user(token), bookId); return planning.pendingBillsForBook(bookId);
    }
    @PostMapping("/books/{bookId}/pending-bills/{id}/approve")
    public ResponseEntity<Void> approve(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId, @PathVariable long id) {
        finance.owner(user(token), bookId); planning.decidePendingForBook(id, bookId, true); return ResponseEntity.noContent().build();
    }
    @PostMapping("/books/{bookId}/pending-bills/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId, @PathVariable long id) {
        finance.owner(user(token), bookId); planning.decidePendingForBook(id, bookId, false); return ResponseEntity.noContent().build();
    }
}
