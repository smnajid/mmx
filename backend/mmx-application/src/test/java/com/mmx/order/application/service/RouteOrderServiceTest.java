package com.mmx.order.application.service;

import com.mmx.order.application.command.ReceiveOrderCommand;
import com.mmx.order.application.port.in.RouteOrderUseCase;
import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.DelegatedGrantDirectory;
import com.mmx.order.application.port.out.GlobalAccountDirectory;
import com.mmx.order.application.port.out.GrantResolution;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.LegalEntityRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.OrganisationRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.GlobalAccount;
import com.mmx.order.domain.model.Institution;
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
import com.mmx.order.domain.model.ThinProxyInstitution;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteOrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final OrganisationCode PM_ORG = new OrganisationCode("BNKG");
    private static final LegalEntityCode PAR = new LegalEntityCode("PAR");
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
    @Mock DelegatedGrantDirectory delegatedGrantDirectory;
    @Mock GlobalAccountDirectory globalAccountDirectory;

    RouteOrderService service;

    @BeforeEach
    void setUp() {
        service =
                new RouteOrderService(
                        orderRepository,
                        managedCurrencyRepository,
                        institutionRepository,
                        proxyInstitutionRepository,
                        openPositionPort,
                        organisationRepository,
                        legalEntityRepository,
                        delegatedGrantDirectory,
                        globalAccountDirectory,
                        PM_ORG,
                        auditLogger,
                        clock);
        when(clock.now()).thenReturn(NOW);
        when(clock.today()).thenReturn(TODAY);
        when(orderRepository.save(any())).thenAnswer(returnsFirstArg());
        when(organisationRepository.findByCode(PM_ORG))
                .thenReturn(Optional.of(new Organisation(PM_ORG)));
        LegalEntity hub = LegalEntity.tradingHub(LOC, PM_ORG);
        LegalEntity client = LegalEntity.tradingClient(PAR, PM_ORG, hub);
        when(legalEntityRepository.findByCode(PAR)).thenReturn(Optional.of(client));
        when(legalEntityRepository.belongsToOrganisation(PAR, PM_ORG)).thenReturn(true);
        when(proxyInstitutionRepository.findByInstitutionCode("BNPLOC"))
                .thenReturn(
                        Optional.of(
                                ThinProxyInstitution.forHubInstitution(
                                        "BNPLOC",
                                        new Institution("BNP", "BNP", true),
                                        LOC)));
        when(institutionRepository.findByInstitutionCode("BNP"))
                .thenReturn(Optional.of(new Institution("BNP", "BNP", true)));
        when(managedCurrencyRepository.findByCode("EUR"))
                .thenReturn(Optional.of(permissiveEur()));
        when(delegatedGrantDirectory.resolveTenor(PAR, "BNPLOC", "EUR", Tenor._3M))
                .thenReturn(GrantResolution.GRANTED);
    }

    @Test
    void successful_routing_yields_routed_client_and_hub_received() {
        when(globalAccountDirectory.resolve(PAR, LOC, "EUR"))
                .thenReturn(Optional.of(new GlobalAccount(PAR, LOC, "EUR", "PAR-EUR-001")));
        when(orderRepository.findByLegalEntityAndExternalReference(any(), any()))
                .thenReturn(Optional.empty());
        when(orderRepository.findHubOrderByRoutingId(any())).thenReturn(Optional.empty());

        RouteOrderUseCase.Result result = service.route(sampleCommand());

        assertThat(result.newlyCreated()).isTrue();
        assertThat(result.clientStatus()).isEqualTo(OrderStatus.ROUTED);
        assertThat(result.hubOrderId()).isNotNull();
        verify(orderRepository, times(2)).save(any(MoneyMarketOrder.class));
    }

    @Test
    void unresolved_account_rejects_without_hub_order() {
        when(globalAccountDirectory.resolve(PAR, LOC, "EUR")).thenReturn(Optional.empty());
        when(orderRepository.findByLegalEntityAndExternalReference(any(), any()))
                .thenReturn(Optional.empty());

        RouteOrderUseCase.Result result = service.route(sampleCommand());

        assertThat(result.clientStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.hubOrderId()).isNull();
        verify(orderRepository, times(1)).save(any(MoneyMarketOrder.class));
        verify(orderRepository, never()).findHubOrderByRoutingId(any());
    }

    @Test
    void grant_violation_rejects_without_hub_order() {
        when(delegatedGrantDirectory.resolveTenor(PAR, "BNPLOC", "EUR", Tenor._3M))
                .thenReturn(GrantResolution.NO_ACTIVE_GRANT);
        when(orderRepository.findByLegalEntityAndExternalReference(any(), any()))
                .thenReturn(Optional.empty());

        RouteOrderUseCase.Result result = service.route(sampleCommand());

        assertThat(result.clientStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.hubOrderId()).isNull();
        verify(orderRepository, times(1)).save(any(MoneyMarketOrder.class));
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

    private static ReceiveOrderCommand sampleCommand() {
        return new ReceiveOrderCommand(
                new ExternalOrderReference("PM-ROUTE-100"),
                PAR,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PAR-PM-77"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(2),
                new BigDecimal("2.50000000"),
                Tenor._3M,
                null,
                null,
                "BNPLOC");
    }
}
