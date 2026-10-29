package com.example.bookkeeping.wechat;

import com.example.bookkeeping.config.AppConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 调用小程序接口
 */
@Component
public class WechatClient {
    private static final Logger log = LoggerFactory.getLogger(WechatClient.class);

    public record Identity(String openId, String unionId) {}

    private final AppConfig config;
    private final RestClient miniClient;
    private final RestClient officialClient;
    private volatile String accessToken;
    private volatile long accessTokenExpires;
    private volatile String miniAccessToken;
    private volatile long miniAccessTokenExpires;
    private final ObjectMapper objectMapper;

    public WechatClient(AppConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory miniFactory = new SimpleClientHttpRequestFactory();
        miniFactory.setConnectTimeout(Duration.ofSeconds(2));
        miniFactory.setReadTimeout(Duration.ofSeconds(4));
        this.miniClient = RestClient.builder().requestFactory(miniFactory).build();
        SimpleClientHttpRequestFactory officialFactory = new SimpleClientHttpRequestFactory();
        officialFactory.setConnectTimeout(Duration.ofMillis(700));
        officialFactory.setReadTimeout(Duration.ofMillis(1100));
        this.officialClient = RestClient.builder().requestFactory(officialFactory).build();
    }

    private static String q(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }

    private JsonNode get(RestClient client, String url) {
        //报错：Could not extract response: no suitable HttpMessageConverter found for response type [class com.fasterxml.jackson.databind.JsonNode] and content type [text/plain]
        //原因：客户端预计收到的响应是application/json 实际收到的是text/plain 没有转换器可以把text/plain转换成JsonNode格式 所以报错

        //先用字符串格式接收原始响应
        String body = client.get()
                .uri(URI.create(url))
                .retrieve()
                .body(String.class);
        if (body == null){
            throw new IllegalStateException("微信接口没有响应");
        }
        //用JsonSon的ObjectMapper转换为JsonNode格式
        JsonNode result = null;
        try {
            result = objectMapper.readTree(body);
        }catch (JsonProcessingException e){
            log.error("解析微信响应失败，原始响应是",e);
            throw new RuntimeException("解析微信响应失败",e);
        }

        if (result == null) throw new IllegalStateException("微信接口没有返回响应");
        int errorCode = result.path("errcode").asInt(0);
        if (errorCode != 0) {
            log.warn("微信接口返回错误码 {}", errorCode);
            throw new IllegalStateException("微信接口返回错误");
        }
        return result;
    }

    public Identity miniIdentity(String code) {
        if (config.miniAppId.isBlank() || config.miniAppSecret.isBlank()) throw new IllegalStateException("尚未配置小程序 AppID/Secret");
        JsonNode result = get(miniClient, "https://api.weixin.qq.com/sns/jscode2session?appid=" + q(config.miniAppId)
                + "&secret=" + q(config.miniAppSecret) + "&js_code=" + q(code) + "&grant_type=authorization_code");
        String openId = result.path("openid").asText("");
        if (openId.isBlank()) throw new IllegalStateException("微信未返回 OpenID");
        String unionId = result.path("unionid").asText("");
        return new Identity(openId, unionId.isBlank() ? null : unionId);
    }

    public String officialUnionId(String openId) {
        if (config.officialAppSecret.isBlank()) return null;
        String token = token();
        JsonNode user = get(officialClient, "https://api.weixin.qq.com/cgi-bin/user/info?access_token=" + q(token)
                + "&openid=" + q(openId) + "&lang=zh_CN");
        String unionId = user.path("unionid").asText("");
        return unionId.isBlank() ? null : unionId;
    }

    private synchronized String token() {
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpires) return accessToken;
        JsonNode response = get(officialClient, "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                + q(config.officialAppId) + "&secret=" + q(config.officialAppSecret));
        accessToken = response.path("access_token").asText("");
        if (accessToken.isBlank()) throw new IllegalStateException("微信未返回 access_token");
        accessTokenExpires = System.currentTimeMillis() + Math.max(60, response.path("expires_in").asLong(7200) - 300) * 1000;
        return accessToken;
    }

    private synchronized String miniToken() {
        if (miniAccessToken != null && System.currentTimeMillis() < miniAccessTokenExpires) return miniAccessToken;
        if (config.miniAppId.isBlank() || config.miniAppSecret.isBlank()) throw new IllegalStateException("尚未配置小程序 AppID/Secret");
        JsonNode response = get(miniClient, "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                + q(config.miniAppId) + "&secret=" + q(config.miniAppSecret));
        miniAccessToken = response.path("access_token").asText("");
        if (miniAccessToken.isBlank()) throw new IllegalStateException("微信未返回小程序 access_token");
        miniAccessTokenExpires = System.currentTimeMillis() + Math.max(60, response.path("expires_in").asLong(7200) - 300) * 1000;
        return miniAccessToken;
    }

    public record SendResult(int code, String message) {}

    public SendResult sendMiniSubscription(String openId, String title, LocalDateTime when, String content) {
        if (config.miniReminderTemplateId.isBlank()) throw new IllegalStateException("尚未配置小程序订阅消息模板");
        Map<String, Object> payload = Map.of(
                "touser", openId,
                "template_id", config.miniReminderTemplateId,
                "page", config.miniReminderPage,
                "data", Map.of(
                        config.miniReminderTitleKey, Map.of("value", title.substring(0, Math.min(title.length(), 20))),
                        config.miniReminderTimeKey, Map.of("value", when.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))),
                        config.miniReminderContentKey, Map.of("value", content.substring(0, Math.min(content.length(), 20)))
                ));
        JsonNode result = miniClient.post().uri(URI.create("https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + q(miniToken())))
                .body(payload).retrieve().body(JsonNode.class);
        if (result == null) throw new IllegalStateException("微信订阅消息接口没有返回结果");
        return new SendResult(result.path("errcode").asInt(-1), result.path("errmsg").asText(""));
    }
}
