package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "legal_entity")
public class LegalEntityEntity {

    @Id
    @Column(name = "code", nullable = false, length = 3)
    private String code;

    @Column(name = "organisation_code", nullable = false, length = 4)
    private String organisationCode;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "connected_hub_code", length = 3)
    private String connectedHubCode;

    public LegalEntityEntity() {}

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOrganisationCode() {
        return organisationCode;
    }

    public void setOrganisationCode(String organisationCode) {
        this.organisationCode = organisationCode;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getConnectedHubCode() {
        return connectedHubCode;
    }

    public void setConnectedHubCode(String connectedHubCode) {
        this.connectedHubCode = connectedHubCode;
    }
}
