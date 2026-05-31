package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.TermRateEntity;
import com.mmx.order.adapter.out.persistence.entity.TermRateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataTermRateRepository extends JpaRepository<TermRateEntity, TermRateId> {

    void deleteByTradingDate(LocalDate tradingDate);

    List<TermRateEntity> findByTradingDateOrderByInstitutionCodeAscCurrencyAscTenorAsc(LocalDate tradingDate);

    @Query(
            """
            SELECT DISTINCT t.tradingDate
            FROM TermRateEntity t
            ORDER BY t.tradingDate DESC
            """)
    List<LocalDate> findDistinctTradingDatesDesc();
}
