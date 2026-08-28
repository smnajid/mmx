package com.mmx.order.application.service;

import com.mmx.order.application.port.in.AcceptRoutedHubOrderUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.exception.CrossOrgMembershipException;
import com.mmx.order.domain.exception.DuplicateRoutedHubOrderException;
import com.mmx.order.domain.model.DelegatedGrantKey;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.RoutedHubOrderDraft;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Hub-deployment (LODH) leg-A inbound. Validates the originating client's grant against the hub's
 * own reference data; on success creates the hub-side order in {@code RECEIVED} and commits a leg-B
 * {@code ACCEPTED} outbox row in the same transaction; on a grant/currency/tenor validation failure
 * rejects, creating no order and emitting no event. Cross-boundary idempotency: a leg-A retry that
 * collides with the partial unique index is caught and resolved to the already-persisted hub-side
 * order.
 *
 * <p>Spec: {@code order-routing} — remote routed order intake at the hub.
 */
public final class AcceptRoutedHubOrderService implements AcceptRoutedHubOrderUseCase {

    private final LegalEntityCode hubLegalEntityCode;
    private final CrossOrgMembershipPort membershipPort;
    private final DelegatedGrantRepository delegatedGrantRepository;
    private final InstitutionRepository institutionRepository;
    private final OrderRepository orderRepository;
    private final RoutingOutcomeOutbox routingOutcomeOutbox;
    private final Clock clock;

    public AcceptRoutedHubOrderService(
            LegalEntityCode hubLegalEntityCode,
            CrossOrgMembershipPort membershipPort,
            DelegatedGrantRepository delegatedGrantRepository,
            InstitutionRepository institutionRepository,
            OrderRepository orderRepository,
            RoutingOutcomeOutbox routingOutcomeOutbox,
            Clock clock) {
        this.hubLegalEntityCode = Objects.requireNonNull(hubLegalEntityCode, "hubLegalEntityCode");
        this.membershipPort = Objects.requireNonNull(membershipPort, "membershipPort");
        this.delegatedGrantRepository = Objects.requireNonNull(delegatedGrantRepository, "delegatedGrantRepository");
        this.institutionRepository = Objects.requireNonNull(institutionRepository, "institutionRepository");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository");
        this.routingOutcomeOutbox = Objects.requireNonNull(routingOutcomeOutbox, "routingOutcomeOutbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RemoteRoutingResponse accept(
            RemoteRoutingRequest request, LegalEntityCode provenOriginatingLegalEntityCode) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(provenOriginatingLegalEntityCode, "provenOriginatingLegalEntityCode must not be null");

        if (!membershipPort.isRemoteTradingClientOfThisHub(provenOriginatingLegalEntityCode)) {
            throw new CrossOrgMembershipException(provenOriginatingLegalEntityCode);
        }

        Optional<Institution> institution = institutionRepository.findByInstitutionCode(request.institutionCode());
        if (institution.isEmpty()) {
            return reject("Unknown institution: " + request.institutionCode());
        }

        Optional<DelegatedInstitutionGrant> grant =
                delegatedGrantRepository.findByKey(
                        new DelegatedGrantKey(request.institutionCode(), provenOriginatingLegalEntityCode, request.currency()));
        if (grant.isEmpty() || !grant.get().isActive() || !isRequestedTenorOrNoticeCoveredBy(grant.get(), request)) {
            return reject("Delegated grant validation failed for ("
                    + request.institutionCode() + ", " + provenOriginatingLegalEntityCode + ", " + request.currency() + ")");
        }

        MoneyMarketOrder hubOrder =
                MoneyMarketOrder.createHubSideFromRouting(
                        toDraft(request, provenOriginatingLegalEntityCode, institution.get().getDisplayName()),
                        clock.today());
        Instant now = clock.now();
        try {
            MoneyMarketOrder saved = orderRepository.save(hubOrder);
            routingOutcomeOutbox.scheduleAccepted(saved, now);
        } catch (DuplicateRoutedHubOrderException collision) {
            orderRepository
                    .findHubOrderByOriginatingAndRoutingId(provenOriginatingLegalEntityCode, request.routingId())
                    .orElseThrow(() -> collision);
        }
        return new RemoteRoutingResponse.Accept(now);
    }

    private static boolean isRequestedTenorOrNoticeCoveredBy(DelegatedInstitutionGrant grant, RemoteRoutingRequest request) {
        return switch (request.orderType()) {
            case TERM -> request.tenor() != null && grant.getEnabledTenors().contains(request.tenor());
            case ON_CALL -> request.noticePeriod() != null
                    && grant.getEnabledNoticePeriods().contains(request.noticePeriod());
        };
    }

    private RoutedHubOrderDraft toDraft(
            RemoteRoutingRequest request, LegalEntityCode provenOriginatingLegalEntityCode, String counterparty) {
        return new RoutedHubOrderDraft(
                hubLegalEntityCode,
                request.portfolioNumber(),
                request.institutionCode(),
                counterparty,
                request.currency(),
                request.amount(),
                request.valueDate(),
                request.orderType(),
                request.orderOperation(),
                request.tenor(),
                request.noticePeriod(),
                request.minimumRate(),
                request.sourceContractNumber(),
                request.routingId(),
                provenOriginatingLegalEntityCode,
                request.originatingExternalOrderReference());
    }

    private static RemoteRoutingResponse reject(String reason) {
        return new RemoteRoutingResponse.Reject(reason);
    }
}
