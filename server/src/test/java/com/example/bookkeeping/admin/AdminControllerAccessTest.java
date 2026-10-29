package com.example.bookkeeping.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AdminControllerAccessTest {
    @Test
    void operatorCannotReadIndividualBills() {
        AdminAuthService auth = mock(AdminAuthService.class);
        AdminDataService data = mock(AdminDataService.class);
        when(auth.require("operator-token")).thenReturn(Map.of("id", 2L, "role", "OPERATOR"));
        AdminController controller = new AdminController(auth, data, mock(AdminAuditService.class));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.bills("operator-token", null, null, null, null, null, null, null));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(data);
    }
}
