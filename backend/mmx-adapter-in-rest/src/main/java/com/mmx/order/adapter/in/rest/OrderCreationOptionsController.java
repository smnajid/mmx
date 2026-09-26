package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.api.OrderCreationApi;
import com.mmx.order.adapter.in.rest.generated.model.ContractInfoResponse;
import com.mmx.order.adapter.in.rest.generated.model.CounterpartiesResponse;
import com.mmx.order.adapter.in.rest.generated.model.LiveContractsResponse;
import com.mmx.order.adapter.in.rest.generated.model.NoticePeriod;
import com.mmx.order.adapter.in.rest.generated.model.NoticePeriodsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OnCallCurrenciesResponse;
import com.mmx.order.adapter.in.rest.generated.model.OperationsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderType;
import com.mmx.order.adapter.in.rest.generated.model.Tenor;
import com.mmx.order.adapter.in.rest.generated.model.TenorsResponse;
import com.mmx.order.adapter.in.rest.generated.model.TermCurrenciesResponse;
import com.mmx.order.adapter.in.rest.mapper.OrderCreationRestMapper;
import com.mmx.order.application.port.in.GetContractInfoUseCase;
import com.mmx.order.application.port.in.ListLiveContractsUseCase;
import com.mmx.order.application.port.in.ListOnCallCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListOnCallCurrenciesUseCase;
import com.mmx.order.application.port.in.ListOnCallNoticePeriodsUseCase;
import com.mmx.order.application.port.in.ListOnCallOperationsUseCase;
import com.mmx.order.application.port.in.ListTermCounterpartiesUseCase;
import com.mmx.order.application.port.in.ListTermCurrenciesUseCase;
import com.mmx.order.application.port.in.ListTermOperationsUseCase;
import com.mmx.order.application.port.in.ListTermTenorsUseCase;
import com.mmx.order.domain.model.LegalEntityCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class OrderCreationOptionsController implements OrderCreationApi {

    private final ListTermCurrenciesUseCase listTermCurrenciesUseCase;
    private final ListOnCallCurrenciesUseCase listOnCallCurrenciesUseCase;
    private final ListTermOperationsUseCase listTermOperationsUseCase;
    private final ListOnCallOperationsUseCase listOnCallOperationsUseCase;
    private final ListTermTenorsUseCase listTermTenorsUseCase;
    private final ListOnCallNoticePeriodsUseCase listOnCallNoticePeriodsUseCase;
    private final ListTermCounterpartiesUseCase listTermCounterpartiesUseCase;
    private final ListOnCallCounterpartiesUseCase listOnCallCounterpartiesUseCase;
    private final GetContractInfoUseCase getContractInfoUseCase;
    private final ListLiveContractsUseCase listLiveContractsUseCase;
    private final OrderCreationRestMapper mapper;

    public OrderCreationOptionsController(
            ListTermCurrenciesUseCase listTermCurrenciesUseCase,
            ListOnCallCurrenciesUseCase listOnCallCurrenciesUseCase,
            ListTermOperationsUseCase listTermOperationsUseCase,
            ListOnCallOperationsUseCase listOnCallOperationsUseCase,
            ListTermTenorsUseCase listTermTenorsUseCase,
            ListOnCallNoticePeriodsUseCase listOnCallNoticePeriodsUseCase,
            ListTermCounterpartiesUseCase listTermCounterpartiesUseCase,
            ListOnCallCounterpartiesUseCase listOnCallCounterpartiesUseCase,
            GetContractInfoUseCase getContractInfoUseCase,
            ListLiveContractsUseCase listLiveContractsUseCase,
            OrderCreationRestMapper mapper) {
        this.listTermCurrenciesUseCase = listTermCurrenciesUseCase;
        this.listOnCallCurrenciesUseCase = listOnCallCurrenciesUseCase;
        this.listTermOperationsUseCase = listTermOperationsUseCase;
        this.listOnCallOperationsUseCase = listOnCallOperationsUseCase;
        this.listTermTenorsUseCase = listTermTenorsUseCase;
        this.listOnCallNoticePeriodsUseCase = listOnCallNoticePeriodsUseCase;
        this.listTermCounterpartiesUseCase = listTermCounterpartiesUseCase;
        this.listOnCallCounterpartiesUseCase = listOnCallCounterpartiesUseCase;
        this.getContractInfoUseCase = getContractInfoUseCase;
        this.listLiveContractsUseCase = listLiveContractsUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TermCurrenciesResponse> listTermCurrencies() {
        return ResponseEntity.ok(mapper.toTermCurrenciesResponse(listTermCurrenciesUseCase.listCurrencies()));
    }

    @Override
    public ResponseEntity<OnCallCurrenciesResponse> listOnCallCurrencies() {
        return ResponseEntity.ok(
                mapper.toOnCallCurrenciesResponse(listOnCallCurrenciesUseCase.listCurrencies()));
    }

    @Override
    public ResponseEntity<OperationsResponse> listTermOperations(String currency) {
        return ResponseEntity.ok(
                mapper.toOperationsResponse(listTermOperationsUseCase.listOperations(currency)));
    }

    @Override
    public ResponseEntity<OperationsResponse> listOnCallOperations(String currency) {
        return ResponseEntity.ok(
                mapper.toOperationsResponse(listOnCallOperationsUseCase.listOperations(currency)));
    }

    @Override
    public ResponseEntity<TenorsResponse> listTermTenors(String currency) {
        return ResponseEntity.ok(mapper.toTenorsResponse(listTermTenorsUseCase.listTenors(currency)));
    }

    @Override
    public ResponseEntity<NoticePeriodsResponse> listOnCallNoticePeriods(String currency) {
        return ResponseEntity.ok(
                mapper.toNoticePeriodsResponse(listOnCallNoticePeriodsUseCase.listNoticePeriods(currency)));
    }

    @Override
    public ResponseEntity<CounterpartiesResponse> listTermCounterparties(
            String legalEntityCode, String currency, Tenor tenor) {
        return ResponseEntity.ok(
                mapper.toCounterpartiesResponse(
                        listTermCounterpartiesUseCase.listCounterparties(
                                new LegalEntityCode(legalEntityCode),
                                currency,
                                mapper.toDomainTenor(tenor))));
    }

    @Override
    public ResponseEntity<CounterpartiesResponse> listOnCallCounterparties(
            String legalEntityCode,
            String currency,
            NoticePeriod noticePeriod,
            LocalDate valueDate) {
        return ResponseEntity.ok(
                mapper.toCounterpartiesResponse(
                        listOnCallCounterpartiesUseCase.listCounterparties(
                                new LegalEntityCode(legalEntityCode),
                                currency,
                                mapper.toDomainNoticePeriod(noticePeriod),
                                valueDate)));
    }

    @Override
    public ResponseEntity<ContractInfoResponse> getOnCallContractInfo(String contractNumber) {
        return ResponseEntity.ok(
                mapper.toContractInfoResponse(getContractInfoUseCase.getContractInfo(contractNumber)));
    }

    @Override
    public ResponseEntity<LiveContractsResponse> listLiveContracts(
            String portfolioNumber, OrderType orderType) {
        return ResponseEntity.ok(
                mapper.toLiveContractsResponse(
                        listLiveContractsUseCase.listLiveContracts(
                                portfolioNumber, mapper.toDomainOrderType(orderType))));
    }
}
