package com.example.bookkeeping.planning;

import com.example.bookkeeping.config.AppConfig;
import com.example.bookkeeping.finance.LedgerService;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class SubscriptionController {
    private final LedgerService ledger;
    private final JdbcTemplate jdbc;
    private final AppConfig config;
    public SubscriptionController(LedgerService ledger, JdbcTemplate jdbc, AppConfig config) {
        this.ledger = ledger; this.jdbc = jdbc; this.config = config;
    }
    private long user(String authorization) {
        Long id = ledger.sessionUser(authorization);
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新打开小程序");
        return id;
    }
    @GetMapping("/api/subscription-config")
    public Map<String, String> config(@RequestHeader(value="Authorization", required=false) String authorization) {
        user(authorization);
        return Map.of("templateId", config.miniReminderTemplateId);
    }
    @PostMapping("/api/subscription-grant")
    public ResponseEntity<Void> grant(@RequestHeader(value="Authorization", required=false) String authorization) {
        long userId = user(authorization);
        if (config.miniReminderTemplateId.isBlank()) throw new IllegalArgumentException("订阅消息模板尚未配置");
        jdbc.update("INSERT INTO subscription_permission(user_id,template_id,credits) VALUES (?,?,1) "
                + "ON DUPLICATE KEY UPDATE credits=LEAST(credits+1,100)", userId, config.miniReminderTemplateId);
        return ResponseEntity.noContent().build();
    }
}
