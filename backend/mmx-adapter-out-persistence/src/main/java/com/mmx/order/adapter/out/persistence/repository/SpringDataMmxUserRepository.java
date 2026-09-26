package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.MmxUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMmxUserRepository extends JpaRepository<MmxUserEntity, String> {}
