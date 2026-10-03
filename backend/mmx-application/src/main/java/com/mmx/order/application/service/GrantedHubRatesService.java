package com.mmx.order.application.service;

import com.mmx.order.application.port.in.ListGrantedHubRatesUseCase;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.Tenor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Scopes the hub's rates to a remote client's active grants on {@code (institution, currency)} only. Whether
 * a tenor or notice period may be ordered is the client's own effective enablement, so it is not applied here.
 */
public class GrantedHubRatesService implements ListGrantedHubRatesUseCase {

    private final DelegatedGrantRepository delegatedGrantRepository;
    private final TermRateRepository termRateRepository;
    private final OnCallRateRepository onCallRateRepository;

    public GrantedHubRatesService(
            DelegatedGrantRepository delegatedGrantRepository,
            TermRateRepository termRateRepository,
            OnCallRateRepository onCallRateRepository) {
        this.delegatedGrantRepository = delegatedGrantRepository;
        this.termRateRepository = termRateRepository;
        this.onCallRateRepository = onCallRateRepository;
    }

    @Override
    public List<TermRateAuditRow> termRatesForDay(LegalEntityCode client, LocalDate tradingDate) {
        return termRateRepository.findByTradingDate(tradingDate).stream().filter(termRowGranted(client)).toList();
    }

    @Override
    public List<TermRateAuditRow> latestTermRates(LegalEntityCode client, String currency, Tenor tenor) {
        return termRateRepository.findLatestRatePerInstitution(currency, tenor).stream()
                .filter(termRowGranted(client))
                .toList();
    }

    @Override
    public List<OnCallRateSegment> openOnCallSegments(LegalEntityCode client, String currency, NoticePeriod noticePeriod) {
        return onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod(currency, noticePeriod).stream()
                .filter(segmentGranted(client))
                .toList();
    }

    @Override
    public List<OnCallRateSegment> onCallSegmentsCovering(
            LegalEntityCode client, String currency, NoticePeriod noticePeriod, LocalDate valueDate) {
        return onCallRateRepository.findSegmentsCoveringDate(currency, noticePeriod, valueDate).stream()
                .filter(segmentGranted(client))
                .toList();
    }

    private Predicate<TermRateAuditRow> termRowGranted(LegalEntityCode client) {
        Set<GrantKey> granted = grantedPairs(client);
        return row -> granted.contains(new GrantKey(row.institutionCode(), row.currency()));
    }

    private Predicate<OnCallRateSegment> segmentGranted(LegalEntityCode client) {
        Set<GrantKey> granted = grantedPairs(client);
        return segment ->
                granted.contains(new GrantKey(segment.getCurveKey().institutionCode(), segment.getCurveKey().currency()));
    }

    /** Active grants to the client as {@code (hubInstitutionCode, currency)} pairs. */
    private Set<GrantKey> grantedPairs(LegalEntityCode client) {
        return delegatedGrantRepository.findByClientLegalEntityCode(client).stream()
                .filter(DelegatedInstitutionGrant::isActive)
                .map(grant -> new GrantKey(grant.getHubInstitutionCode(), grant.getCurrency()))
                .collect(Collectors.toSet());
    }

    private record GrantKey(String hubInstitutionCode, String currency) {}
}
