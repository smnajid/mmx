package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.GlobalAccountEntity;
import com.mmx.order.adapter.out.persistence.entity.GlobalAccountId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataGlobalAccountRepository
        extends JpaRepository<GlobalAccountEntity, GlobalAccountId> {}
