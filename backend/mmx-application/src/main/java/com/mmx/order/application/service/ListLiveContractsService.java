package com.mmx.order.application.service;

import com.mmx.order.application.ordercreation.LiveContractResult;
import com.mmx.order.application.ordercreation.LiveContractsResult;
import com.mmx.order.application.port.in.ListLiveContractsUseCase;
import com.mmx.order.application.port.out.Clock;
import com.mmx.order.application.port.out.ExecutedSubscriptionContract;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.policy.ContractLivenessPolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public final class ListLiveContractsService implements ListLiveContractsUseCase {

    private final OrderRepository orderRepository;
    private final Clock clock;
    private final ContractLivenessPolicy livenessPolicy;

    public ListLiveContractsService(OrderRepository orderRepository, Clock clock) {
        this(orderRepository, clock, new ContractLivenessPolicy());
    }

    ListLiveContractsService(
            OrderRepository orderRepository, Clock clock, ContractLivenessPolicy livenessPolicy) {
        this.orderRepository = orderRepository;
        this.clock = clock;
        this.livenessPolicy = livenessPolicy;
    }

    @Override
    public LiveContractsResult listLiveContracts(String portfolioNumber, OrderType orderType) {
        List<ExecutedSubscriptionContract> candidates =
                orderRepository.findExecutedSubscriptionsByPortfolioAndOrderType(portfolioNumber, orderType);
        if (candidates.isEmpty()) {
            return new LiveContractsResult(List.of());
        }

        List<String> contractNumbers = candidates.stream().map(ExecutedSubscriptionContract::contractNumber).toList();
        Set<String> redeemedContractNumbers =
                orderRepository.findContractNumbersWithNonCancelledRedemption(contractNumbers);
        LocalDate today = clock.today();

        List<LiveContractResult> contracts =
                candidates.stream()
                        .filter(candidate -> isLive(candidate, redeemedContractNumbers, today))
                        .map(this::toResult)
                        .toList();
        return new LiveContractsResult(contracts);
    }

    private boolean isLive(
            ExecutedSubscriptionContract candidate, Set<String> redeemedContractNumbers, LocalDate today) {
        if (candidate.orderType() == OrderType.ON_CALL) {
            return livenessPolicy.isOnCallLive(redeemedContractNumbers.contains(candidate.contractNumber()));
        }
        return livenessPolicy.isTermLive(candidate.valueDate(), candidate.tenor(), today);
    }

    private LiveContractResult toResult(ExecutedSubscriptionContract candidate) {
        LocalDate endDate =
                candidate.orderType() == OrderType.TERM
                        ? livenessPolicy.termEndDate(candidate.valueDate(), candidate.tenor())
                        : null;
        return new LiveContractResult(
                candidate.contractNumber(),
                candidate.orderType(),
                candidate.currency(),
                candidate.noticePeriod(),
                candidate.tenor(),
                candidate.valueDate(),
                endDate,
                candidate.originalAmount());
    }
}
