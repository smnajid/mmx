package com.mmx.order.application.port.in;

import com.mmx.order.domain.model.Institution;

import java.util.List;

/** Role-scoped institution list: native hub institutions for a Trader, the client's onboarded institutions for a ClientRepresentative. */
public sealed interface InstitutionListView permits InstitutionListView.Native, InstitutionListView.Onboarded {

    List<Institution> institutions();

    record Native(List<Institution> institutions) implements InstitutionListView {}

    record Onboarded(List<Institution> institutions) implements InstitutionListView {}
}
