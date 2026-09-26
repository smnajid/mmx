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

    @Query(
            """
            SELECT t FROM TermRateEntity t
            INNER JOIN InstitutionEntity i ON i.institutionCode = t.institutionCode AND i.active = true
            WHERE t.currency = :currency AND t.tenor = :tenor
            AND t.tradingDate = (
                SELECT MAX(t2.tradingDate) FROM TermRateEntity t2
                WHERE t2.institutionCode = t.institutionCode
                AND t2.currency = :currency AND t2.tenor = :tenor
            )
            ORDER BY t.institutionCode
            """)
    List<TermRateEntity> findLatestRatePerInstitution(String currency, String tenor);

    @Query(
            """
            SELECT DISTINCT t.currency FROM TermRateEntity t
            INNER JOIN InstitutionEntity i ON i.institutionCode = t.institutionCode AND i.active = true
            ORDER BY t.currency
            """)
    List<String> findDistinctCurrenciesWithTermRates();
}
