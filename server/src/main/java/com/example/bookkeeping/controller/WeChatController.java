package com.example.bookkeeping.controller;

import com.example.bookkeeping.service.impl.WxMessageProcessService;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@RestController
@RequestMapping("/wechat")
public class WeChatController {
    @Autowired
    private WxMessageProcessService wxMessageProcessService;
    private static final Logger log = LoggerFactory.getLogger(WeChatController.class);
    /*WxMpConfig 配置类*/
    private final WxMpService wxMpService;
    public WeChatController(WxMpService wxMpService) {
        this.wxMpService = wxMpService;
    }
    /*微信公众号配置的token*/
    @Value("${wechat.token}")
    private String token;

    /**
     * 校验签名
     * @param signature
     * @param timestamp
     * @param nonce
     * @return
     */
    private boolean verifySignature(String signature,String timestamp,String nonce){
        /*排序 使相同的内容计算出来的哈希值一致*/
        java.lang.String[] arr = {token,timestamp,nonce};
        Arrays.sort(arr);
        String str = String.join("", arr);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            /*计算摘要的哈希值*/
            byte[] bytes = digest.digest(str.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString().equals(signature);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 与微信服务器建立链接
     * @param signature
     * @param timestamp
     * @param nonce
     * @param echostr
     * @return
     */
    @GetMapping
    public String verify(
            @RequestParam String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr
    ) {

        boolean res = verifySignature(signature, timestamp, nonce);
        if (!res) {
            log.info("检查url、token设置是否正确");
            return "验证失败";
        }
        log.info("与微信服务器已建立联系");
        return echostr;
    }

    @PostMapping
    public String receiveMessage(
            @RequestParam("signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam(name = "encrypt_type", required = false) String encryptType,
            @RequestParam(name = "msg_signature", required = false) String msgSignature,
            @RequestBody String xml
    ){
        log.info("进入 POST /wechat，消息体长度={}", xml.length());
        /*校验签名*/
        if (!verifySignature(signature,timestamp,nonce)) {
            log.info("post 基础验签失败");
            return "";
        }
        log.info("用户发送的消息:{}",xml);

        WxMpXmlMessage inMessage;
        try {
            /*如果是安全模式*/
            if ("aes".equals(encryptType)) {
                /*msg_signature已在fromEncryptedXml内部完成校验*/
                /*验签 校验 解析 包含url解析 消息体签名校验 appid校验 返回一个可以直接读取用户消息的WxMpXmlMessage对象*/
                inMessage = WxMpXmlMessage.fromEncryptedXml(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        wxMpService.getWxMpConfigStorage(),
                        timestamp,
                        nonce,
                        msgSignature
                );
            }else {
                inMessage = WxMpXmlMessage.fromXml(xml);
            }
            log.info("解析之后的用户消息:{}",inMessage);
            return wxMessageProcessService.processMessage(inMessage);
        } catch (Exception e) {
            log.error("微信消息安全校验或解密失败，msg_signature={}", msgSignature, e);
            return "success";
        }
    }


}
