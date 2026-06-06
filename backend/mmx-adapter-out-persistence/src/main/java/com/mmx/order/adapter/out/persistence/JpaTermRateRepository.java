package com.mmx.order.adapter.out.persistence;

import com.mmx.order.adapter.out.persistence.entity.TermRateEntity;
import com.mmx.order.adapter.out.persistence.mapper.TermRatePersistenceMapper;
import com.mmx.order.adapter.out.persistence.repository.SpringDataTermRateRepository;
import com.mmx.order.application.port.out.TermRateRepository;
import com.mmx.order.application.termrate.TermRateAuditRow;
import com.mmx.order.domain.model.Tenor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

public class JpaTermRateRepository implements TermRateRepository {

    private final SpringDataTermRateRepository springDataRepository;
    private final TermRatePersistenceMapper mapper;
    private final TransactionTemplate transactionTemplate;

    public JpaTermRateRepository(
            SpringDataTermRateRepository springDataRepository,
            TermRatePersistenceMapper mapper,
            TransactionTemplate transactionTemplate) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void replaceAllForDate(LocalDate tradingDate, List<TermRateAuditRow> rows) {
        transactionTemplate.executeWithoutResult(
                status -> {
                    springDataRepository.deleteByTradingDate(tradingDate);
                    List<TermRateEntity> entities = rows.stream().map(mapper::toEntity).toList();
                    springDataRepository.saveAll(entities);
                });
    }

    @Override
    public List<TermRateAuditRow> findByTradingDate(LocalDate tradingDate) {
        return springDataRepository
                .findByTradingDateOrderByInstitutionCodeAscCurrencyAscTenorAsc(tradingDate)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<LocalDate> findDistinctTradingDatesDesc() {
        return springDataRepository.findDistinctTradingDatesDesc();
    }

    @Override
    public List<TermRateAuditRow> findLatestRatePerInstitution(String currency, Tenor tenor) {
        return springDataRepository.findLatestRatePerInstitution(currency, tenor.getCode()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<String> findDistinctCurrenciesWithTermRates() {
        return springDataRepository.findDistinctCurrenciesWithTermRates();
    }
}
