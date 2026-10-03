package com.mmx.order.application.port.in;

import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.Tenor;

import java.time.LocalDate;
import java.util.List;

/**
 * The hub's rates as seen by one remote TradingClient: only rows whose {@code (institution, currency)} is
 * covered by an active delegated grant to that client.
 */
public interface ListGrantedHubRatesUseCase {

    /** The Term rate sheet for a trading day. */
    List<TermRateAuditRow> termRatesForDay(LegalEntityCode client, LocalDate tradingDate);

    /** Each institution's latest Term rate for the currency and tenor. */
    List<TermRateAuditRow> latestTermRates(LegalEntityCode client, String currency, Tenor tenor);

    /** Open OnCall segments ({@code VALID} or {@code PENDING_CONFIRMATION}) for the currency and notice period. */
    List<OnCallRateSegment> openOnCallSegments(LegalEntityCode client, String currency, NoticePeriod noticePeriod);

    /** OnCall segments for the currency and notice period that cover the value date. */
    List<OnCallRateSegment> onCallSegmentsCovering(
            LegalEntityCode client, String currency, NoticePeriod noticePeriod, LocalDate valueDate);
}
