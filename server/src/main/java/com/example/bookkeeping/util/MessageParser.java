package com.example.bookkeeping.util;

import lombok.Data;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 格式处理器
 * 处理格式：早餐 15
 */
public class MessageParser {
    // 匹配：任意非空白字符 + 空格(或多个空格) + 数字(可带小数)
    private static final Pattern EXPENSE_PATTERN = Pattern.compile("^([^\\s]+)\\s+(\\d+(?:\\.\\d{1,2})?)$");

    public static ParsedExpense parse(String content){
        /*如果内容为空直接返回*/
        if (content == null) return null;
        /*用正则判断格式*/
        Matcher matcher = EXPENSE_PATTERN.matcher(content.trim());
        if(matcher.find()){
            /*创建花销内容类 填入内容*/
            ParsedExpense expense = new ParsedExpense();
            expense.setPurpose(matcher.group(1));
            expense.setAmount(new BigDecimal(matcher.group(2)));
            return expense;
        }
        return null;
    }

    /*花销内容类*/
    @Data
    public static class ParsedExpense {
        private String purpose;
        private BigDecimal amount;
    }
}
