package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RemoteRoutingTransientFailureException;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * CGED leg-A REST client. Posts a {@link RemoteRoutingRequest} to the LODH inbound
 * {@code POST /api/v1/cross-org/routed-orders} endpoint with the {@code X-MMX-CrossOrg-Key} transport
 * credential. On 200 returns accept; on 422 returns reject; on any other status or transport error
 * throws {@link RemoteRoutingTransientFailureException} (the caller's circuit-breaker retries or opens).
 *
 * <p>Spec: {@code order-routing} — cross-org routing transport backbone.
 */
public final class RemoteRoutingGatewayRestAdapter implements RemoteRoutingGateway {

    private static final String CREDENTIAL_HEADER = "X-MMX-CrossOrg-Key";
    private static final ObjectMapper JSON = new ObjectMapper().setSerializationInclusion(
            JsonInclude.Include.NON_NULL);

    private final String baseUrl;
    private final String credentialKey;
    private final HttpClient httpClient;

    public RemoteRoutingGatewayRestAdapter(String baseUrl, String credentialKey) {
        this(baseUrl, credentialKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    RemoteRoutingGatewayRestAdapter(String baseUrl, String credentialKey, HttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.credentialKey = credentialKey;
        this.httpClient = httpClient;
    }

    @Override
    public RemoteRoutingResponse route(RemoteRoutingRequest request) {
        try {
            String json = JSON.writeValueAsString(toRequestBody(request));
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/cross-org/routed-orders"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header(CREDENTIAL_HEADER, credentialKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                return parseAccept(response.body());
            }
            if (status == 422) {
                return new RemoteRoutingResponse.Reject(extractReason(response.body()));
            }
            throw new RemoteRoutingTransientFailureException(
                    "LODH leg-A returned HTTP " + status + ": " + response.body());
        } catch (RemoteRoutingTransientFailureException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RemoteRoutingTransientFailureException(
                    "LODH leg-A transport failure: " + ex.getMessage(), ex);
        }
    }

    private static LegARequestBody toRequestBody(RemoteRoutingRequest req) {
        return new LegARequestBody(
                req.routingId().value().toString(),
                req.portfolioNumber().value(),
                req.institutionCode(),
                req.originatingExternalOrderReference().value(),
                req.currency(),
                req.amount().doubleValue(),
                req.valueDate() != null ? req.valueDate().toString() : null,
                req.orderType().name(),
                req.orderOperation().name(),
                req.tenor() != null ? req.tenor().getCode() : null,
                req.noticePeriod() != null ? req.noticePeriod().getCode() : null,
                req.minimumRate() != null ? req.minimumRate().doubleValue() : null,
                req.sourceContractNumber() != null ? req.sourceContractNumber().value() : null);
    }

    private static RemoteRoutingResponse.Accept parseAccept(String body) throws Exception {
        JsonNode root = JSON.readTree(body);
        String acceptedAt = root.path("acceptedAt").asText();
        return new RemoteRoutingResponse.Accept(Instant.parse(acceptedAt));
    }

    private static String extractReason(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            return root.path("reason").asText("Routing rejected by hub");
        } catch (Exception ex) {
            return "Routing rejected by hub";
        }
    }

    private record LegARequestBody(
            String routingId,
            String portfolioNumber,
            String institutionCode,
            String originatingExternalOrderReference,
            String currency,
            double amount,
            String valueDate,
            String orderType,
            String orderOperation,
            String tenor,
            String noticePeriod,
            Double minimumRate,
            String sourceContractNumber) {}
}
