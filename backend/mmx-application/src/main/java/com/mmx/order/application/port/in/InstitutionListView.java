package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;
import com.mmx.order.domain.model.ThinProxyInstitution;

import java.util.List;

/** Role-scoped institution catalog view: native hub institutions for a Trader, client proxies for a ClientRepresentative. */
public sealed interface InstitutionListView permits InstitutionListView.Native, InstitutionListView.Proxies {

    record Native(List<Institution> institutions) implements InstitutionListView {}

    record Proxies(List<ThinProxyInstitution> proxies) implements InstitutionListView {}
}
