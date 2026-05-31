package com.mmx.order.application.port.in;

import java.time.Instant;
import java.time.LocalDate;

public interface UploadTermRatesUseCase {

    UploadResult upload(UploadCommand command);

    record UploadCommand(byte[] csvBytes, String uploadedBy) {}

    record UploadResult(LocalDate tradingDate, int rowCount, Instant uploadedAt) {}
}
