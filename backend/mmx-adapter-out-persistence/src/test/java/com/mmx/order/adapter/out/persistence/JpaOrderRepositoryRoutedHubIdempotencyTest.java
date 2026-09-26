package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.domain.exception.DuplicateRoutedHubOrderException;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.RoutedHubOrderDraft;
import com.mmx.order.domain.model.RoutingId;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the persistence-adapter translation seam required by cross-org routed-hub idempotency:
 * a {@link DataIntegrityViolationException} raised by the underlying Spring Data repository during
 * a routed hub-side order insert is translated into the domain {@link DuplicateRoutedHubOrderException}
 * so the application layer (framework-free) can catch it.
 *
 * <p>Spec: {@code order-routing} — cross-boundary correlation and idempotency (design D5: the partial
 * unique index is authoritative; the adapter translates; the use case catches).
 *
 * <p>The composite partial unique index itself is integration-tested in
 * {@code CrossOrgRoutingPartialUniqueIndexTest}; this test pins the translation seam in isolation.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class JpaOrderRepositoryRoutedHubIdempotencyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);
    private static final LegalEntityCode HUB_LE = new LegalEntityCode("LOC");
    private static final LegalEntityCode ORIGINATING_LE = new LegalEntityCode("CGD");

    @Mock
    SpringDataOrderRepository springDataRepository;
    @Mock
    OrderPersistenceMapper mapper;

    JpaOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaOrderRepository(springDataRepository, mapper);
    }

    @Test
    void save_routedHubSideOrder_translatesDataIntegrityViolation_toDuplicateRoutedHubOrderException() {
        MoneyMarketOrder hubOrder = routedHubSideOrder();
        OrderEntity entity = new OrderEntity();
        when(mapper.toEntity(hubOrder)).thenReturn(entity);
        when(springDataRepository.saveAndFlush(entity))
                .thenThrow(new DataIntegrityViolationException("duplicate key violates uq_money_market_order_routing_hub_pair"));

        assertThatThrownBy(() -> repository.save(hubOrder))
                .isInstanceOf(DuplicateRoutedHubOrderException.class)
                .hasMessageContaining("Routed hub-side order collided")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);

        verify(mapper).toEntity(hubOrder);
        verify(springDataRepository).saveAndFlush(entity);
    }

    @Test
    void save_localDeskOrder_propagatesDataIntegrityViolation_unchanged() {
        MoneyMarketOrder localOrder = localDeskOrder();
        OrderEntity entity = new OrderEntity();
        when(mapper.toEntity(localOrder)).thenReturn(entity);
        when(springDataRepository.save(entity))
                .thenThrow(new DataIntegrityViolationException("duplicate key violates external_order_reference"));

        assertThatThrownBy(() -> repository.save(localOrder))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateRoutedHubOrderException.class);

        verify(springDataRepository).save(entity);
        verify(springDataRepository, never()).saveAndFlush(any());
    }

    private static MoneyMarketOrder routedHubSideOrder() {
        RoutingId routingId = RoutingId.fromClientOrderId(UUID.randomUUID());
        return MoneyMarketOrder.createHubSideFromRouting(
                new RoutedHubOrderDraft(
                        HUB_LE,
                        new PortfolioNumber("LOC-EUR-001"),
                        "HSBC-01",
                        "BankCo International",
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(2),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        Tenor._3M,
                        null,
                        new BigDecimal("3.25"),
                        null,
                        routingId,
                        ORIGINATING_LE,
                        new ExternalOrderReference("CGD-PM-1")),
                TODAY);
    }

    private static MoneyMarketOrder localDeskOrder() {
        return MoneyMarketOrder.create(
                new ExternalOrderReference("LOCAL-1"),
                HUB_LE,
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-LOCAL"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(2),
                new BigDecimal("3.25"),
                Tenor._3M,
                null,
                null,
                "HSBC-01",
                "BankCo",
                TODAY);
    }
}
