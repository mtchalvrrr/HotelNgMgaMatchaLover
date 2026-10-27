package com.mtchlvr.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Server-side verification for Google's reCAPTCHA v2 checkbox. */
@Service
public class CaptchaService {
    private final String secretKey;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().build();

    public CaptchaService(@Value("${google.recaptcha.secret-key:}") String secretKey, ObjectMapper mapper) {
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.mapper = mapper;
    }

    public boolean isConfigured() { return !secretKey.isBlank(); }

    public boolean verify(String token, String remoteIp) {
        if (!isConfigured() || token == null || token.isBlank()) return false;
        try {
            String body = "secret=" + enc(secretKey) + "&response=" + enc(token)
                    + (remoteIp == null || remoteIp.isBlank() ? "" : "&remoteip=" + enc(remoteIp));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://www.google.com/recaptcha/api/siteverify"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return false;
            JsonNode json = mapper.readTree(response.body());
            return json.path("success").asBoolean(false);
        } catch (Exception ex) {
            return false;
        }
    }

    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
