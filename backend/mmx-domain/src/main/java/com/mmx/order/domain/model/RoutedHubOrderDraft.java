package com.mmx.order.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input to {@link MoneyMarketOrder#createHubSideFromRouting} for routed intake. */
public record RoutedHubOrderDraft(
        LegalEntityCode hubLegalEntityCode,
        PortfolioNumber portfolioNumber,
        String institutionCode,
        String counterparty,
        String currency,
        BigDecimal amount,
        LocalDate valueDate,
        OrderType orderType,
        OrderOperation orderOperation,
        Tenor tenor,
        NoticePeriod noticePeriod,
        BigDecimal minimumRate,
        ContractNumber sourceContractNumber,
        RoutingId routingId,
        LegalEntityCode originatingLegalEntityCode,
        ExternalOrderReference originatingExternalOrderReference) {}
