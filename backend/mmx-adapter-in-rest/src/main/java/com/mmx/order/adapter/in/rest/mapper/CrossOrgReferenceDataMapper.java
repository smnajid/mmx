package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgCurrencyResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgGrantResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgInstitutionResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.CrossOrgTermRateResponse;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.NoticePeriodCode;
import com.mmx.order.adapter.in.rest.generated.crossorg.model.TenorCode;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class CrossOrgReferenceDataMapper {

    public List<CrossOrgCurrencyResponse> toCurrencyResponses(List<ManagedCurrency> currencies) {
        return currencies.stream().map(this::toCurrencyResponse).toList();
    }

    public List<CrossOrgInstitutionResponse> toInstitutionResponses(List<Institution> institutions) {
        return institutions.stream().map(this::toInstitutionResponse).toList();
    }

    public List<CrossOrgTermRateResponse> toTermRateResponses(List<TermRateAuditRow> rows) {
        return rows.stream().map(this::toTermRateResponse).toList();
    }

    public List<CrossOrgGrantResponse> toGrantResponses(List<DelegatedInstitutionGrant> grants) {
        return grants.stream().map(this::toGrantResponse).toList();
    }

    private CrossOrgCurrencyResponse toCurrencyResponse(ManagedCurrency c) {
        CrossOrgCurrencyResponse r = new CrossOrgCurrencyResponse()
                .code(c.getCode())
                .active(c.isActive())
                .minSubscriptionAmount(c.getMinSubscriptionAmount().doubleValue())
                .minIncreaseDecreaseAmount(c.getMinIncreaseDecreaseAmount().doubleValue());
        for (Tenor t : c.getEnabledTenors()) {
            r.addEnabledTenorsItem(toTenorCode(t));
        }
        for (NoticePeriod np : c.getEnabledNoticePeriods()) {
            r.addEnabledNoticePeriodsItem(toNoticePeriodCode(np));
        }
        return r;
    }

    private CrossOrgInstitutionResponse toInstitutionResponse(Institution i) {
        return new CrossOrgInstitutionResponse()
                .institutionCode(i.getInstitutionCode())
                .displayName(i.getDisplayName())
                .active(i.isActive());
    }

    private CrossOrgTermRateResponse toTermRateResponse(TermRateAuditRow row) {
        return new CrossOrgTermRateResponse()
                .tradingDate(row.tradingDate())
                .institutionCode(row.institutionCode())
                .currency(row.currency())
                .tenor(toTenorCode(row.tenor()))
                .rate(row.rate().doubleValue());
    }

    private CrossOrgGrantResponse toGrantResponse(DelegatedInstitutionGrant g) {
        CrossOrgGrantResponse r = new CrossOrgGrantResponse()
                .hubInstitutionCode(g.getHubInstitutionCode())
                .clientLegalEntityCode(g.getClientLegalEntityCode().value())
                .currency(g.getCurrency())
                .active(g.isActive());
        for (Tenor t : g.getEnabledTenors()) {
            r.addEnabledTenorsItem(toTenorCode(t));
        }
        for (NoticePeriod np : g.getEnabledNoticePeriods()) {
            r.addEnabledNoticePeriodsItem(toNoticePeriodCode(np));
        }
        return r;
    }

    private static TenorCode toTenorCode(Tenor tenor) {
        return TenorCode.fromValue(tenor.getCode());
    }

    private static NoticePeriodCode toNoticePeriodCode(NoticePeriod period) {
        return NoticePeriodCode.fromValue(period.getCode());
    }
}
