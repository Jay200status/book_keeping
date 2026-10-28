package com.example.bookkeeping;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExpenseParser {
    private static final Pattern MESSAGE = Pattern.compile("^(.{1,40}?)\\s+([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:元)?(?:\\s+(.{1,200}))?$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern AMOUNT = Pattern.compile("^(?:0|[1-9][0-9]{0,8})(?:\\.[0-9]{1,2})?$");

    private ExpenseParser() {}

    public record Expense(String purpose, BigDecimal amount, String note) {}

    public static BigDecimal amount(String value) {
        String text = value == null ? "" : value.trim();
        if (!AMOUNT.matcher(text).matches()) throw new IllegalArgumentException("金额格式不正确");
        BigDecimal result = new BigDecimal(text).setScale(2);
        if (result.signum() <= 0) throw new IllegalArgumentException("金额必须大于 0");
        return result;
    }

    public static Expense parse(String message) {
        if (message == null) return null;
        Matcher match = MESSAGE.matcher(message.trim());
        if (!match.matches()) return null;
        try {
            return new Expense(match.group(1).trim(), amount(match.group(2)), match.group(3) == null ? "" : match.group(3).trim());
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
