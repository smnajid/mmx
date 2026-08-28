package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmx.order.application.port.out.ExternalIdentityGateway;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.PortfolioNumber;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Adapter over the external identity system — resolves
 * {@code (client LegalEntityCode, client portfolioNumber, hub LegalEntityCode) → hub-side
 * portfolioNumber}. Fail-closed (empty) on errors; the caller transitions the client-side order to
 * {@code Rejected} when resolution fails (no hub round-trip, no hub-side order created).
 *
 * <p>Spec: {@code order-routing} — remote account resolution via ExternalIdentityGateway.
 */
public final class ExternalIdentityGatewayAdapter implements ExternalIdentityGateway {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    public ExternalIdentityGatewayAdapter(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    ExternalIdentityGatewayAdapter(String baseUrl, HttpClient httpClient) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<PortfolioNumber> resolveHubSidePortfolioNumber(
            LegalEntityCode clientLegalEntityCode,
            PortfolioNumber clientPortfolioNumber,
            LegalEntityCode hubLegalEntityCode) {
        try {
            String uri = baseUrl + "/api/identity/resolve"
                    + "?clientLegalEntityCode=" + clientLegalEntityCode.value()
                    + "&clientPortfolioNumber=" + clientPortfolioNumber.value()
                    + "&hubLegalEntityCode=" + hubLegalEntityCode.value();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            ResolveDto dto = JSON.readValue(response.body(), ResolveDto.class);
            if (dto == null || dto.hubPortfolioNumber() == null || dto.hubPortfolioNumber().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PortfolioNumber(dto.hubPortfolioNumber()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResolveDto(String hubPortfolioNumber) {}
}
