package com.mmx.order.support;

/** Institution codes seeded by {@link RestTestInstitutionBootstrap} for rest-test profile. */
public final class RestTestInstitutions {

    public static final String BANKCO_CODE = "BI-01";
    public static final String CP_OC_CODE = "CPOC-01";

    private RestTestInstitutions() {}

    public static String executeJson(double executedRate, String institutionCode) {
        return "{\"executedRate\":" + executedRate + ",\"institutionCode\":\"" + institutionCode + "\"}";
    }

    public static String bankCoExecuteJson(double executedRate) {
        return executeJson(executedRate, BANKCO_CODE);
    }
}
