package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.application.exception.HubReferenceDataUnavailableException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Shared HTTP GET helper for the remote-backed reference-data adapters. Sends the
 * {@code X-MMX-CrossOrg-Key} credential and deserialises the JSON array response into domain DTOs.
 * A transport error, timeout, non-200 response or unparsable body is reported as
 * {@link HubReferenceDataUnavailableException}, never as an empty list.
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
                    .timeout(ctx.requestTimeout())
                    .header("X-MMX-CrossOrg-Key", ctx.credentialKey())
                    .GET()
                    .build();

            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new HubReferenceDataUnavailableException(
                        "Hub reference data read " + path + " returned HTTP " + response.statusCode());
            }
            JavaType type = JSON.getTypeFactory().constructParametricType(List.class, elementType);
            return JSON.readValue(response.body(), type);
        } catch (HubReferenceDataUnavailableException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HubReferenceDataUnavailableException("Hub reference data read " + path + " was interrupted", ex);
        } catch (Exception ex) {
            throw new HubReferenceDataUnavailableException("Hub reference data read " + path + " failed", ex);
        }
    }
}
