package com.mmx.order.application.service;

import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The hub's rate reads for a remote client are scoped to the client's active delegated grants: a row is
 * visible only when {@code (institution, currency)} is covered by an active grant to that client. Tenor and
 * notice-period permission is the client's concern (effective enablement), not applied here.
 *
 * <p>Spec: {@code order-routing} — thin remote client reads hub reference data live.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class GrantedHubRatesServiceTest {

    private static final LegalEntityCode CGD = new LegalEntityCode("CGD");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 2);

    @Mock
    DelegatedGrantRepository delegatedGrantRepository;
    @Mock
    TermRateRepository termRateRepository;
    @Mock
    OnCallRateRepository onCallRateRepository;

    GrantedHubRatesService subject;

    @BeforeEach
    void setUp() {
        subject = new GrantedHubRatesService(delegatedGrantRepository, termRateRepository, onCallRateRepository);
    }

    @Test
    void termRatesForDay_returnsOnlyRowsGrantedToTheClient() {
        givenGrants(grant("BNP", "EUR", true));
        when(termRateRepository.findByTradingDate(DAY))
                .thenReturn(List.of(rate("BNP", "EUR"), rate("BNP", "USD"), rate("SGFR", "EUR")));

        assertThat(subject.termRatesForDay(CGD, DAY)).extracting(TermRateAuditRow::institutionCode, TermRateAuditRow::currency)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BNP", "EUR"));
    }

    @Test
    void latestTermRates_returnsOnlyGrantedInstitutionsForTheCurrencyAndTenor() {
        givenGrants(grant("BNP", "EUR", true));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M))
                .thenReturn(List.of(rate("BNP", "EUR"), rate("SGFR", "EUR")));

        assertThat(subject.latestTermRates(CGD, "EUR", Tenor._3M)).extracting(TermRateAuditRow::institutionCode)
                .containsExactly("BNP");
    }

    @Test
    void latestTermRates_ignoresAnInactiveGrant() {
        givenGrants(grant("BNP", "EUR", false));
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M)).thenReturn(List.of(rate("BNP", "EUR")));

        assertThat(subject.latestTermRates(CGD, "EUR", Tenor._3M)).isEmpty();
    }

    @Test
    void latestTermRates_isEmptyWhenTheClientHasNoGrants() {
        givenGrants();
        when(termRateRepository.findLatestRatePerInstitution("EUR", Tenor._3M)).thenReturn(List.of(rate("BNP", "EUR")));

        assertThat(subject.latestTermRates(CGD, "EUR", Tenor._3M)).isEmpty();
    }

    @Test
    void openOnCallSegments_returnsOnlyGrantedSegments_includingPendingConfirmation() {
        givenGrants(grant("BNP", "EUR", true));
        when(onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod("EUR", NoticePeriod._24H))
                .thenReturn(List.of(
                        segment("BNP", OnCallRateSegmentStatus.PENDING_CONFIRMATION),
                        segment("SGFR", OnCallRateSegmentStatus.VALID)));

        assertThat(subject.openOnCallSegments(CGD, "EUR", NoticePeriod._24H))
                .extracting(s -> s.getCurveKey().institutionCode())
                .containsExactly("BNP");
        verify(onCallRateRepository, never()).findSegmentsCoveringDate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onCallSegmentsCovering_returnsOnlyGrantedSegmentsCoveringTheValueDate() {
        LocalDate valueDate = LocalDate.of(2026, 6, 9);
        givenGrants(grant("BNP", "EUR", true));
        when(onCallRateRepository.findSegmentsCoveringDate("EUR", NoticePeriod._24H, valueDate))
                .thenReturn(List.of(
                        segment("BNP", OnCallRateSegmentStatus.VALID),
                        segment("SGFR", OnCallRateSegmentStatus.VALID)));

        assertThat(subject.onCallSegmentsCovering(CGD, "EUR", NoticePeriod._24H, valueDate))
                .extracting(s -> s.getCurveKey().institutionCode())
                .containsExactly("BNP");
        verify(onCallRateRepository, never()).findOpenSegmentsByCurrencyAndNoticePeriod(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void givenGrants(DelegatedInstitutionGrant... grants) {
        when(delegatedGrantRepository.findByClientLegalEntityCode(CGD)).thenReturn(List.of(grants));
    }

    private static DelegatedInstitutionGrant grant(String hubInstitutionCode, String currency, boolean active) {
        return new DelegatedInstitutionGrant(
                hubInstitutionCode, CGD, currency, Set.of(Tenor._3M), Set.of(NoticePeriod._24H), active);
    }

    private static TermRateAuditRow rate(String institution, String currency) {
        return new TermRateAuditRow(DAY, institution, currency, Tenor._3M, new BigDecimal("3.50"), null, null);
    }

    private static OnCallRateSegment segment(String institution, OnCallRateSegmentStatus status) {
        return new OnCallRateSegment(
                UUID.randomUUID(),
                new OnCallCurveKey(institution, "EUR", NoticePeriod._24H),
                new BigDecimal("2.90"),
                LocalDate.of(2026, 6, 1),
                OnCallRateSegment.NO_END_DATE,
                status,
                null);
    }
}
