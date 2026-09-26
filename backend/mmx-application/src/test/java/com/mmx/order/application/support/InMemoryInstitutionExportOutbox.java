package com.mmx.order.application.support;

import com.mmx.order.application.port.out.InstitutionExportOutbox;
import com.mmx.order.domain.model.Institution;

import java.util.ArrayList;
import java.util.List;

/** Records every scheduled institution export for application tests. */
public final class InMemoryInstitutionExportOutbox implements InstitutionExportOutbox {

    public record Scheduled(String institutionCode, long version, boolean closedToNewBusiness, ChangeReason reason) {}

    private final List<Scheduled> scheduled = new ArrayList<>();

    @Override
    public void schedule(Institution institution, ChangeReason reason) {
        scheduled.add(new Scheduled(
                institution.getInstitutionCode(), institution.getVersion(), institution.isClosedToNewBusiness(), reason));
    }

    public List<Scheduled> scheduled() {
        return scheduled;
    }

    public List<ChangeReason> reasons() {
        return scheduled.stream().map(Scheduled::reason).toList();
    }
}
