package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "organisation")
public class OrganisationEntity {

    @Id
    @Column(name = "code", nullable = false, length = 4)
    private String code;

    public OrganisationEntity() {}

    public OrganisationEntity(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
