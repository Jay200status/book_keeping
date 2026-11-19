package com.example.bookkeeping.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OfficalMessageMapper {
    @Insert("INSERT INTO official_message (official_app_id, wechat_msg_id, sender_open_id, user_id, content, status, parse_result) " +
            "VALUES (#{officialAppId}, #{msgId}, #{openId}, #{userId}, #{content}, #{status}, #{parseResult}) " +
            "ON DUPLICATE KEY UPDATE id = id")
    void insertMessage(@Param("officialAppId") String officialAppId,
                       @Param("msgId") String msgId,
                       @Param("openId") String openId,
                       @Param("userId") Long userId,
                       @Param("content") String content,
                       @Param("status") String status,
                       @Param("parseResult") String parseResult);
}
