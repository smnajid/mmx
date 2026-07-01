package com.mmx.order.application.port.out;

import com.mmx.order.domain.model.MmxUser;
import com.mmx.order.domain.model.MmxUserId;

import java.util.Optional;

public interface MmxUserRepository {

    Optional<MmxUser> findById(MmxUserId id);

    MmxUser save(MmxUser user);
}
