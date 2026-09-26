package com.mmx.order.application.command;

import com.mmx.order.domain.model.ContractNumber;
import com.mmx.order.domain.model.ExternalOrderReference;
import com.mmx.order.domain.model.LegalEntityCode;
import com.mmx.order.domain.model.NoticePeriod;
import com.mmx.order.domain.model.OrderOperation;
import com.mmx.order.domain.model.OrderType;
import com.mmx.order.domain.model.PortfolioNumber;
import com.mmx.order.domain.model.Tenor;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceiveOrderCommand(
        ExternalOrderReference externalOrderReference,
        LegalEntityCode legalEntityCode,
        OrderType orderType,
        OrderOperation orderOperation,
        PortfolioNumber portfolioNumber,
        String currency,
        BigDecimal amount,
        LocalDate valueDate,
        BigDecimal minimumRate,
        Tenor tenor,
        NoticePeriod noticePeriod,
        ContractNumber sourceContractNumber,
        String institutionCode
) {}
