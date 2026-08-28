package com.mmx.order.adapter.in.rest.crossorg;

import com.mmx.order.adapter.in.rest.mapper.CrossOrgReferenceDataMapper;
import com.mmx.order.application.port.out.CrossOrgCredentialBinder;
import com.mmx.order.application.port.out.CrossOrgMembershipPort;
import com.mmx.order.application.port.out.DelegatedGrantRepository;
import com.mmx.order.application.port.out.InstitutionRepository;
import com.mmx.order.application.port.out.ManagedCurrencyRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.DelegatedInstitutionGrant;
import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.ManagedCurrency;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.Tenor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Thin-client reference-data reads: currencies, institutions, term-rates (hub-global), and grants
 * (scoped to the proven client). All endpoints share the same transport-credential trust boundary as
 * routed-order intake.
 *
 * <p>Spec: {@code order-routing} — thin-client reference-data reads.
 */
@Tag("fast")
@ExtendWith(MockitoExtension.class)
class CrossOrgReferenceDataControllerTest {

    private static final String CREDENTIAL = "key-cgd";
    private static final LegalEntityCode PROVEN = new LegalEntityCode("CGD");

    @Mock
    CrossOrgCredentialBinder credentialBinder;
    @Mock
    CrossOrgMembershipPort membershipPort;
    @Mock
    ManagedCurrencyRepository currencyRepository;
    @Mock
    InstitutionRepository institutionRepository;
    @Mock
    TermRateRepository termRateRepository;
    @Mock
    DelegatedGrantRepository delegatedGrantRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CrossOrgIdentityResolver resolver = new CrossOrgIdentityResolver(credentialBinder, membershipPort);
        CrossOrgReferenceDataMapper mapper = new CrossOrgReferenceDataMapper();
        CrossOrgReferenceDataController controller = new CrossOrgReferenceDataController(
                resolver, currencyRepository, institutionRepository, termRateRepository,
                delegatedGrantRepository, mapper);
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new CrossOrgExceptionHandler())
                .build();
        lenient().when(credentialBinder.bindOriginatingLegalEntity(CREDENTIAL)).thenReturn(Optional.of(PROVEN));
        lenient().when(membershipPort.isRemoteTradingClientOfThisHub(PROVEN)).thenReturn(true);
    }

    @Test
    void listCurrencies_returnsHubManagedCurrencies() throws Exception {
        when(currencyRepository.findAll()).thenReturn(List.of(
                new ManagedCurrency("EUR", true,
                        new BigDecimal("10000.00"), new BigDecimal("1000.00"),
                        Set.of(Tenor._3M, Tenor._6M), Set.of(NoticePeriod._24H)),
                new ManagedCurrency("USD", true,
                        new BigDecimal("50000.00"), new BigDecimal("5000.00"),
                        Set.of(Tenor._1M), Set.of())));

        mockMvc.perform(get("/api/v1/cross-org/reference/currencies")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("EUR"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].minSubscriptionAmount").value(10000.0))
                .andExpect(jsonPath("$[0].enabledTenors[0]").value("3M"))
                .andExpect(jsonPath("$[0].enabledNoticePeriods[0]").value("24H"))
                .andExpect(jsonPath("$[1].code").value("USD"))
                .andExpect(jsonPath("$[1].enabledNoticePeriods").isEmpty());
    }

    @Test
    void listInstitutions_all_returnsAllInstitutions() throws Exception {
        when(institutionRepository.findAll()).thenReturn(List.of(
                new Institution("HSBC-01", "BankCo International", true),
                new Institution("BARC-02", "Barclays Capital", false)));

        mockMvc.perform(get("/api/v1/cross-org/reference/institutions")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].institutionCode").value("HSBC-01"))
                .andExpect(jsonPath("$[0].displayName").value("BankCo International"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].institutionCode").value("BARC-02"))
                .andExpect(jsonPath("$[1].active").value(false));

        verify(institutionRepository).findAll();
    }

    @Test
    void listInstitutions_activeOnly_returnsActiveOnly() throws Exception {
        when(institutionRepository.findActive()).thenReturn(List.of(
                new Institution("HSBC-01", "BankCo International", true)));

        mockMvc.perform(get("/api/v1/cross-org/reference/institutions")
                        .param("activeOnly", "true")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].institutionCode").value("HSBC-01"));

        verify(institutionRepository).findActive();
    }

    @Test
    void listTermRates_returnsRatesForTradingDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 1);
        when(termRateRepository.findByTradingDate(date)).thenReturn(List.of(
                new TermRateAuditRow(date, "HSBC-01", "EUR", Tenor._3M,
                        new BigDecimal("3.25"), null, null)));

        mockMvc.perform(get("/api/v1/cross-org/reference/term-rates")
                        .param("tradingDate", "2026-08-01")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tradingDate").value("2026-08-01"))
                .andExpect(jsonPath("$[0].institutionCode").value("HSBC-01"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].tenor").value("3M"))
                .andExpect(jsonPath("$[0].rate").value(3.25));
    }

    @Test
    void listGrants_scopedToProvenClient() throws Exception {
        when(delegatedGrantRepository.findByClientLegalEntityCode(PROVEN)).thenReturn(List.of(
                new DelegatedInstitutionGrant(
                        "HSBC-01", PROVEN, "EUR",
                        Set.of(Tenor._3M), Set.of(NoticePeriod._24H), true)));

        mockMvc.perform(get("/api/v1/cross-org/reference/grants")
                        .header("X-MMX-CrossOrg-Key", CREDENTIAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hubInstitutionCode").value("HSBC-01"))
                .andExpect(jsonPath("$[0].clientLegalEntityCode").value("CGD"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].enabledTenors[0]").value("3M"))
                .andExpect(jsonPath("$[0].enabledNoticePeriods[0]").value("24H"))
                .andExpect(jsonPath("$[0].active").value(true));

        verify(delegatedGrantRepository).findByClientLegalEntityCode(PROVEN);
    }
}
