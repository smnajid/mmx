package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.adapter.in.rest.generated.crossorg.api.CrossOrgReferenceDataApi;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgCurrencyResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgGrantResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgInstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgOnCallSegmentResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgTermRateResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.NoticePeriodCode;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.TenorCode;
import com.mmx.order.adapter.in.rest.mapper.CrossOrgReferenceDataMapper;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.OnCallRateRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.Tenor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LODH inbound reference-data reads for thin remote clients. Every endpoint resolves the transport
 * credential to a proven principal via {@link CrossOrgIdentityResolver} (same 401/403 trust boundary as
 * routed-order intake). Grants and every rate read are scoped to the proven client's active grants;
 * currencies and institutions are hub-global.
 *
 * <p>Spec: {@code order-routing} — thin-client reference-data reads.
 */
@RestController
@ConditionalOnProperty(prefix = "mmx.cross-org", name = "role", havingValue = "hub")
public class CrossOrgReferenceDataController implements CrossOrgReferenceDataApi {

    private static final String CREDENTIAL_HEADER = "X-MMX-CrossOrg-Key";

    private final CrossOrgIdentityResolver identityResolver;
    private final ManagedCurrencyRepository currencyRepository;
    private final InstitutionRepository institutionRepository;
    private final TermRateRepository termRateRepository;
    private final OnCallRateRepository onCallRateRepository;
    private final DelegatedGrantRepository delegatedGrantRepository;
    private final CrossOrgReferenceDataMapper mapper;

    public CrossOrgReferenceDataController(
            CrossOrgIdentityResolver identityResolver,
            ManagedCurrencyRepository currencyRepository,
            InstitutionRepository institutionRepository,
            TermRateRepository termRateRepository,
            OnCallRateRepository onCallRateRepository,
            DelegatedGrantRepository delegatedGrantRepository,
            CrossOrgReferenceDataMapper mapper) {
        this.identityResolver = identityResolver;
        this.currencyRepository = currencyRepository;
        this.institutionRepository = institutionRepository;
        this.termRateRepository = termRateRepository;
        this.onCallRateRepository = onCallRateRepository;
        this.delegatedGrantRepository = delegatedGrantRepository;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<CrossOrgCurrencyResponse>> listCrossOrgCurrencies() {
        resolveProven();
        return ResponseEntity.ok(mapper.toCurrencyResponses(currencyRepository.findAll()));
    }

    @Override
    public ResponseEntity<List<CrossOrgInstitutionResponse>> listCrossOrgInstitutions(Boolean activeOnly) {
        resolveProven();
        List<com.mmx.order.domain.model.Institution> institutions =
                Boolean.TRUE.equals(activeOnly)
                        ? institutionRepository.findActive()
                        : institutionRepository.findAll();
        return ResponseEntity.ok(mapper.toInstitutionResponses(institutions));
    }

    @Override
    public ResponseEntity<List<CrossOrgTermRateResponse>> listCrossOrgTermRates(LocalDate tradingDate) {
        GrantedPairs granted = grantedPairs(resolveProven());
        return ResponseEntity.ok(
                mapper.toTermRateResponses(
                        termRateRepository.findByTradingDate(tradingDate).stream()
                                .filter(row -> granted.covers(row.institutionCode(), row.currency()))
                                .toList()));
    }

    @Override
    public ResponseEntity<List<CrossOrgTermRateResponse>> listCrossOrgLatestTermRates(
            String currency, TenorCode tenor) {
        GrantedPairs granted = grantedPairs(resolveProven());
        return ResponseEntity.ok(
                mapper.toTermRateResponses(
                        termRateRepository.findLatestRatePerInstitution(currency, Tenor.fromCode(tenor.getValue()).orElseThrow()).stream()
                                .filter(row -> granted.covers(row.institutionCode(), row.currency()))
                                .toList()));
    }

    @Override
    public ResponseEntity<List<CrossOrgOnCallSegmentResponse>> listCrossOrgOnCallSegments(
            String currency, NoticePeriodCode noticePeriod, LocalDate valueDate) {
        GrantedPairs granted = grantedPairs(resolveProven());
        NoticePeriod domainNoticePeriod = NoticePeriod.fromCode(noticePeriod.getValue()).orElseThrow();
        List<OnCallRateSegment> segments =
                valueDate == null
                        ? onCallRateRepository.findOpenSegmentsByCurrencyAndNoticePeriod(currency, domainNoticePeriod)
                        : onCallRateRepository.findSegmentsCoveringDate(currency, domainNoticePeriod, valueDate);
        return ResponseEntity.ok(
                mapper.toOnCallSegmentResponses(
                        segments.stream()
                                .filter(s -> granted.covers(s.getCurveKey().institutionCode(), s.getCurveKey().currency()))
                                .toList()));
    }

    @Override
    public ResponseEntity<List<CrossOrgGrantResponse>> listCrossOrgGrants() {
        LegalEntityCode proven = resolveProven();
        return ResponseEntity.ok(
                mapper.toGrantResponses(delegatedGrantRepository.findByClientLegalEntityCode(proven)));
    }

    /** Active grants to the proven client as {@code (hubInstitutionCode, currency)} pairs: the rate-read scope. */
    private GrantedPairs grantedPairs(LegalEntityCode proven) {
        return new GrantedPairs(
                delegatedGrantRepository.findByClientLegalEntityCode(proven).stream()
                        .filter(DelegatedInstitutionGrant::isActive)
                        .map(grant -> grant.getHubInstitutionCode() + "|" + grant.getCurrency())
                        .collect(Collectors.toSet()));
    }

    private record GrantedPairs(Set<String> keys) {
        boolean covers(String institutionCode, String currency) {
            return keys.contains(institutionCode + "|" + currency);
        }
    }

    private LegalEntityCode resolveProven() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();
        return identityResolver.resolve(request.getHeader(CREDENTIAL_HEADER));
    }
}
