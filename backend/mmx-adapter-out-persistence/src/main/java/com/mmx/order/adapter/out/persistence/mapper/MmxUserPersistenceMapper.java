package com.mmx.order.adapter.out.persistence.mapper;

import com.mmx.order.adapter.out.persistence.entity.MmxUserEntity;
import com.mmx.order.adapter.out.persistence.entity.MmxUserScopeEntity;
import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;
import com.mmx.order.domain.model.Role;
import com.mmx.order.domain.model.UserScope;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MmxUserPersistenceMapper {

    public MmxUser toDomain(MmxUserEntity entity) {
        Set<UserScope> scopes = new HashSet<>();
        for (MmxUserScopeEntity scopeEntity : entity.getScopes()) {
            scopes.add(
                    new UserScope(
                            new com.mmx.order.domain.model.LegalEntityCode(scopeEntity.getLegalEntityCode()),
                            Role.valueOf(scopeEntity.getRole())));
        }
        return MmxUser.create(new MmxUserId(entity.getId()), scopes);
    }

    public MmxUserEntity toEntity(MmxUser user) {
        MmxUserEntity entity = new MmxUserEntity(user.getId().value());
        Set<MmxUserScopeEntity> scopeEntities = new HashSet<>();
        for (UserScope scope : user.getScopes()) {
            MmxUserScopeEntity scopeEntity =
                    new MmxUserScopeEntity(
                            user.getId().value(),
                            scope.legalEntityCode().value(),
                            scope.role().name());
            scopeEntity.setUser(entity);
            scopeEntities.add(scopeEntity);
        }
        entity.setScopes(scopeEntities);
        return entity;
    }
}
