package com.mmx.order.adapter.out.persistence.repository;

import com.mmx.order.adapter.out.persistence.entity.OrganisationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrganisationRepository extends JpaRepository<OrganisationEntity, String> {}
