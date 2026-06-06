package com.mmx.order.adapter.in.rest.mapper;

import com.mmx.order.adapter.in.rest.generated.model.ContractInfoResponse;
import com.mmx.order.adapter.in.rest.generated.model.CounterpartiesResponse;
import com.mmx.order.adapter.in.rest.generated.model.CounterpartyOption;
import com.mmx.order.adapter.in.rest.generated.model.NoticePeriod;
import com.mmx.order.adapter.in.rest.generated.model.NoticePeriodsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OnCallCurrenciesResponse;
import com.mmx.order.adapter.in.rest.generated.model.OperationOption;
import com.mmx.order.adapter.in.rest.generated.model.OperationsResponse;
import com.mmx.order.adapter.in.rest.generated.model.OrderOperation;
import com.mmx.order.adapter.in.rest.generated.model.Tenor;
import com.mmx.order.adapter.in.rest.generated.model.TenorsResponse;
import com.mmx.order.adapter.in.rest.generated.model.TermCurrenciesResponse;
import com.mmx.order.application.ordercreation.ContractInfoResult;
import com.mmx.order.application.ordercreation.CounterpartiesResult;
import com.mmx.order.application.ordercreation.NoticePeriodsResult;
import com.mmx.order.application.ordercreation.OnCallCurrenciesResult;
import com.mmx.order.application.ordercreation.OperationsResult;
import com.mmx.order.application.ordercreation.OrderCreationCounterparty;
import com.mmx.order.application.ordercreation.OrderCreationOperation;
import com.mmx.order.application.ordercreation.TermCurrenciesResult;
import com.mmx.order.application.ordercreation.TenorsResult;
import com.mmx.order.domain.exception.InvalidOrderException;
import org.springframework.stereotype.Component;

@Component
public class OrderCreationRestMapper {

    public TermCurrenciesResponse toTermCurrenciesResponse(TermCurrenciesResult result) {
        return new TermCurrenciesResponse()
                .tradingDate(result.tradingDate())
                .currencies(result.currencies());
    }

    public OnCallCurrenciesResponse toOnCallCurrenciesResponse(OnCallCurrenciesResult result) {
        return new OnCallCurrenciesResponse().currencies(result.currencies());
    }

    public OperationsResponse toOperationsResponse(OperationsResult result) {
        return new OperationsResponse()
                .operations(result.operations().stream().map(this::toOperationOption).toList());
    }

    public TenorsResponse toTenorsResponse(TenorsResult result) {
        return new TenorsResponse()
                .tenors(result.tenors().stream().map(this::toApiTenor).toList());
    }

    public NoticePeriodsResponse toNoticePeriodsResponse(NoticePeriodsResult result) {
        return new NoticePeriodsResponse()
                .noticePeriods(result.noticePeriods().stream().map(this::toApiNoticePeriod).toList());
    }

    public CounterpartiesResponse toCounterpartiesResponse(CounterpartiesResult result) {
        return new CounterpartiesResponse()
                .counterparties(result.counterparties().stream().map(this::toCounterpartyOption).toList());
    }

    public ContractInfoResponse toContractInfoResponse(ContractInfoResult result) {
        return new ContractInfoResponse()
                .currency(result.currency())
                .noticePeriod(toApiNoticePeriod(result.noticePeriod()));
    }

    public com.mmx.order.domain.model.Tenor toDomainTenor(Tenor apiTenor) {
        if (apiTenor == null) {
            return null;
        }
        String code = apiTenor.getValue();
        for (com.mmx.order.domain.model.Tenor tenor : com.mmx.order.domain.model.Tenor.values()) {
            if (tenor.getCode().equals(code)) {
                return tenor;
            }
        }
        throw new InvalidOrderException("Unknown tenor: " + code);
    }

    public com.mmx.order.domain.model.NoticePeriod toDomainNoticePeriod(NoticePeriod apiNoticePeriod) {
        if (apiNoticePeriod == null) {
            return null;
        }
        return switch (apiNoticePeriod) {
            case _24_H -> com.mmx.order.domain.model.NoticePeriod._24H;
            case _48_H -> com.mmx.order.domain.model.NoticePeriod._48H;
        };
    }

    private OperationOption toOperationOption(OrderCreationOperation operation) {
        return new OperationOption()
                .operation(OrderOperation.fromValue(operation.operation().name()))
                .minAmount(operation.minAmount().doubleValue());
    }

    private CounterpartyOption toCounterpartyOption(OrderCreationCounterparty counterparty) {
        return new CounterpartyOption()
                .institutionCode(counterparty.institutionCode())
                .displayName(counterparty.displayName())
                .rate(counterparty.rate().doubleValue())
                .rateDate(counterparty.rateDate())
                .indicative(counterparty.indicative());
    }

    private Tenor toApiTenor(com.mmx.order.domain.model.Tenor tenor) {
        return Tenor.fromValue(tenor.getCode());
    }

    private NoticePeriod toApiNoticePeriod(com.mmx.order.domain.model.NoticePeriod noticePeriod) {
        return switch (noticePeriod) {
            case _24H -> NoticePeriod._24_H;
            case _48H -> NoticePeriod._48_H;
        };
    }
}
