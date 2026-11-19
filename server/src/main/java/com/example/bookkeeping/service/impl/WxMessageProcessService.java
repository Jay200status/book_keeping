package com.example.bookkeeping.service.impl;

import com.example.bookkeeping.mapper.OfficalMessageMapper;
import com.example.bookkeeping.mapper.WechatAccountBinding;
import com.example.bookkeeping.mapper.WechatMessageMapper;
import com.example.bookkeeping.util.MessageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 处理微信消息
 */
@Service
public class WxMessageProcessService {
    /*为当前类创建一个专属的日志记录器*/
    private static final Logger log = LoggerFactory.getLogger(WxMessageProcessService.class);
    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> redisScript;
    private final WechatAccountBinding wechatAccountBinding;
    private final WechatMessageMapper messageMapper;
    private final String officialAppId;
    private final WxMpService wxMpService;
    private final OfficalMessageMapper officalMessageMapper;
    private final ObjectMapper objectMapper;

    // 新增的构造方法参数
    @Value("${wechat.mini-program-app-id}")
    private String miniProgramAppId;

    public WxMessageProcessService(StringRedisTemplate redisTemplate, DefaultRedisScript<Long> redisScript,
                                   WechatMessageMapper messageMapper, @Value("${wechat.app-id}") String officialAppId,
                                   WechatAccountBinding wechatAccountBinding,
                                   WxMpService wxMpService,
                                   OfficalMessageMapper officalMessageMapper,
                                   ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.redisScript = redisScript;
        this.messageMapper = messageMapper;
        this.wechatAccountBinding = wechatAccountBinding;
        this.officialAppId = officialAppId;
        this.wxMpService = wxMpService;
        this.officalMessageMapper = officalMessageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步处理微信消息
     * @param inMessage
     */
    public String processMessage(WxMpXmlMessage inMessage){
        /*获取key*/
        String LockKey = "wx:official:" + officialAppId + ":" + getLockKey(inMessage);
        String uuid = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LockKey, uuid, 5, TimeUnit.SECONDS);
        /*判断消息是否重复*/
        if(!Boolean.TRUE.equals(success)){
            log.info("检测到重复消息 已忽略:{}",inMessage.getMsgId());
            return null;
        }
        try {
            log.info("获取锁成功:{}",LockKey);
            return doBusinessLogin(inMessage);
        }catch (Exception e){
            log.error("处理微信消息业务逻辑失败 msg_id:{}，已删除锁", inMessage.getMsgId(), e);
            throw new RuntimeException("处理微信消息失败，等待微信重试", e);
        }finally {
            unLockKey(LockKey,uuid);
        }
    }

    /**
     * 检查并生成防重key
     * @param inMessage
     * @return
     */
    public String getLockKey(WxMpXmlMessage inMessage){
        String key;
        if(inMessage.getMsgId() != null){
            /*如果是普通消息*/
            key = "wx:msg:lock:" + inMessage.getMsgId();
        }else {
            /*如果是关注 取关等消息*/
            key = "wx:event:lock:" + inMessage.getToUser() + ":" + inMessage.getFromUser()
                    + ":" + inMessage.getCreateTime()
                    + ":" + inMessage.getEvent() + ":" + inMessage.getEventKey();
        }
        return key;
    }

    /**
     * 删除锁
     * @param key
     * @param value
     */
    public void unLockKey(String key,String value){
        log.info("准备执行解锁脚本, key: {}, value: {}", key, value);
        redisTemplate.execute(redisScript, Collections.singletonList(key),value);
    }

    /**
     * 保存微信消息 同一条回调重复投递时保留首次入库的数据
     */
    public String doBusinessLogin(WxMpXmlMessage inMessage) {
        /*如果不是文字消息直接返回*/
        if (!"text".equals(inMessage.getMsgType())) {
            return bindTextReply(inMessage,"格式错误 请按 [用途 金额] 发送（如：早餐 15）");
        }
        /*绑定状态*/
        String status;
        /*查询用户是否绑定小程序*/
        Long userId = wechatAccountBinding.getMiNiUser(officialAppId,inMessage.getFromUser(),miniProgramAppId);
        /*构造花销内容类*/
        HashMap<String, Object> hashMap = new HashMap<>();
        MessageParser.ParsedExpense expense = MessageParser.parse(inMessage.getContent());
        if(expense != null){
            hashMap.put("purpose",expense.getPurpose());
            hashMap.put("amount",expense.getAmount());
            status = (userId == null) ? "NEEDS_BINDING" : "RECEIVED";
        }else {
            status = "FAILED";
        }

        /*无论用户是否绑定小程序 都记录原始消息*/
        String msgId = inMessage.getMsgId() != null ? inMessage.getMsgId().toString() : String.valueOf(System.currentTimeMillis());
        String openId = inMessage.getFromUser();
        String content = inMessage.getContent();
        String parseResult = toJson(hashMap);
        officalMessageMapper.insertMessage(officialAppId,msgId,openId,userId,content,status,parseResult);
        if(userId != null){
            if(expense != null){
                return bindTextReply(inMessage,"已成功记录:" + expense.getPurpose() + "金额" + expense.getAmount());
            }else {
                return bindTextReply(inMessage,"格式错误 请按 [用途 金额] 发送（如：早餐 15）");
            }
        }else {
            /*未绑定 生成ticket 并引导用户进入小程序*/
            String ticket = UUID.randomUUID().toString().replace("-", "");
            String ticketKey = "wx:ticket" + UUID.randomUUID();
            HashMap<String, String> ticketValue = new HashMap<>();
            ticketValue.put("officalAppId",officialAppId);
            ticketValue.put("officalOpenId", inMessage.getOpenId());
            ticketValue.put("miniProgramAppId",miniProgramAppId);
            /*设置十分钟有效期 用户可能点进公众号没那么快*/
            redisTemplate.opsForValue().setIfAbsent(ticketKey, toJson(ticketValue), 10, TimeUnit.MINUTES);
            /*向指定公众号用户发送小程序链接*/
            String bindH5Url = "https://prerequisite-columns-utils-champion.trycloudflare.com/bind.html";
            String replyContent = "首次记录需先进入小程序 请先点击" + bindH5Url + "?ticket=" + ticket + "进入小程序";
            if (expense == null) {
                replyContent = "\n另外，格式错误，请按 [用途 金额] 发送（如：早餐 15）。" + replyContent;
            }
            return bindTextReply(inMessage,replyContent);
        }
    }

    /*提示用户已入账*/
    private String bindTextReply(WxMpXmlMessage inMessage, String content) {
        return String.format("<xml>" +
                "<ToUserName><![CDATA[%s]]></ToUserName>" +
                "<FromUserName><![CDATA[%s]]></FromUserName>" +
                "<CreateTime>%d</CreateTime>" +
                "<MsgType><![CDATA[text]]></MsgType>" +
                "<Content><![CDATA[%s]]></Content>" +
                "</xml>",inMessage.getFromUser(),inMessage.getToUser(),System.currentTimeMillis() / 1000,content);
    }

    /*将对象转换为json*/
    private String toJson(Object obj){
        try {
            return objectMapper.writeValueAsString(obj);
        }catch (Exception e){
            log.info("Json序列化异常",e);
            return "{}";
        }
    }
}
