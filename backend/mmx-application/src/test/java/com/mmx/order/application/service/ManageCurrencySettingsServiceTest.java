package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ManageCurrencySettingsUseCase;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.domain.exception.DuplicateManagedCurrencyException;
import com.mmx.order.domain.exception.InvalidManagedCurrencyException;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManageCurrencySettingsServiceTest {

    @Mock
    ManagedCurrencyRepository repository;

    ManageCurrencySettingsService subject;

    @BeforeEach
    void setUp() {
        subject = new ManageCurrencySettingsService(repository);
    }

    @Test
    void onboard_persistsCurrency() {
        when(repository.existsByCode("EUR")).thenReturn(false);
        when(repository.save(any(ManagedCurrency.class))).thenAnswer(inv -> inv.getArgument(0));

        ManagedCurrency result =
                subject.onboard(
                        new ManageCurrencySettingsUseCase.OnboardCommand(
                                "EUR",
                                new BigDecimal("1000000.00"),
                                new BigDecimal("250000.00"),
                                EnumSet.of(Tenor._3M),
                                EnumSet.of(NoticePeriod._24H)));

        assertThat(result.getCode()).isEqualTo("EUR");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void onboard_rejectsDuplicate() {
        when(repository.existsByCode("EUR")).thenReturn(true);
        assertThatThrownBy(
                        () ->
                                subject.onboard(
                                        new ManageCurrencySettingsUseCase.OnboardCommand(
                                                "EUR",
                                                new BigDecimal("1000000.00"),
                                                new BigDecimal("250000.00"),
                                                EnumSet.allOf(Tenor.class),
                                                EnumSet.allOf(NoticePeriod.class))))
                .isInstanceOf(DuplicateManagedCurrencyException.class);
    }

    @Test
    void update_rejectsLastTenorRemoval() {
        ManagedCurrency existing =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("1000000.00"),
                        new BigDecimal("250000.00"),
                        EnumSet.of(Tenor._3M),
                        EnumSet.allOf(NoticePeriod.class));
        when(repository.findByCode("EUR")).thenReturn(Optional.of(existing));

        assertThatThrownBy(
                        () ->
                                subject.updateRules(
                                        "EUR",
                                        new ManageCurrencySettingsUseCase.UpdateRulesCommand(
                                                null,
                                                null,
                                                EnumSet.noneOf(Tenor.class),
                                                null)))
                .isInstanceOf(InvalidManagedCurrencyException.class);
    }

    @Test
    void disable_marksInactive() {
        ManagedCurrency existing =
                new ManagedCurrency(
                        "EUR",
                        true,
                        new BigDecimal("1000000.00"),
                        new BigDecimal("250000.00"),
                        EnumSet.allOf(Tenor.class),
                        EnumSet.allOf(NoticePeriod.class));
        when(repository.findByCode("EUR")).thenReturn(Optional.of(existing));
        when(repository.save(any(ManagedCurrency.class))).thenAnswer(inv -> inv.getArgument(0));

        ManagedCurrency result = subject.disable("EUR");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void listAll_delegatesToRepository() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(subject.listAll()).isEmpty();
    }
}
