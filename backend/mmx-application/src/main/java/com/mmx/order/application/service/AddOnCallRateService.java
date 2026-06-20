package com.mmx.order.application.service;

import com.mmx.order.application.command.AddOnCallRateCommand;
import com.mmx.order.application.exception.InstitutionNotFoundException;
import com.mmx.order.application.port.in.AddOnCallRateUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.OnCallRateHandoffOutbox;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.ReferenceGenerator;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.policy.OnCallRateCurvePolicy;

public final class AddOnCallRateService implements AddOnCallRateUseCase {

    private final OnCallRateRepository onCallRateRepository;
    private final InstitutionRepository institutionRepository;
    private final OnCallRateHandoffOutbox onCallRateHandoffOutbox;
    private final ReferenceGenerator referenceGenerator;
    private final Clock clock;
    private final OnCallRateCurvePolicy curvePolicy;

    public AddOnCallRateService(
            OnCallRateRepository onCallRateRepository,
            InstitutionRepository institutionRepository,
            OnCallRateHandoffOutbox onCallRateHandoffOutbox,
            ReferenceGenerator referenceGenerator,
            Clock clock) {
        this.onCallRateRepository = onCallRateRepository;
        this.institutionRepository = institutionRepository;
        this.onCallRateHandoffOutbox = onCallRateHandoffOutbox;
        this.referenceGenerator = referenceGenerator;
        this.clock = clock;
        this.curvePolicy = new OnCallRateCurvePolicy();
    }

    @Override
    public OnCallRateSegment add(AddOnCallRateCommand command) {
        requireInstitution(command.institutionCode());
        var curveKey =
                new OnCallCurveKey(
                        command.institutionCode(), command.currency(), command.noticePeriod());
        var today = clock.today();
        curvePolicy.assertValueDateNotBackdated(command.valueDate(), today);
        curvePolicy.assertNoPendingOnCurve(onCallRateRepository.findPendingForCurveKey(curveKey));

        onCallRateRepository
                .findOpenSegment(curveKey)
                .ifPresent(
                        prior ->
                                onCallRateRepository.save(
                                        prior.withEndDate(
                                                curvePolicy.computeSupersededPriorEndDate(
                                                        command.valueDate()))));

        OnCallRateSegment pending =
                OnCallRateSegment.createPending(
                        referenceGenerator.generateSegmentId(),
                        curveKey,
                        command.rate(),
                        command.valueDate());
        OnCallRateSegment saved = onCallRateRepository.save(pending);
        onCallRateHandoffOutbox.scheduleUpdated(saved);
        return saved;
    }

    private void requireInstitution(String institutionCode) {
        institutionRepository
                .findByInstitutionCode(institutionCode)
                .orElseThrow(() -> new InstitutionNotFoundException(institutionCode));
    }
}
