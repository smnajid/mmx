package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.OrderEntity;
import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.in.OrderPage;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    public List<MoneyMarketOrder> findByStatusAndOrderType(OrderStatus status, OrderType orderType) {
        return springDataRepository
                .findByStatusAndOrderType(status.name(), orderType.name())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public OrderPage findReceivedPageByOrderType(
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
        Page<OrderEntity> slice =
                valueDateFrom.isPresent()
                        ? springDataRepository.findByStatusAndOrderTypeAndValueDateBetweenOrderByValueDateAsc(
                                status, type, valueDateFrom.get(), valueDateTo.get(), pageable)
                        : springDataRepository.findByStatusAndOrderTypeOrderByValueDateAsc(
                                status, type, pageable);
        return new OrderPage(
                slice.getContent().stream().map(mapper::toDomain).toList(),
                slice.getTotalElements(),
                slice.getNumber(),
                slice.getSize());
    }

    @Override
    public List<MoneyMarketOrder> findByAssignedTraderIdAndStatus(TraderId traderId, OrderStatus status) {
        return springDataRepository
                .findByAssignedTraderIdAndStatus(traderId.value(), status.name())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<MoneyMarketOrder> findByAssignedTraderIdAndStatusAndOrderType(
            TraderId traderId, OrderStatus status, OrderType orderType) {
        return springDataRepository
                .findByAssignedTraderIdAndStatusAndOrderType(
                        traderId.value(), status.name(), orderType.name())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}
