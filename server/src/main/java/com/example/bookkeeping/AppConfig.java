package com.example.bookkeeping;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    @Value("${app.mini-app-id}") public String miniAppId;
    @Value("${app.mini-app-secret}") public String miniAppSecret;
    @Value("${app.official-app-id}") public String officialAppId;
    @Value("${app.official-app-secret}") public String officialAppSecret;
    @Value("${app.wechat-token}") public String wechatToken;
    @Value("${app.wechat-encoding-aes-key}") public String wechatEncodingAesKey;
    @Value("${app.bind-secret}") public String bindSecret;
    @Value("${app.mini-reminder-template-id}") public String miniReminderTemplateId;
    @Value("${app.mini-reminder-title-key}") public String miniReminderTitleKey;
    @Value("${app.mini-reminder-time-key}") public String miniReminderTimeKey;
    @Value("${app.mini-reminder-content-key}") public String miniReminderContentKey;
    @Value("${app.mini-reminder-page}") public String miniReminderPage;
}
