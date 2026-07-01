package com.mmx.order.adapter.out.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "mmx_user")
public class MmxUserEntity {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<MmxUserScopeEntity> scopes = new HashSet<>();

    public MmxUserEntity() {}

    public MmxUserEntity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Set<MmxUserScopeEntity> getScopes() {
        return scopes;
    }

    public void setScopes(Set<MmxUserScopeEntity> scopes) {
        this.scopes = scopes;
    }
}
