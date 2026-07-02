package com.mmx.order.domain.policy;

import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Maps a client-side order to hub-side intake fields and originating trace. */
public final class OrderRoutingFieldMappingPolicy {

    private OrderRoutingFieldMappingPolicy() {}

    public static RoutedHubOrderDraft mapToHubSide(
            MoneyMarketOrder clientOrder,
            GlobalAccount globalAccount,
            RoutingId routingId,
            String hubNativeInstitutionCode,
            String hubNativeInstitutionDisplayName) {
        return new RoutedHubOrderDraft(
                globalAccount.hubLegalEntityCode(),
                globalAccount.asPortfolioNumber(),
                hubNativeInstitutionCode,
                hubNativeInstitutionDisplayName,
                clientOrder.getCurrency(),
                clientOrder.getAmount(),
                clientOrder.getValueDate(),
                clientOrder.getOrderType(),
                clientOrder.getOrderOperation(),
                clientOrder.getTenor(),
                clientOrder.getNoticePeriod(),
                clientOrder.getMinimumRate(),
                clientOrder.getSourceContractNumber(),
                routingId,
                clientOrder.getLegalEntityCode(),
                clientOrder.getExternalOrderReference());
    }

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
            com.mmx.order.domain.model.ContractNumber sourceContractNumber,
            RoutingId routingId,
            LegalEntityCode originatingLegalEntityCode,
            ExternalOrderReference originatingExternalOrderReference) {}
}
