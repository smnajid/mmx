package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.OpenContractPosition;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * HTTP client for external PositionApi. Fail closed (empty) on errors until the owning team publishes the canonical contract.
 */
public final class PositionApiOpenPositionAdapter implements OpenPositionPort {

    private final String baseUrl;
    private final HttpClient httpClient;

    public PositionApiOpenPositionAdapter(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public Optional<OpenContractPosition> findOpenByContractNumber(ContractNumber contractNumber) {
        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/contracts/" + contractNumber.value()))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            PositionDto dto = PositionApiJson.read(response.body(), PositionDto.class);
            if (dto == null || dto.outstandingAmount() == null || dto.currency() == null) {
                return Optional.empty();
            }
            return Optional.of(
                    new OpenContractPosition(
                            contractNumber, dto.currency(), new BigDecimal(dto.outstandingAmount())));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PositionDto(String contractNumber, String currency, String outstandingAmount) {}
}
