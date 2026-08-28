package com.mmx.order.application.service;

import com.mmx.order.application.port.out.ExecutionHandoffRoutingContext;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;

import java.time.Instant;
import java.util.Objects;

/**
 * Locality-aware hub-side routed-order outcome propagation (design D6). For a <strong>local</strong>
 * pair (the originating client's organisation matches the hub deployment's organisation) it
 * delegates to the existing synchronous in-process {@link RoutedOrderOutcomePropagation}, which
 * loads the client-side order in the same DB and transitions it atomically. For a
 * <strong>remote</strong> pair (the originating client is in a different organisation) it bypasses
 * the client lookup entirely — the hub cannot see the client's order table — and instead schedules a
 * leg-B terminal-outcome outbox row in the same transaction as the hub-side terminal transition;
 * the client catches up asynchronously via {@code ApplyRemoteOrderOutcomeUseCase}.
 *
 * <p>Spec: {@code order-routing} — hub-side propagation bypassed for remote pairs; local pairs keep
 * the existing in-process atomic propagation unchanged.
 */
public final class LocalityAwareRoutedOrderOutcomePropagation implements RoutedOrderOutcomePropagation {

    private final OrganisationCode hubOrganisation;
    private final LegalEntityRepository legalEntityRepository;
    private final RoutedOrderOutcomePropagation localPropagation;
    private final RoutingOutcomeOutbox routingOutcomeOutbox;

    public LocalityAwareRoutedOrderOutcomePropagation(
            OrganisationCode hubOrganisation,
            LegalEntityRepository legalEntityRepository,
            RoutedOrderOutcomePropagation localPropagation,
            RoutingOutcomeOutbox routingOutcomeOutbox) {
        this.hubOrganisation = Objects.requireNonNull(hubOrganisation);
        this.legalEntityRepository = Objects.requireNonNull(legalEntityRepository);
        this.localPropagation = Objects.requireNonNull(localPropagation);
        this.routingOutcomeOutbox = Objects.requireNonNull(routingOutcomeOutbox);
    }

    @Override
    public ExecutionPropagationResult propagateExecution(MoneyMarketOrder hubOrder) {
        if (isRemote(hubOrder)) {
            routingOutcomeOutbox.scheduleExecuted(
                    hubOrder, hubOrder.getExecutionDetails().executionTime());
            return new ExecutionPropagationResult(
                    null,
                    new ExecutionHandoffRoutingContext(
                            hubOrder.getRoutingId(),
                            hubOrder.getOriginatingLegalEntityCode(),
                            null,
                            null,
                            null));
        }
        return localPropagation.propagateExecution(hubOrder);
    }

    @Override
    public void propagateCancel(MoneyMarketOrder hubOrder, Instant now) {
        if (isRemote(hubOrder)) {
            routingOutcomeOutbox.scheduleCancelled(hubOrder, now);
            return;
        }
        localPropagation.propagateCancel(hubOrder, now);
    }

    @Override
    public void propagateReject(MoneyMarketOrder hubOrder, String reason, Instant now) {
        if (isRemote(hubOrder)) {
            routingOutcomeOutbox.scheduleRejected(hubOrder, reason, now);
            return;
        }
        localPropagation.propagateReject(hubOrder, reason, now);
    }

    /**
     * A pair is remote when the originating client's legal entity belongs to a different
     * organisation than the hub deployment. A non-routed order (null {@code
     * originatingLegalEntityCode}) is never remote — it is a local desk order or a client-side order.
     */
    private boolean isRemote(MoneyMarketOrder hubOrder) {
        LegalEntityCode originatingLegalEntityCode = hubOrder.getOriginatingLegalEntityCode();
        if (originatingLegalEntityCode == null) {
            return false;
        }
        return !legalEntityRepository.belongsToOrganisation(originatingLegalEntityCode, hubOrganisation);
    }
}
