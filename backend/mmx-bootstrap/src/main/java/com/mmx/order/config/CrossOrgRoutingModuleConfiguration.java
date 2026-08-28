package com.mmx.order.config;

import com.mmx.order.adapter.in.rest.crossorg.PropertiesCrossOrgCredentialBinder;
import com.mmx.order.adapter.out.integration.ExternalIdentityGatewayAdapter;
import com.mmx.order.adapter.out.integration.LoggingOperationalSignalAdapter;
import com.mmx.order.adapter.out.integration.RemoteManagedCurrencyRepository;
import com.mmx.order.adapter.out.integration.RemoteDelegatedGrantRepository;
import com.mmx.order.adapter.out.integration.RemoteInstitutionRepository;
import com.mmx.order.adapter.out.integration.RemoteReferenceDataContext;
import com.mmx.order.adapter.out.integration.RemoteRoutingGatewayRestAdapter;
import com.mmx.order.adapter.out.integration.RemoteTermRateRepository;
import com.mmx.order.adapter.out.integration.ThreadSleeper;
import com.mmx.order.adapter.out.messaging.RoutingOutcomeOutboxAdapter;
import com.mmx.order.adapter.out.persistence.JpaCrossOrgMembershipAdapter;
import com.mmx.order.application.port.in.AcceptRoutedHubOrderUseCase;
import com.mmx.order.application.port.in.ApplyRemoteOrderOutcomeUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.CrossOrgCredentialBinder;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.ExternalIdentityGateway;
import com.mmx.order.application.port.out.HubLocalityResolver;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OperationalSignalPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.port.out.RemoteRoutingRetryPolicy;
import com.mmx.order.application.port.out.RoutingOutcomeOutbox;
import com.mmx.order.application.port.out.Sleeper;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.service.AcceptRoutedHubOrderService;
import com.mmx.order.application.service.ApplyRemoteOrderOutcomeService;
import com.mmx.order.application.service.RemoteRoutedOrderIntake;
import com.mmx.order.application.service.ResilientRemoteRoutingGateway;
import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.domain.model.LegalEntityCode;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires all cross-org routing ports/adapters. Beans are conditionally registered based on
 * {@code mmx.cross-org.role}:
 *
 * <ul>
 *   <li><strong>hub</strong> (LODH): leg-A inbound ({@link AcceptRoutedHubOrderUseCase}), leg-B
 *       outbox ({@link RoutingOutcomeOutbox}), credential binding
 *       ({@link CrossOrgCredentialBinder}), membership check ({@link CrossOrgMembershipPort}).
 *   <li><strong>client</strong> (CGED): leg-A outbound ({@link RemoteRoutingGateway} with retry +
 *       circuit-breaker), external identity ({@link ExternalIdentityGateway}), leg-B apply
 *       ({@link ApplyRemoteOrderOutcomeUseCase}), remote-backed reference-data adapters.
 * </ul>
 *
 * <p>When {@code role} is absent (local-only deployment), no cross-org beans are registered and
 * routing falls back to the existing synchronous in-process path.
 */
@Configuration
@EnableConfigurationProperties(CrossOrgRoutingProperties.class)
public class CrossOrgRoutingModuleConfiguration {

    // ─── Hub-side beans (LODH) ──────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
    public LegalEntityCode hubLegalEntityCode(CrossOrgRoutingProperties props) {
        return new LegalEntityCode(props.hubLegalEntityCode());
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
    public CrossOrgMembershipPort crossOrgMembershipPort(
            LegalEntityRepository legalEntityRepository, LegalEntityCode hubLegalEntityCode) {
        return new JpaCrossOrgMembershipAdapter(legalEntityRepository, hubLegalEntityCode);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
    public CrossOrgCredentialBinder crossOrgCredentialBinder(CrossOrgRoutingProperties props) {
        Map<String, String> creds =
                props.credentials() != null ? props.credentials() : Map.of();
        return new PropertiesCrossOrgCredentialBinder(creds);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
    public RoutingOutcomeOutbox routingOutcomeOutbox(
            com.mmx.order.adapter.out.messaging.repository.SpringDataRoutingOutcomeOutboxRepository
                    repo,
            java.time.Clock clock) {
        return new RoutingOutcomeOutboxAdapter(repo, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
    public AcceptRoutedHubOrderUseCase acceptRoutedHubOrderUseCase(
            LegalEntityCode hubLegalEntityCode,
            CrossOrgMembershipPort membershipPort,
            DelegatedGrantRepository delegatedGrantRepository,
            InstitutionRepository institutionRepository,
            OrderRepository orderRepository,
            RoutingOutcomeOutbox routingOutcomeOutbox,
            Clock clock) {
        return new AcceptRoutedHubOrderService(
                hubLegalEntityCode,
                membershipPort,
                delegatedGrantRepository,
                institutionRepository,
                orderRepository,
                routingOutcomeOutbox,
                clock);
    }

    // ─── Client-side beans (CGED) ───────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public HubLocalityResolver hubLocalityResolver() {
        return clientCode -> HubLocality.REMOTE;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public ApplyRemoteOrderOutcomeUseCase applyRemoteOrderOutcomeUseCase(
            OrderRepository orderRepository, ReferenceGenerator referenceGenerator) {
        return new ApplyRemoteOrderOutcomeService(orderRepository, referenceGenerator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public Sleeper sleeper() {
        return new ThreadSleeper();
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public OperationalSignalPort operationalSignalPort() {
        return new LoggingOperationalSignalAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public RemoteRoutingRetryPolicy remoteRoutingRetryPolicy(CrossOrgRoutingProperties props) {
        var r = props.retry();
        return new RemoteRoutingRetryPolicy(
                r.maxAttempts(),
                Duration.ofMillis(r.initialBackoffMs()),
                r.failureThreshold(),
                Duration.ofMillis(r.recoveryDurationMs()));
    }

    @Bean(name = "remoteRoutingGateway")
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public RemoteRoutingGateway remoteRoutingGateway(
            CrossOrgRoutingProperties props,
            Clock clock,
            Sleeper sleeper,
            OperationalSignalPort signalPort,
            RemoteRoutingRetryPolicy retryPolicy) {
        var endpoint = props.remoteRoutingGateway();
        var restAdapter =
                new RemoteRoutingGatewayRestAdapter(endpoint.baseUrl(), endpoint.credentialKey());
        return new ResilientRemoteRoutingGateway(
                restAdapter, clock, sleeper, signalPort, retryPolicy);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public ExternalIdentityGateway externalIdentityGateway(CrossOrgRoutingProperties props) {
        return new ExternalIdentityGatewayAdapter(props.externalIdentity().baseUrl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public RemoteRoutedOrderIntake remoteRoutedOrderIntake(
            ExternalIdentityGateway externalIdentityGateway,
            RemoteRoutingGateway remoteRoutingGateway,
            OrderRepository orderRepository,
            Clock clock) {
        return new RemoteRoutedOrderIntake(
                externalIdentityGateway, remoteRoutingGateway, orderRepository, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "client")
    public com.mmx.order.adapter.out.messaging.RoutingOutcomeV1Consumer routingOutcomeV1Consumer(
            ApplyRemoteOrderOutcomeUseCase applyRemoteOrderOutcomeUseCase,
            CrossOrgRoutingProperties props) {
        java.util.Set<String> ownLes =
                props.ownLegalEntityCodes() != null ? props.ownLegalEntityCodes() : java.util.Set.of();
        return new com.mmx.order.adapter.out.messaging.RoutingOutcomeV1Consumer(
                applyRemoteOrderOutcomeUseCase, ownLes);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "consumer-enabled", havingValue = "true")
    public com.mmx.order.messaging.RoutingOutcomeKafkaListener routingOutcomeKafkaListener(
            com.mmx.order.adapter.out.messaging.RoutingOutcomeV1Consumer consumer) {
        return new com.mmx.order.messaging.RoutingOutcomeKafkaListener(consumer);
    }

    // ─── Remote-backed reference-data adapters (CGED, opt-in) ───────────────

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "reference-data-remote", havingValue = "true")
    public RemoteReferenceDataContext remoteReferenceDataContext(
            CrossOrgRoutingProperties props) {
        var endpoint = props.remoteRoutingGateway();
        return new RemoteReferenceDataContext(endpoint.baseUrl(), endpoint.credentialKey());
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "reference-data-remote", havingValue = "true")
    @org.springframework.context.annotation.Primary
    public ManagedCurrencyRepository remoteManagedCurrencyRepository(
            RemoteReferenceDataContext ctx) {
        return new RemoteManagedCurrencyRepository(ctx);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "reference-data-remote", havingValue = "true")
    @org.springframework.context.annotation.Primary
    public InstitutionRepository remoteInstitutionRepository(RemoteReferenceDataContext ctx) {
        return new RemoteInstitutionRepository(ctx);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "reference-data-remote", havingValue = "true")
    @org.springframework.context.annotation.Primary
    public TermRateRepository remoteTermRateRepository(RemoteReferenceDataContext ctx) {
        return new RemoteTermRateRepository(ctx);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mmx.cross-org", name = "reference-data-remote", havingValue = "true")
    @org.springframework.context.annotation.Primary
    public DelegatedGrantRepository remoteDelegatedGrantRepository(RemoteReferenceDataContext ctx) {
        return new RemoteDelegatedGrantRepository(ctx);
    }
}
