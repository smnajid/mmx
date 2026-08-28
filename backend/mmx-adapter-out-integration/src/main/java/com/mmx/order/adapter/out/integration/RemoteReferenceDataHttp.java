package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Shared HTTP GET helper for the remote-backed reference-data adapters. Sends the
 * {@code X-MMX-CrossOrg-Key} credential and deserialises the JSON array response into domain DTOs.
 * Fail-closed (empty list) on transport errors or non-200 responses.
 */
final class RemoteReferenceDataHttp {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private RemoteReferenceDataHttp() {}

    static <T> List<T> getList(
            RemoteReferenceDataContext ctx, String path, Class<T> elementType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ctx.normalizedBaseUrl() + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-MMX-CrossOrg-Key", ctx.credentialKey())
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            JavaType type = JSON.getTypeFactory().constructParametricType(List.class, elementType);
            return JSON.readValue(response.body(), type);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
