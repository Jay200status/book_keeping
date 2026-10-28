package com.example.bookkeeping;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/finance")
public class MiniFinanceController {
    private final LedgerService ledger;
    private final MiniFinanceService finance;
    public MiniFinanceController(LedgerService ledger, MiniFinanceService finance) { this.ledger = ledger; this.finance = finance; }
    private long user(String token) {
        Long id = ledger.sessionUser(token);
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新打开小程序");
        return id;
    }
    private static ResponseEntity<Void> done() { return ResponseEntity.noContent().build(); }

    @GetMapping("/books")
    public List<Map<String, Object>> books(@RequestHeader(value="Authorization",required=false) String token) { return finance.books(user(token)); }
    @PostMapping("/books")
    public ResponseEntity<Map<String, Long>> addBook(@RequestHeader(value="Authorization",required=false) String token, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", finance.addBook(user(token), body)));
    }
    @PatchMapping("/books/{bookId}")
    public ResponseEntity<Void> renameBook(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId, @RequestBody Map<String, Object> body) {
        finance.renameBook(user(token), bookId, body); return done();
    }
    @GetMapping("/books/{bookId}/members")
    public List<Map<String, Object>> members(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId) {
        return finance.members(user(token), bookId);
    }
    @PostMapping("/books/{bookId}/invitation")
    public Map<String, String> invitation(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId, @RequestBody Map<String, Object> body) {
        return Map.of("code", finance.invitation(user(token), bookId, String.valueOf(body.get("role"))));
    }
    @PostMapping("/join")
    public Map<String, Long> join(@RequestHeader(value="Authorization",required=false) String token, @RequestBody Map<String, Object> body) {
        return Map.of("bookId", finance.join(user(token), String.valueOf(body.get("code"))));
    }
    @PatchMapping("/books/{bookId}/members/{memberId}")
    public ResponseEntity<Void> member(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long memberId, @RequestBody Map<String, Object> body) {
        finance.setMember(user(token), bookId, memberId, String.valueOf(body.get("role"))); return done();
    }

    @GetMapping("/books/{bookId}/categories")
    public List<Map<String, Object>> categories(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestParam(required=false) String type) { return finance.categories(user(token), bookId, type); }
    @PostMapping("/books/{bookId}/categories")
    public ResponseEntity<Map<String, Long>> addCategory(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestBody Map<String, Object> body) { return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", finance.addCategory(user(token), bookId, body))); }
    @PutMapping("/books/{bookId}/categories/{categoryId}")
    public ResponseEntity<Void> category(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long categoryId, @RequestBody Map<String, Object> body) { finance.updateCategory(user(token), bookId, categoryId, body); return done(); }

    @GetMapping("/books/{bookId}/accounts")
    public List<Map<String, Object>> accounts(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId) { return finance.accounts(user(token), bookId); }
    @GetMapping("/books/{bookId}/accounts/overview")
    public Map<String, String> accountOverview(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId) {
        return finance.accountOverview(user(token), bookId);
    }
    @PostMapping("/books/{bookId}/accounts")
    public ResponseEntity<Map<String, Long>> addAccount(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestBody Map<String, Object> body) { return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", finance.addAccount(user(token), bookId, body))); }
    @PutMapping("/books/{bookId}/accounts/{accountId}")
    public ResponseEntity<Void> account(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long accountId, @RequestBody Map<String, Object> body) { finance.updateAccount(user(token), bookId, accountId, body); return done(); }

    @GetMapping("/books/{bookId}/transactions")
    public Map<String, Object> transactions(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to, @RequestParam(required=false) String search,
            @RequestParam(required=false) Long categoryId, @RequestParam(required=false) Long accountId, @RequestParam(required=false) Long memberId) {
        return finance.transactions(user(token), bookId, from, to, search, categoryId, accountId, memberId);
    }
    @PostMapping("/books/{bookId}/records")
    public ResponseEntity<Map<String, Long>> addRecord(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestBody Map<String, Object> body) { return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", finance.addRecord(user(token), bookId, body))); }
    @PutMapping("/books/{bookId}/records/{recordId}")
    public ResponseEntity<Void> record(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long recordId, @RequestBody Map<String, Object> body) { finance.updateRecord(user(token), bookId, recordId, body); return done(); }
    @DeleteMapping("/books/{bookId}/records/{recordId}")
    public ResponseEntity<Void> deleteRecord(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long recordId) { finance.deleteRecord(user(token), bookId, recordId); return done(); }
    @GetMapping("/books/{bookId}/records/{recordId}/comments")
    public List<Map<String, Object>> comments(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable long bookId, @PathVariable long recordId) { return finance.comments(user(token), bookId, recordId); }
    @PostMapping("/books/{bookId}/records/{recordId}/comments")
    public ResponseEntity<Map<String, Long>> addComment(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable long bookId, @PathVariable long recordId, @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", finance.addComment(user(token), bookId, recordId, body)));
    }
    @PostMapping("/books/{bookId}/transfers")
    public ResponseEntity<Map<String, Long>> addTransfer(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestBody Map<String, Object> body) { return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", finance.addTransfer(user(token), bookId, body))); }
    @DeleteMapping("/books/{bookId}/transfers/{transferId}")
    public ResponseEntity<Void> deleteTransfer(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long transferId) { finance.deleteTransfer(user(token), bookId, transferId); return done(); }

    @GetMapping("/books/{bookId}/templates")
    public List<Map<String, Object>> templates(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId) { return finance.templates(user(token), bookId); }
    @PostMapping("/books/{bookId}/templates")
    public ResponseEntity<Map<String, Long>> addTemplate(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestBody Map<String, Object> body) { return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", finance.addTemplate(user(token), bookId, body))); }
    @DeleteMapping("/books/{bookId}/templates/{templateId}")
    public ResponseEntity<Void> deleteTemplate(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @PathVariable long templateId) { finance.deleteTemplate(user(token), bookId, templateId); return done(); }
    @GetMapping("/books/{bookId}/report")
    public Map<String, Object> report(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestParam(required=false) String month) { return finance.report(user(token), bookId, month); }
    @GetMapping("/books/{bookId}/summary")
    public Map<String, Object> summary(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestParam(defaultValue="month") String period, @RequestParam(required=false) String date) {
        return finance.summary(user(token), bookId, period, date);
    }
    @GetMapping("/books/{bookId}/budgets")
    public List<Map<String, Object>> budgets(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestParam(required=false) String month) { return finance.budgetStatus(user(token), bookId, month); }
    @GetMapping("/books/{bookId}/annual-report")
    public Map<String, Object> annualReport(@RequestHeader(value="Authorization",required=false) String token, @PathVariable long bookId,
            @RequestParam int year) { return finance.annualReport(user(token), bookId, year); }
    @GetMapping("/books/{bookId}/calendar")
    public List<Map<String, Object>> calendar(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable long bookId, @RequestParam String month) { return finance.calendar(user(token), bookId, month); }
}
