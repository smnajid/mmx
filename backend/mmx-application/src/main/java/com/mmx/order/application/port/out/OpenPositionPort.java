package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.OpenContractPosition;

import java.util.Optional;

public interface OpenPositionPort {

    Optional<OpenContractPosition> findOpenByContractNumber(ContractNumber contractNumber);
}
