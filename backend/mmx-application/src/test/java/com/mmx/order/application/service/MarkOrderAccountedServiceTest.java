package com.mmx.order.application.service;

import com.mmx.order.application.port.out.AuditLogger;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.DealingReference;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.MoneyMarketOrder;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.TraderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarkOrderAccountedServiceTest {

    private static final Instant T0 = Instant.parse("2026-05-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-05-01T11:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 1);

    @Mock
    OrderRepository orderRepository;

    @Mock
    AuditLogger auditLogger;

    @Mock
    Clock clock;

    @InjectMocks
    MarkOrderAccountedService subject;

    @BeforeEach
    void clockT1() {
        when(clock.now()).thenReturn(T1);
    }

    @Test
    void markAccounted_executed_persistsAndAuditsOnce() {
        MoneyMarketOrder order = executedTermOrder();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(MoneyMarketOrder.class))).then(returnsFirstArg());

        subject.markAccounted(order.getId());

        verify(orderRepository).save(eq(order));
        verify(auditLogger)
                .log(
                        eq(order.getId()),
                        eq(MarkOrderAccountedService.EVENT_ORDER_ACCOUNTED),
                        eq(MarkOrderAccountedService.AUDIT_ACTOR_BACK_OFFICE),
                        eq(T1));
    }

    @Test
    void markAccounted_alreadyAccounted_noSaveNoAudit() {
        MoneyMarketOrder order = executedTermOrder();
        order.markAccounted(T0);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        subject.markAccounted(order.getId());

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void markAccounted_unknownOrder_throws() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subject.markAccounted(id)).isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    @Test
    void markAccounted_nonExecuted_throws() {
        MoneyMarketOrder received = MoneyMarketOrder.create(
                new ExternalOrderReference("REF-BO-" + System.nanoTime()),
                OrderType.TERM,
                OrderOperation.SUBSCRIPTION,
                new PortfolioNumber("PF-1"),
                "EUR",
                new BigDecimal("1000000.00"),
                TODAY.plusDays(5),
                new BigDecimal("3.25"),
                Tenor._3M,
                null,
                null,
                null,
                TODAY);
        when(orderRepository.findById(received.getId())).thenReturn(Optional.of(received));

        assertThatThrownBy(() -> subject.markAccounted(received.getId()))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
        verify(auditLogger, never()).log(any(), any(), any(), any());
    }

    private static MoneyMarketOrder executedTermOrder() {
        MoneyMarketOrder order =
                MoneyMarketOrder.create(
                        new ExternalOrderReference("REF-BO-X-" + System.nanoTime()),
                        OrderType.TERM,
                        OrderOperation.SUBSCRIPTION,
                        new PortfolioNumber("PF-1"),
                        "EUR",
                        new BigDecimal("1000000.00"),
                        TODAY.plusDays(5),
                        new BigDecimal("3.25"),
                        Tenor._3M,
                        null,
                        null,
                        null,
                        TODAY);
        order.assign(new TraderId("trader-x"), T0);
        order.execute(
                new BigDecimal("3.5"),
                "BankCo",
                new DealingReference("DL-x"),
                new ContractNumber("CN-x"),
                new TraderId("trader-x"),
                T0);
        return order;
    }
}
