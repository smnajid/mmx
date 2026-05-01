package com.mmx.order.adapter.in.rest.dto;

/**
 * Request body for receiving an order ({@code POST /api/v1/orders}).
 * <p>
 * Wire format and Jakarta Bean Validation constraints are defined in
 * {@code specs/001-mm-order-processing/contracts/openapi.yaml} and generated into
 * {@link com.mmx.order.adapter.in.rest.generated.model.ReceiveOrderRequest}. This class extends the
 * generated model so REST controllers depend on the adapter {@code dto} package while staying
 * contract-aligned and validation-complete (see {@code useBeanValidation} on the OpenAPI Generator
 * configuration in {@code mmx-adapter-in-rest/pom.xml}).
 */
public class ReceiveOrderRequest extends com.mmx.order.adapter.in.rest.generated.model.ReceiveOrderRequest {

}
