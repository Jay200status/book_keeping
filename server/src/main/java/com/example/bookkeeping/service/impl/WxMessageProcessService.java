package com.example.bookkeeping.service.impl;

import com.example.bookkeeping.mapper.WechatMessageMapper;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
    private final WechatMessageMapper messageMapper;

    public WxMessageProcessService(StringRedisTemplate redisTemplate, DefaultRedisScript<Long> redisScript,
                                   WechatMessageMapper messageMapper) {
        this.redisTemplate = redisTemplate;
        this.redisScript = redisScript;
        this.messageMapper = messageMapper;
    }

    /**
     * 同步处理微信消息
     * @param inMessage
     */
    public void processMessage(WxMpXmlMessage inMessage){
        /*获取key*/
        String LockKey = getLockKey(inMessage);
        String uuid = UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(LockKey, uuid, 30, TimeUnit.SECONDS);
        /*判断消息是否重复*/
        if(Boolean.FALSE.equals(success)){
            log.info("检测到重复消息 已忽略:{}",inMessage.getMsgId());
            return;
        }
        try {
            log.info("获取锁成功:{}",LockKey);
            doBusinessLogin(inMessage);
        }catch (Exception e){
            unLockKey(LockKey,uuid);
            log.error("处理微信消息业务逻辑失败 msg_id:{}，已删除锁", inMessage.getMsgId(), e);
            throw new RuntimeException("处理微信消息失败，等待微信重试", e);
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
     * 保存微信消息。同一条回调重复投递时保留首次入库的数据。
     */
    public void doBusinessLogin(WxMpXmlMessage inMessage) {
        messageMapper.insertIfAbsent(getLockKey(inMessage).getBytes(StandardCharsets.UTF_8), inMessage);
    }

    /**
     * 删除锁
     * @param key
     * @param value
     */
    public void  unLockKey(String key,String value){
        redisTemplate.execute(redisScript, Collections.singletonList(key),value);
    }
}
