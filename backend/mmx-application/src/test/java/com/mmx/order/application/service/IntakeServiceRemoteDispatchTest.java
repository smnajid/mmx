package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.IntakeUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.ExternalIdentityGateway;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.application.port.out.HubLocalityResolver;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.OrganisationRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.application.port.out.RemoteRoutingGateway;
import com.mmx.order.application.port.out.RemoteRoutingRequest;
import com.mmx.order.application.port.out.RemoteRoutingResponse;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.HubLocality;
import com.mmx.order.domain.model.LegalEntity;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderStatus;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.Organisation;
import com.mmx.order.domain.model.OrganisationCode;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the dispatch decision in {@link IntakeService#receiveRouted}: when the connected hub is
 * remote, intake delegates to {@link RemoteRoutedOrderIntake} and never touches the local-routing
 * path (no proxy lookup, no local account directory, no local hub-order persistence).
 *
 * <p>Spec: {@code order-routing} — remote routed order intake; design D1/D3/D7.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IntakeServiceRemoteDispatchTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final OrganisationCode CGED_ORG = new OrganisationCode("CGED");
    private static final LegalEntityCode CGD = new LegalEntityCode("CGD");
    private static final LegalEntityCode LOC = new LegalEntityCode("LOC");

    @Mock OrderRepository orderRepository;
    @Mock AuditLogger auditLogger;
    @Mock Clock clock;
    @Mock ManagedCurrencyRepository managedCurrencyRepository;
    @Mock InstitutionRepository institutionRepository;
    @Mock ProxyInstitutionRepository proxyInstitutionRepository;
    @Mock OpenPositionPort openPositionPort;
    @Mock OrganisationRepository organisationRepository;
    @Mock LegalEntityRepository legalEntityRepository;
    @Mock GlobalAccountDirectory globalAccountDirectory;
    @Mock DelegatedGrantDirectory delegatedGrantDirectory;
    @Mock ExternalIdentityGateway externalIdentityGateway;
    @Mock RemoteRoutingGateway remoteRoutingGateway;

    IntakeService subject;

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(FIXED_NOW);
        when(clock.today()).thenReturn(TODAY);
        when(orderRepository.save(any())).thenAnswer(returnsFirstArg());
        when(orderRepository.findByLegalEntityAndExternalReference(any(), any()))
                .thenReturn(Optional.empty());
        when(organisationRepository.findByCode(CGED_ORG))
                .thenReturn(Optional.of(new Organisation(CGED_ORG)));

        LegalEntity hub = LegalEntity.tradingHub(LOC, new OrganisationCode("LODH"));
        LegalEntity client = LegalEntity.tradingClient(CGD, CGED_ORG, hub);
        when(legalEntityRepository.findByCode(CGD)).thenReturn(Optional.of(client));
        when(legalEntityRepository.belongsToOrganisation(CGD, CGED_ORG)).thenReturn(true);

        when(externalIdentityGateway.resolveHubSidePortfolioNumber(any(), any(), any()))
                .thenReturn(Optional.of(new PortfolioNumber("LOC-EUR-001")));
        when(remoteRoutingGateway.route(any()))
                .thenReturn(new RemoteRoutingResponse.Accept(FIXED_NOW));

        HubLocalityResolver remoteResolver = code -> HubLocality.REMOTE;
        RemoteRoutedOrderIntake remoteIntake =
                new RemoteRoutedOrderIntake(externalIdentityGateway, remoteRoutingGateway, orderRepository, clock);
        RoutedOrderIntake localIntake =
                new RoutedOrderIntake(
                        proxyInstitutionRepository,
                        delegatedGrantDirectory,
                        globalAccountDirectory,
                        institutionRepository,
                        orderRepository,
                        clock);

        subject =
                new IntakeService(
                        orderRepository,
                        managedCurrencyRepository,
                        institutionRepository,
                        openPositionPort,
                        organisationRepository,
                        legalEntityRepository,
                        CGED_ORG,
                        localIntake,
                        auditLogger,
                        clock,
                        remoteResolver,
                        remoteIntake);
    }

    @Test
    void receiveRouted_remoteHub_delegatesToRemoteIntake_neverTouchesLocalPath() {
        ReceiveOrderCommand command = termSubscribeCommand();

        IntakeUseCase.Result result = subject.receive(command);

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.status()).isEqualTo(OrderStatus.ROUTED);

        verify(remoteRoutingGateway).route(any(RemoteRoutingRequest.class));
        verify(externalIdentityGateway).resolveHubSidePortfolioNumber(any(), any(), any());
        verify(proxyInstitutionRepository, never()).findByInstitutionCode(any());
        verify(globalAccountDirectory, never()).resolve(any(), any(), any());
    }

    @Test
    void receiveRouted_localHub_usesLocalIntake_neverTouchesRemotePath() {
        when(managedCurrencyRepository.findByCode("EUR"))
                .thenReturn(Optional.of(permissiveEur()));

        HubLocalityResolver localResolver = code -> HubLocality.LOCAL;
        RoutedOrderIntake localIntake =
                new RoutedOrderIntake(
                        proxyInstitutionRepository,
                        delegatedGrantDirectory,
                        globalAccountDirectory,
                        institutionRepository,
                        orderRepository,
                        clock);
        subject =
                new IntakeService(
                        orderRepository,
                        managedCurrencyRepository,
                        institutionRepository,
                        openPositionPort,
                        organisationRepository,
                        legalEntityRepository,
                        CGED_ORG,
                        localIntake,
                        auditLogger,
                        clock,
                        localResolver,
                        null);

        when(proxyInstitutionRepository.findByInstitutionCode(any()))
                .thenReturn(Optional.of(
                        com.mmx.order.domain.model.ThinProxyInstitution.forHubInstitution(
                                "HSBC-01",
                                new com.mmx.order.domain.model.Institution("HSBC-01", "HSBC", true),
                                LOC)));
        when(globalAccountDirectory.resolve(any(), any(), any()))
                .thenReturn(Optional.of(new com.mmx.order.domain.model.GlobalAccount(
                        CGD, LOC, "EUR", "LOC-EUR-001")));
        when(delegatedGrantDirectory.resolveTenor(any(), any(), any(), any()))
                .thenReturn(GrantResolution.GRANTED);
        when(institutionRepository.findByInstitutionCode("HSBC-01"))
                .thenReturn(Optional.of(
                        new com.mmx.order.domain.model.Institution("HSBC-01", "HSBC", true)));

        ReceiveOrderCommand command = termSubscribeCommand();
        subject.receive(command);

        verify(proxyInstitutionRepository).findByInstitutionCode(any());
        verify(remoteRoutingGateway, never()).route(any());
        verify(externalIdentityGateway, never()).resolveHubSidePortfolioNumber(any(), any(), any());
    }

    private static ReceiveOrderCommand termSubscribeCommand() {
        return new ReceiveOrderCommand(
                new ExternalOrderReference("CGD-PM-1"),
                CGD,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("CGD-EUR-001"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                new BigDecimal("3.25"),
                Tenor._3M,
                null,
                null,
                "HSBC-01");
    }

    private static ManagedCurrency permissiveEur() {
        return new ManagedCurrency(
                "EUR",
                true,
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                EnumSet.allOf(Tenor.class),
                EnumSet.allOf(NoticePeriod.class));
    }
}
