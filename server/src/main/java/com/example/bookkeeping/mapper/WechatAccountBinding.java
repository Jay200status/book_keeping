package com.example.bookkeeping.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WechatAccountBinding {

    @Select("""
        SELECT user_id
        FROM wechat_account_binding
        WHERE official_app_id = #{officialAppId}
          AND official_open_id = #{officialOpenId}
          AND mini_program_app_id = #{miniProgramAppId}
        """)
    Long getMiNiUser(@Param("officialAppId")       String officialAppId,
                     @Param("officialOpenId")       String officialOpenId,
                     @Param("miniProgramAppId")     String miniProgramAppId);
}
