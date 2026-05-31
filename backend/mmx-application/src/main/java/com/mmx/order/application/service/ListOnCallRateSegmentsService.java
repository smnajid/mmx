package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ListOnCallRateSegmentsUseCase;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.domain.model.OnCallRateSegment;

import java.util.List;

public final class ListOnCallRateSegmentsService implements ListOnCallRateSegmentsUseCase {

    private final OnCallRateRepository onCallRateRepository;
    private final InstitutionRepository institutionRepository;

    public ListOnCallRateSegmentsService(
            OnCallRateRepository onCallRateRepository, InstitutionRepository institutionRepository) {
        this.onCallRateRepository = onCallRateRepository;
        this.institutionRepository = institutionRepository;
    }

    @Override
    public List<OnCallRateSegment> listByInstitution(String institutionCode) {
        institutionRepository
                .findByInstitutionCode(institutionCode)
                .orElseThrow(
                        () ->
                                new ManageInstitutionSettingsService.InstitutionNotFoundException(
                                        institutionCode));
        return onCallRateRepository.findByInstitutionCode(institutionCode);
    }
}
