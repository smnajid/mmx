package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.TraderId;

import com.mmx.order.application.port.in.OrderPage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrderRepository {

    MoneyMarketOrder save(MoneyMarketOrder order);

    Optional<MoneyMarketOrder> findById(UUID id);

    Optional<MoneyMarketOrder> findByExternalOrderReference(ExternalOrderReference reference);

    Optional<MoneyMarketOrder> findByLegalEntityAndExternalReference(
            LegalEntityCode legalEntityCode, ExternalOrderReference reference);

    Optional<MoneyMarketOrder> findRoutedClientOrderByRoutingId(RoutingId routingId);

    Optional<MoneyMarketOrder> findHubOrderByRoutingId(RoutingId routingId);

    /**
     * Trust-boundary containment (D5): resolves a hub-side order by the composite cross-boundary
     * correlation key {@code (originatingLegalEntityCode, routingId)}. Used by the leg-A idempotent
     * collision path so one originating client can never resolve to another client's order.
     */
    Optional<MoneyMarketOrder> findHubOrderByOriginatingAndRoutingId(
            LegalEntityCode originatingLegalEntityCode, RoutingId routingId);

    List<MoneyMarketOrder> findByStatusAndOrderType(
            LegalEntityCode legalEntityCode, OrderStatus status, OrderType orderType);

    /**
     * Paged RECEIVED orders for a workspace type. When {@code valueDateFrom} and {@code valueDateTo}
     * are present, restricts to {@code valueDate} in that inclusive range; when both are empty, no
     * valueDate filter.
     */
    OrderPage findReceivedPageByOrderType(
            LegalEntityCode legalEntityCode,
            OrderType orderType,
            Optional<LocalDate> valueDateFrom,
            Optional<LocalDate> valueDateTo,
            int page,
            int size);

    List<MoneyMarketOrder> findByAssignedTraderIdAndStatus(
            LegalEntityCode legalEntityCode, TraderId traderId, OrderStatus status);

    Optional<ExecutedSubscriptionContractInfo> findExecutedSubscriptionByContractNumber(
            String contractNumber);

    List<ExecutedSubscriptionContract> findExecutedSubscriptionsByPortfolioAndOrderType(
            String portfolioNumber, OrderType orderType);

    Set<String> findContractNumbersWithNonCancelledRedemption(List<String> contractNumbers);
}
