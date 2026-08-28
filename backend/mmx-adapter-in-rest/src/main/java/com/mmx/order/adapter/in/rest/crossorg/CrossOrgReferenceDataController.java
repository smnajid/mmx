package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.adapter.in.rest.generated.crossorg.api.CrossOrgReferenceDataApi;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgCurrencyResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgGrantResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgInstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgTermRateResponse;
import com.mmx.order.adapter.in.rest.mapper.CrossOrgReferenceDataMapper;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.domain.model.LegalEntityCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * LODH inbound reference-data reads for thin remote clients. Every endpoint resolves the transport
 * credential to a proven principal via {@link CrossOrgIdentityResolver} (same 401/403 trust boundary as
 * routed-order intake). Grants are auto-scoped to the proven client; currencies, institutions, and
 * rates are hub-global (the client's own grant filter narrows what it can actually use).
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
    private final DelegatedGrantRepository delegatedGrantRepository;
    private final CrossOrgReferenceDataMapper mapper;

    public CrossOrgReferenceDataController(
            CrossOrgIdentityResolver identityResolver,
            ManagedCurrencyRepository currencyRepository,
            InstitutionRepository institutionRepository,
            TermRateRepository termRateRepository,
            DelegatedGrantRepository delegatedGrantRepository,
            CrossOrgReferenceDataMapper mapper) {
        this.identityResolver = identityResolver;
        this.currencyRepository = currencyRepository;
        this.institutionRepository = institutionRepository;
        this.termRateRepository = termRateRepository;
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
        resolveProven();
        return ResponseEntity.ok(mapper.toTermRateResponses(termRateRepository.findByTradingDate(tradingDate)));
    }

    @Override
    public ResponseEntity<List<CrossOrgGrantResponse>> listCrossOrgGrants() {
        LegalEntityCode proven = resolveProven();
        return ResponseEntity.ok(
                mapper.toGrantResponses(delegatedGrantRepository.findByClientLegalEntityCode(proven)));
    }

    private LegalEntityCode resolveProven() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();
        return identityResolver.resolve(request.getHeader(CREDENTIAL_HEADER));
    }
}
