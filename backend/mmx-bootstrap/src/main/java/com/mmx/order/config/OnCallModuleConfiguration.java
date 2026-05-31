package com.mmx.order.config;

import com.mmx.order.adapter.out.persistence.JpaOnCallRateRepository;
import com.mmx.order.adapter.out.persistence.mapper.OnCallRateSegmentPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOnCallRateSegmentRepository;
import com.mmx.order.application.port.in.AddOnCallRateUseCase;
import com.mmx.order.application.port.in.CancelOnCallRateUseCase;
import com.mmx.order.application.port.in.ConfirmOnCallRateUseCase;
import com.mmx.order.application.port.in.ListOnCallRateSegmentsUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OnCallRateHandoffOutbox;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.application.service.AddOnCallRateService;
import com.mmx.order.application.service.CancelOnCallRateService;
import com.mmx.order.application.service.ConfirmOnCallRateService;
import com.mmx.order.application.service.ListOnCallRateSegmentsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OnCallModuleConfiguration {

    @Bean
    public OnCallRateRepository onCallRateRepository(
            SpringDataOnCallRateSegmentRepository springDataRepository,
            OnCallRateSegmentPersistenceMapper mapper) {
        return new JpaOnCallRateRepository(springDataRepository, mapper);
    }

    @Bean
    public OnCallRateSegmentPersistenceMapper onCallRateSegmentPersistenceMapper() {
        return new OnCallRateSegmentPersistenceMapper();
    }

    @Bean
    public AddOnCallRateService addOnCallRateService(
            OnCallRateRepository onCallRateRepository,
            InstitutionRepository institutionRepository,
            OnCallRateHandoffOutbox onCallRateHandoffOutbox,
            ReferenceGenerator referenceGenerator,
            Clock clock) {
        return new AddOnCallRateService(
                onCallRateRepository,
                institutionRepository,
                onCallRateHandoffOutbox,
                referenceGenerator,
                clock);
    }

    @Bean
    public CancelOnCallRateService cancelOnCallRateService(
            OnCallRateRepository onCallRateRepository,
            InstitutionRepository institutionRepository,
            OnCallRateHandoffOutbox onCallRateHandoffOutbox) {
        return new CancelOnCallRateService(
                onCallRateRepository, institutionRepository, onCallRateHandoffOutbox);
    }

    @Bean
    public ConfirmOnCallRateService confirmOnCallRateService(
            OnCallRateRepository onCallRateRepository, Clock clock) {
        return new ConfirmOnCallRateService(onCallRateRepository, clock);
    }

    @Bean
    public ListOnCallRateSegmentsUseCase listOnCallRateSegmentsUseCase(
            OnCallRateRepository onCallRateRepository, InstitutionRepository institutionRepository) {
        return new ListOnCallRateSegmentsService(onCallRateRepository, institutionRepository);
    }
}
