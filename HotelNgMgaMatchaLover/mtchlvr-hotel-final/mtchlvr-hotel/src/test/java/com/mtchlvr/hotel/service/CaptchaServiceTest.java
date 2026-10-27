package com.mtchlvr.hotel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {
    @Test
    void rejectsVerificationWhenGoogleSecretIsNotConfigured() {
        CaptchaService service = new CaptchaService("", new ObjectMapper());
        assertFalse(service.isConfigured());
        assertFalse(service.verify("any-token", null));
    }

    @Test
    void rejectsMissingToken() {
        CaptchaService service = new CaptchaService("test-secret", new ObjectMapper());
        assertFalse(service.verify(null, null));
        assertFalse(service.verify("  ", null));
    }
}
