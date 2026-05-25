package com.mmx.order.adapter.out.integration;

import com.mmx.order.application.port.out.OpenPositionPort;
import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.OpenContractPosition;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test and local-dev stub for external PositionApi. Production SHOULD replace with HTTP adapter when available.
 */
public final class InMemoryOpenPositionPort implements OpenPositionPort {

    private final Map<String, OpenContractPosition> positions = new ConcurrentHashMap<>();

    public void register(OpenContractPosition position) {
        positions.put(position.contractNumber().value(), position);
    }

    public void clear() {
        positions.clear();
    }

    @Override
    public Optional<OpenContractPosition> findOpenByContractNumber(ContractNumber contractNumber) {
        return Optional.ofNullable(positions.get(contractNumber.value()));
    }
}
