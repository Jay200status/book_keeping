package com.example.bookkeeping.wechat;

import com.example.bookkeeping.config.AppConfig;
import com.example.bookkeeping.finance.LedgerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;

@RestController
public class WechatController {
    private static final Logger log = LoggerFactory.getLogger(WechatController.class);
    private final AppConfig config;
    private final LedgerService ledger;
    private final WechatClient client;
    private final JdbcTemplate jdbc;

    public WechatController(AppConfig config, LedgerService ledger, WechatClient client, JdbcTemplate jdbc) {
        this.config = config; this.ledger = ledger; this.client = client; this.jdbc = jdbc;
    }

    @GetMapping(value = "/wechat/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@RequestParam String timestamp, @RequestParam String nonce,
                         @RequestParam String signature, @RequestParam String echostr) {
        if (config.wechatToken.isBlank() || !WechatCrypto.validSignature(signature, config.wechatToken, timestamp, nonce))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "微信签名错误");
        return echostr;
    }

    @PostMapping(value = "/wechat/callback", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestParam String timestamp, @RequestParam String nonce,
                          @RequestParam(required = false) String signature,
                          @RequestParam(name = "msg_signature", required = false) String messageSignature,
                          @RequestBody String rawXml) {
        if (config.wechatToken.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "公众号尚未配置");
        Document envelope = WechatCrypto.xml(rawXml);
        String encrypted = WechatCrypto.tag(envelope, "Encrypt");
        Document message;
        if (!encrypted.isBlank()) {
            if (config.wechatEncodingAesKey.isBlank() || !WechatCrypto.validSignature(messageSignature,
                    config.wechatToken, timestamp, nonce, encrypted))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "微信消息签名错误");
            message = WechatCrypto.xml(WechatCrypto.decrypt(encrypted, config.wechatEncodingAesKey, config.officialAppId));
        } else {
            if (!WechatCrypto.validSignature(signature, config.wechatToken, timestamp, nonce))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "微信消息签名错误");
            message = envelope;
        }
        if (!"text".equals(WechatCrypto.tag(message, "MsgType"))) return "success";
        String openId = WechatCrypto.tag(message, "FromUserName");
        String msgId = WechatCrypto.tag(message, "MsgId");
        String content = WechatCrypto.tag(message, "Content");
        String unionId = null;
        try { unionId = client.officialUnionId(openId); }
        catch (Exception error) { log.warn("公众号 UnionID 暂时不可用，等待绑定码关联: {}", error.getMessage()); }
        try { ledger.officialMessage(msgId, openId, content, unionId); }
        catch (DuplicateKeyException error) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM official_message WHERE official_app_id = ? AND wechat_msg_id = ?",
                    Integer.class, config.officialAppId, msgId);
            if (count == null || count == 0) throw error;
        }
        return "success";
    }
}
