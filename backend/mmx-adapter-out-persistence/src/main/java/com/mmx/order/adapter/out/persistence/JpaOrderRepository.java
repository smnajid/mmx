package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataOrderRepository;
import com.mmx.order.application.port.out.OrderRepository;
import com.mmx.order.domain.model.*;

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
