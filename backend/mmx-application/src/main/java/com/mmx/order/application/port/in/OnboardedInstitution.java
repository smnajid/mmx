package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ThinProxyInstitution;

/** Result of role-qualified institution onboard: a native hub institution or a client thin-proxy. */
public sealed interface OnboardedInstitution permits OnboardedInstitution.Native, OnboardedInstitution.Proxy {

    record Native(Institution institution) implements OnboardedInstitution {}

    record Proxy(ThinProxyInstitution proxy) implements OnboardedInstitution {}
}
