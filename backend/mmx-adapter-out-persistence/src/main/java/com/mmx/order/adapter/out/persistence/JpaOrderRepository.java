package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.out.ExecutedSubscriptionContract;
import com.mmx.order.application.port.out.ExecutedSubscriptionContractInfo;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.exception.DuplicateRoutedHubOrderException;
import com.mmx.order.domain.model.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository springDataRepository;
    private final OrderPersistenceMapper mapper;

    public JpaOrderRepository(SpringDataOrderRepository springDataRepository,
                               OrderPersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public MoneyMarketOrder save(MoneyMarketOrder order) {
        var entity = mapper.toEntity(order);
        // Routed hub-side orders carry the cross-org idempotency key (originatingLegalEntityCode,
        // routingId). Flush synchronously so the partial unique index violation surfaces inside this
        // method and can be translated to the domain exception the application layer catches. Local
        // desk orders keep the deferred-flush path; their constraint violations surface at commit and
        // propagate as DataIntegrityViolationException (existing behaviour).
        if (order.getOriginatingLegalEntityCode() != null && order.getRoutingId() != null) {
            try {
                var saved = springDataRepository.saveAndFlush(entity);
                return mapper.toDomain(saved);
            } catch (DataIntegrityViolationException collision) {
                throw new DuplicateRoutedHubOrderException(
                        "Routed hub-side order collided on (originatingLegalEntityCode="
                                + order.getOriginatingLegalEntityCode().value()
                                + ", routingId=" + order.getRoutingId().value() + ")",
                        collision);
            }
        }
        var saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<MoneyMarketOrder> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<MoneyMarketOrder> findByExternalOrderReference(ExternalOrderReference reference) {
        return springDataRepository
                .findByExternalOrderReference(reference.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<MoneyMarketOrder> findByLegalEntityAndExternalReference(
            LegalEntityCode legalEntityCode, ExternalOrderReference reference) {
        return springDataRepository
                .findByLegalEntityCodeAndExternalOrderReference(
                        legalEntityCode.value(), reference.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<MoneyMarketOrder> findRoutedClientOrderByRoutingId(RoutingId routingId) {
        return springDataRepository
                .findByRoutingIdAndOriginatingLegalEntityCodeIsNull(routingId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<MoneyMarketOrder> findHubOrderByRoutingId(RoutingId routingId) {
        return springDataRepository
                .findByRoutingIdAndOriginatingLegalEntityCodeIsNotNull(routingId.value())
                .map(mapper::toDomain);
    }

    @Override
    public Optional<MoneyMarketOrder> findHubOrderByOriginatingAndRoutingId(
            LegalEntityCode originatingLegalEntityCode, RoutingId routingId) {
        return springDataRepository
                .findByRoutingIdAndOriginatingLegalEntityCode(
                        routingId.value(), originatingLegalEntityCode.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<MoneyMarketOrder> findByStatusAndOrderType(
            LegalEntityCode legalEntityCode, OrderStatus status, OrderType orderType) {
        return springDataRepository
                .findByLegalEntityCodeAndStatusAndOrderType(
                        legalEntityCode.value(), status.name(), orderType.name())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public OrderPage findReceivedPageByOrderType(
            LegalEntityCode legalEntityCode,
            OrderType orderType,
            Optional<LocalDate> valueDateFrom,
            Optional<LocalDate> valueDateTo,
            int page,
            int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be non-negative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (valueDateFrom.isEmpty() != valueDateTo.isEmpty()) {
            throw new IllegalArgumentException("valueDate range: both bounds required or neither");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "valueDate"));
        String status = OrderStatus.RECEIVED.name();
        String type = orderType.name();
        String entityCode = legalEntityCode.value();
        Page<OrderEntity> slice =
                valueDateFrom.isPresent()
                        ? springDataRepository
                                .findByLegalEntityCodeAndStatusAndOrderTypeAndValueDateBetweenOrderByValueDateAsc(
                                        entityCode,
                                        status,
                                        type,
                                        valueDateFrom.get(),
                                        valueDateTo.get(),
                                        pageable)
                        : springDataRepository.findByLegalEntityCodeAndStatusAndOrderTypeOrderByValueDateAsc(
                                entityCode, status, type, pageable);
        return new OrderPage(
                slice.getContent().stream().map(mapper::toDomain).toList(),
                slice.getTotalElements(),
                slice.getNumber(),
                slice.getSize());
    }

    @Override
    public List<MoneyMarketOrder> findByAssignedTraderIdAndStatus(
            LegalEntityCode legalEntityCode, TraderId traderId, OrderStatus status) {
        return springDataRepository
                .findByLegalEntityCodeAndAssignedTraderIdAndStatus(
                        legalEntityCode.value(), traderId.value(), status.name())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ExecutedSubscriptionContractInfo> findExecutedSubscriptionByContractNumber(
            String contractNumber) {
        return springDataRepository
                .findByGeneratedContractNumberAndOrderTypeAndOrderOperationAndStatus(
                        contractNumber,
                        OrderType.ON_CALL.name(),
                        OrderOperation.SUBSCRIPTION.name(),
                        OrderStatus.EXECUTED.name())
                .map(JpaOrderRepository::toExecutedSubscriptionContractInfo);
    }

    @Override
    public List<ExecutedSubscriptionContract> findExecutedSubscriptionsByPortfolioAndOrderType(
            String portfolioNumber, OrderType orderType) {
        return springDataRepository
                .findByPortfolioNumberAndOrderTypeAndOrderOperationAndStatusOrderByValueDateAsc(
                        portfolioNumber,
                        orderType.name(),
                        OrderOperation.SUBSCRIPTION.name(),
                        OrderStatus.EXECUTED.name())
                .stream()
                .map(JpaOrderRepository::toExecutedSubscriptionContract)
                .toList();
    }

    @Override
    public Set<String> findContractNumbersWithNonCancelledRedemption(List<String> contractNumbers) {
        if (contractNumbers.isEmpty()) {
            return Collections.emptySet();
        }
        return springDataRepository.findRedeemedContractNumbers(
                contractNumbers, OrderOperation.REDEMPTION.name(), OrderStatus.CANCELLED.name());
    }

    private static ExecutedSubscriptionContractInfo toExecutedSubscriptionContractInfo(OrderEntity entity) {
        if (entity.getNoticePeriod() == null) {
            throw new IllegalStateException("Executed OnCall subscription missing notice period");
        }
        if (entity.getInstitutionCode() == null || entity.getInstitutionCode().isBlank()) {
            throw new IllegalStateException("Executed OnCall subscription missing institution code");
        }
        if (entity.getCounterparty() == null || entity.getCounterparty().isBlank()) {
            throw new IllegalStateException("Executed OnCall subscription missing counterparty");
        }
        return new ExecutedSubscriptionContractInfo(
                entity.getCurrency(),
                NoticePeriod.valueOf(entity.getNoticePeriod()),
                entity.getInstitutionCode(),
                entity.getCounterparty());
    }

    private static ExecutedSubscriptionContract toExecutedSubscriptionContract(OrderEntity entity) {
        if (entity.getGeneratedContractNumber() == null) {
            throw new IllegalStateException("Executed subscription missing contract number");
        }
        OrderType orderType = OrderType.valueOf(entity.getOrderType());
        NoticePeriod noticePeriod =
                entity.getNoticePeriod() != null ? NoticePeriod.valueOf(entity.getNoticePeriod()) : null;
        Tenor tenor = entity.getTenor() != null ? Tenor.valueOf(entity.getTenor()) : null;
        return new ExecutedSubscriptionContract(
                entity.getGeneratedContractNumber(),
                orderType,
                entity.getCurrency(),
                noticePeriod,
                tenor,
                entity.getValueDate(),
                entity.getAmount());
    }
}
