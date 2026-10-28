package com.example.bookkeeping;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ApiController {
    private final LedgerService ledger;

    public ApiController(LedgerService ledger) { this.ledger = ledger; }
    private static String value(Map<String, Object> body, String key) {
        Object result = body.get(key);
        return result == null ? null : result.toString();
    }
    private long user(String authorization) {
        Long id = ledger.sessionUser(authorization);
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新打开小程序");
        return id;
    }

    @GetMapping("/health")
    public Map<String, Boolean> health() { return Map.of("ok", true); }

    @PostMapping("/api/session")
    public Map<String, String> session(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ledger.createSession(value(body, "code"), value(body, "deviceInfo"), request.getRemoteAddr());
    }

    @GetMapping("/api/records")
    public Map<String, Object> records(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestParam(required = false) String period,
                                        @RequestParam(required = false) String date) {
        return ledger.records(user(authorization), period, date);
    }

    @GetMapping("/api/records/{id}")
    public Map<String, Object> detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable long id) {
        Map<String, Object> record = ledger.detail(user(authorization), id);
        if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账单不存在");
        return record;
    }

    @PostMapping("/api/records")
    public ResponseEntity<Map<String, Long>> add(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledger.addManual(user(authorization), value(body, "amount"),
                value(body, "purpose"), value(body, "note"), value(body, "occurredAt")));
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ledger.me(user(authorization));
    }

    @PostMapping("/api/me")
    public Map<String, String> updateName(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestBody Map<String, Object> body) {
        return ledger.updateName(user(authorization), value(body, "displayName"));
    }

    @PostMapping("/api/binding-code")
    public Map<String, Object> bindingCode(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ledger.bindingCode(user(authorization));
    }
}
