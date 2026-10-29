package com.example.bookkeeping.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AdminAuthServiceTest {
    @Test
    void adminPasswordsUseDistinctSaltsAndRejectWrongPassword() {
        String first = AdminAuthService.hashPassword("correct horse battery staple");
        String second = AdminAuthService.hashPassword("correct horse battery staple");
        assertNotEquals(first, second);
        assertTrue(AdminAuthService.verifyPassword("correct horse battery staple", first));
        assertFalse(AdminAuthService.verifyPassword("wrong password", first));
    }
}
