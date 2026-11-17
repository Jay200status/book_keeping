package com.example.bookkeeping.config;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WxMpConfig 配置类
 */
@Configuration
public class WxMpConfig {
    /*微信公众号配置的token*/
    @Value("${wechat.token}")
    private String token;
    /*微信公众号appid*/
    @Value("${wechat.app-id}")
    private String appId;
    /*消息加解密密钥*/
    @Value("${wechat.encoding-aes-key}")
    private String  encodingAesKey;

    @Bean
    public WxMpService wxMpService(){
        /*创建配置存储器*/
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        /*设置配置*/
        config.setAppId(appId);
        config.setToken(token);
        config.setAesKey(encodingAesKey);
        /*创建公众号服务实现类的实例*/
        WxMpServiceImpl wxMpService = new WxMpServiceImpl();
        wxMpService.setWxMpConfigStorage(config);
        return wxMpService;
    }
}
