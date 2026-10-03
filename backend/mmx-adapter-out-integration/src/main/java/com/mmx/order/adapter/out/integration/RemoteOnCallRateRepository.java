package com.mmx.order.adapter.out.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallCurveKey;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.OnCallRateSegmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Remote-backed {@link OnCallRateRepository}: reads the hub's open OnCall segments, grant-scoped to the proven
 * client, live from LODH via REST. Read-only; a client deployment holds no OnCall rates of its own (ADR 0007).
 * Only the two order-creation reads are served; the rest of the port is either empty (reads of client-local
 * curve data, which has none) or refused (writes and the hub-only distinct-currency query).
 */
public final class RemoteOnCallRateRepository implements OnCallRateRepository {

    private static final String PATH = "/api/v1/cross-org/reference/oncall-segments";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RemoteReferenceDataContext ctx;

    public RemoteOnCallRateRepository(RemoteReferenceDataContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<OnCallRateSegment> findOpenSegmentsByCurrencyAndNoticePeriod(String currency, NoticePeriod noticePeriod) {
        return read(PATH + "?currency=" + currency + "&noticePeriod=" + noticePeriod.getCode());
    }

    @Override
    public List<OnCallRateSegment> findSegmentsCoveringDate(
            String currency, NoticePeriod noticePeriod, LocalDate valueDate) {
        return read(PATH + "?currency=" + currency + "&noticePeriod=" + noticePeriod.getCode()
                + "&valueDate=" + valueDate.format(ISO_DATE));
    }

    private List<OnCallRateSegment> read(String path) {
        return RemoteReferenceDataHttp.getList(ctx, path, SegmentDto.class).stream()
                .map(SegmentDto::toDomain)
                .toList();
    }

    /** A client institution code never carries hub segments, so there is nothing to list. */
    @Override
    public List<OnCallRateSegment> findByInstitutionCode(String institutionCode) {
        return List.of();
    }

    @Override
    public Optional<OnCallRateSegment> findById(UUID segmentId) {
        return Optional.empty();
    }

    @Override
    public Optional<OnCallRateSegment> findOpenSegment(OnCallCurveKey curveKey) {
        return Optional.empty();
    }

    @Override
    public Optional<OnCallRateSegment> findPendingForCurveKey(OnCallCurveKey curveKey) {
        return Optional.empty();
    }

    @Override
    public Optional<OnCallRateSegment> findSupersededPrior(OnCallRateSegment pendingSegment) {
        return Optional.empty();
    }

    @Override
    public OnCallRateSegment save(OnCallRateSegment segment) {
        throw readOnly();
    }

    @Override
    public boolean compareAndConfirmPending(UUID segmentId, Instant validatedAt) {
        throw readOnly();
    }

    /** Hub-only query: a client derives its currencies per enabled notice period. */
    @Override
    public List<String> findDistinctCurrenciesWithOpenOnCallSegments() {
        throw new UnsupportedOperationException(
                "Remote-backed OnCallRateRepository has no hub-wide currency listing; clients read per (currency, noticePeriod)");
    }

    private static UnsupportedOperationException readOnly() {
        return new UnsupportedOperationException(
                "Remote-backed OnCallRateRepository is read-only; a client does not master hub OnCall rates");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class SegmentDto {
        @JsonProperty("institutionCode")
        String institutionCode;
        @JsonProperty("currency")
        String currency;
        @JsonProperty("noticePeriod")
        String noticePeriod;
        @JsonProperty("rate")
        double rate;
        @JsonProperty("valueDate")
        String valueDate;
        @JsonProperty("endDate")
        String endDate;
        @JsonProperty("status")
        String status;

        /**
         * The hub's segment id and confirmation time never cross the boundary: order creation only reads
         * rate, dates and status. The id is a placeholder that satisfies the domain invariant;
         * {@code validatedAt} is left unset.
         */
        OnCallRateSegment toDomain() {
            return new OnCallRateSegment(
                    UUID.randomUUID(),
                    new OnCallCurveKey(institutionCode, currency, NoticePeriod.fromCode(noticePeriod).orElseThrow()),
                    BigDecimal.valueOf(rate),
                    LocalDate.parse(valueDate, ISO_DATE),
                    LocalDate.parse(endDate, ISO_DATE),
                    OnCallRateSegmentStatus.valueOf(status),
                    null);
        }
    }
}
