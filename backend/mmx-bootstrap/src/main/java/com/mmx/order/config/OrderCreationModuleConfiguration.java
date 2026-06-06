package com.mmx.order.config;

import com.mmx.order.application.port.in.GetContractInfoUseCase;
import com.mmx.order.application.port.in.ListOnCallCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListOnCallCurrenciesUseCase;
import com.mmx.order.application.port.in.ListOnCallNoticePeriodsUseCase;
import com.mmx.order.application.port.in.ListOnCallOperationsUseCase;
import com.mmx.order.application.port.in.ListTermCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListTermCurrenciesUseCase;
import com.mmx.order.application.port.in.ListTermOperationsUseCase;
import com.mmx.order.application.port.in.ListTermTenorsUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.service.OnCallOrderCreationOptionsService;
import com.mmx.order.application.service.TermOrderCreationOptionsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderCreationModuleConfiguration {

    @Bean
    public TermOrderCreationOptionsService termOrderCreationOptionsService(
            ManagedCurrencyRepository managedCurrencyRepository,
            TermRateRepository termRateRepository,
            InstitutionRepository institutionRepository,
            Clock clock) {
        return new TermOrderCreationOptionsService(
                managedCurrencyRepository, termRateRepository, institutionRepository, clock);
    }

    @Bean
    public ListTermCurrenciesUseCase listTermCurrenciesUseCase(
            TermOrderCreationOptionsService termOrderCreationOptionsService) {
        return termOrderCreationOptionsService;
    }

    @Bean
    public ListTermOperationsUseCase listTermOperationsUseCase(
            TermOrderCreationOptionsService termOrderCreationOptionsService) {
        return termOrderCreationOptionsService;
    }

    @Bean
    public ListTermTenorsUseCase listTermTenorsUseCase(
            TermOrderCreationOptionsService termOrderCreationOptionsService) {
        return termOrderCreationOptionsService;
    }

    @Bean
    public ListTermCounterpartiesUseCase listTermCounterpartiesUseCase(
            TermOrderCreationOptionsService termOrderCreationOptionsService) {
        return termOrderCreationOptionsService;
    }

    @Bean
    public OnCallOrderCreationOptionsService onCallOrderCreationOptionsService(
            ManagedCurrencyRepository managedCurrencyRepository,
            OnCallRateRepository onCallRateRepository,
            InstitutionRepository institutionRepository,
            OrderRepository orderRepository) {
        return new OnCallOrderCreationOptionsService(
                managedCurrencyRepository, onCallRateRepository, institutionRepository, orderRepository);
    }

    @Bean
    public ListOnCallCurrenciesUseCase listOnCallCurrenciesUseCase(
            OnCallOrderCreationOptionsService onCallOrderCreationOptionsService) {
        return onCallOrderCreationOptionsService;
    }

    @Bean
    public ListOnCallOperationsUseCase listOnCallOperationsUseCase(
            OnCallOrderCreationOptionsService onCallOrderCreationOptionsService) {
        return onCallOrderCreationOptionsService;
    }

    @Bean
    public ListOnCallNoticePeriodsUseCase listOnCallNoticePeriodsUseCase(
            OnCallOrderCreationOptionsService onCallOrderCreationOptionsService) {
        return onCallOrderCreationOptionsService;
    }

    @Bean
    public ListOnCallCounterpartiesUseCase listOnCallCounterpartiesUseCase(
            OnCallOrderCreationOptionsService onCallOrderCreationOptionsService) {
        return onCallOrderCreationOptionsService;
    }

    @Bean
    public GetContractInfoUseCase getContractInfoUseCase(
            OnCallOrderCreationOptionsService onCallOrderCreationOptionsService) {
        return onCallOrderCreationOptionsService;
    }
}
