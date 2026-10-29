package com.example.bookkeeping.finance;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExpenseParserTest {
    @Test void parsesMessageAndOptionalNote() {
        ExpenseParser.Expense lunch = ExpenseParser.parse("午饭 20元");
        assertNotNull(lunch);
        assertEquals("午饭", lunch.purpose());
        assertEquals(new BigDecimal("20.00"), lunch.amount());
        assertEquals("", lunch.note());
        assertEquals("回家", ExpenseParser.parse("地铁 3.50元 回家").note());
    }

    @Test void rejectsInvalidAmounts() {
        assertNull(ExpenseParser.parse("午饭 0元"));
        assertNull(ExpenseParser.parse("午饭 20.999元"));
        assertThrows(IllegalArgumentException.class, () -> ExpenseParser.amount("-1"));
    }
}
