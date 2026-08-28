package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.ProxyInstitutionRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OnCallRateSegment;
import com.mmx.order.domain.model.Tenor;
import com.mmx.order.domain.model.ThinProxyInstitution;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class OrderCreationDelegatedCounterpartySupport {

    private OrderCreationDelegatedCounterpartySupport() {}

    static List<OrderCreationCounterparty> termCounterpartiesForClient(
            LegalEntityCode clientCode,
            String currency,
            Tenor tenor,
            DelegatedGrantRepository grantRepository,
            ProxyInstitutionRepository proxyRepository,
            List<TermRateAuditRow> hubRates,
            LocalDate today) {
        Set<String> grantedHubInstitutions =
                grantRepository.findByClientLegalEntityCode(clientCode).stream()
                        .filter(DelegatedInstitutionGrant::isActive)
                        .filter(grant -> grant.getCurrency().equals(currency))
                        .filter(grant -> grant.getEnabledTenors().contains(tenor))
                        .map(DelegatedInstitutionGrant::getHubInstitutionCode)
                        .collect(Collectors.toSet());
        Map<String, ThinProxyInstitution> proxiesByHub = activeProxiesByHubInstitution(clientCode, proxyRepository);

        List<OrderCreationCounterparty> counterparties = new ArrayList<>();
        for (TermRateAuditRow row : hubRates) {
            if (!grantedHubInstitutions.contains(row.institutionCode())) {
                continue;
            }
            ThinProxyInstitution proxy = proxiesByHub.get(row.institutionCode());
            if (proxy == null) {
                continue;
            }
            counterparties.add(toTermCounterparty(proxy, row, today));
        }
        counterparties.sort(Comparator.comparing(OrderCreationCounterparty::rate).reversed());
        return counterparties;
    }

    static List<OrderCreationCounterparty> onCallCounterpartiesForClient(
            LegalEntityCode clientCode,
            String currency,
            NoticePeriod noticePeriod,
            DelegatedGrantRepository grantRepository,
            ProxyInstitutionRepository proxyRepository,
            List<OnCallRateSegment> hubSegments,
            LocalDate today) {
        Set<String> grantedHubInstitutions =
                grantRepository.findByClientLegalEntityCode(clientCode).stream()
                        .filter(DelegatedInstitutionGrant::isActive)
                        .filter(grant -> grant.getCurrency().equals(currency))
                        .filter(grant -> grant.getEnabledNoticePeriods().contains(noticePeriod))
                        .map(DelegatedInstitutionGrant::getHubInstitutionCode)
                        .collect(Collectors.toSet());
        Map<String, ThinProxyInstitution> proxiesByHub = activeProxiesByHubInstitution(clientCode, proxyRepository);

        List<OrderCreationCounterparty> counterparties = new ArrayList<>();
        for (OnCallRateSegment segment : hubSegments) {
            String hubInstitutionCode = segment.getCurveKey().institutionCode();
            if (!grantedHubInstitutions.contains(hubInstitutionCode)) {
                continue;
               }
            ThinProxyInstitution proxy = proxiesByHub.get(hubInstitutionCode);
            if (proxy == null) {
                continue;
            }
            counterparties.add(toOnCallCounterparty(proxy, segment, today));
        }
        counterparties.sort(Comparator.comparing(OrderCreationCounterparty::rate).reversed());
        return counterparties;
    }

    private static Map<String, ThinProxyInstitution> activeProxiesByHubInstitution(
            LegalEntityCode clientCode, ProxyInstitutionRepository proxyRepository) {
        Map<String, ThinProxyInstitution> proxiesByHub = new HashMap<>();
        for (ThinProxyInstitution proxy : proxyRepository.findByClientLegalEntity(clientCode)) {
            if (proxy.isActive()) {
                proxiesByHub.putIfAbsent(proxy.getHubInstitutionCode(), proxy);
            }
        }
        return proxiesByHub;
    }

    private static OrderCreationCounterparty toTermCounterparty(
            ThinProxyInstitution proxy, TermRateAuditRow row, LocalDate today) {
        return new OrderCreationCounterparty(
                proxy.getInstitutionCode(),
                proxy.getDisplayName(),
                row.rate(),
                row.tradingDate(),
                row.tradingDate().isBefore(today));
    }

    private static OrderCreationCounterparty toOnCallCounterparty(
            ThinProxyInstitution proxy, OnCallRateSegment segment, LocalDate today) {
        LocalDate rateDate = segment.getValueDate();
        return new OrderCreationCounterparty(
                proxy.getInstitutionCode(),
                proxy.getDisplayName(),
                segment.getRate(),
                rateDate,
                rateDate.isBefore(today));
    }
}
