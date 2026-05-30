package com.mmx.order.adapter.in.rest;

import com.mmx.order.adapter.in.rest.generated.model.ErrorCode;
import com.mmx.order.adapter.in.rest.generated.model.ErrorResponse;
import com.mmx.order.adapter.in.rest.generated.model.FieldError;
import com.mmx.order.adapter.in.rest.generated.settings.model.SettingsErrorCode;
import com.mmx.order.adapter.in.rest.generated.settings.model.SettingsErrorResponse;
import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionSettingsErrorCode;
import com.mmx.order.adapter.in.rest.generated.institution.model.InstitutionSettingsErrorResponse;
import com.mmx.order.application.service.ManageCurrencySettingsService;
import com.mmx.order.application.service.ManageInstitutionSettingsService;
import com.mmx.order.domain.exception.DuplicateManagedCurrencyException;
import com.mmx.order.domain.exception.InstitutionSuffixOverflowException;
import com.mmx.order.domain.exception.InvalidInstitutionException;
import com.mmx.order.domain.exception.InvalidManagedCurrencyException;
import com.mmx.order.domain.exception.InvalidOrderException;
import com.mmx.order.domain.exception.InvalidStatusTransitionException;
import com.mmx.order.domain.exception.OrderNotFoundException;
import com.mmx.order.domain.exception.UnauthorizedTraderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrder(InvalidOrderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        new ErrorResponse()
                                .error(ErrorCode.VALIDATION_ERROR)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateManagedCurrencyException.class)
    public ResponseEntity<SettingsErrorResponse> handleDuplicateManagedCurrency(DuplicateManagedCurrencyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        new SettingsErrorResponse()
                                .error(SettingsErrorCode.DUPLICATE_CURRENCY)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(InvalidManagedCurrencyException.class)
    public ResponseEntity<SettingsErrorResponse> handleInvalidManagedCurrency(InvalidManagedCurrencyException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        new SettingsErrorResponse()
                                .error(SettingsErrorCode.VALIDATION_ERROR)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(ManageCurrencySettingsService.CurrencyNotFoundException.class)
    public ResponseEntity<SettingsErrorResponse> handleCurrencyNotFound(
            ManageCurrencySettingsService.CurrencyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        new SettingsErrorResponse()
                                .error(SettingsErrorCode.CURRENCY_NOT_FOUND)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(InvalidInstitutionException.class)
    public ResponseEntity<InstitutionSettingsErrorResponse> handleInvalidInstitution(InvalidInstitutionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        new InstitutionSettingsErrorResponse()
                                .error(InstitutionSettingsErrorCode.VALIDATION_ERROR)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(InstitutionSuffixOverflowException.class)
    public ResponseEntity<InstitutionSettingsErrorResponse> handleInstitutionSuffixOverflow(
            InstitutionSuffixOverflowException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        new InstitutionSettingsErrorResponse()
                                .error(InstitutionSettingsErrorCode.INSTITUTION_SUFFIX_OVERFLOW)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(ManageInstitutionSettingsService.InstitutionNotFoundException.class)
    public ResponseEntity<InstitutionSettingsErrorResponse> handleInstitutionNotFound(
            ManageInstitutionSettingsService.InstitutionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        new InstitutionSettingsErrorResponse()
                                .error(InstitutionSettingsErrorCode.INSTITUTION_NOT_FOUND)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(
                        new ErrorResponse()
                                .error(ErrorCode.ORDER_NOT_FOUND)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        new ErrorResponse()
                                .error(ErrorCode.INVALID_STATUS_TRANSITION)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedTraderException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedTrader(UnauthorizedTraderException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        new ErrorResponse()
                                .error(ErrorCode.UNAUTHORIZED_TRADER)
                                .message(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> details =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                fe ->
                                        new FieldError()
                                                .field(fe.getField())
                                                .message(
                                                        Objects.toString(
                                                                fe.getDefaultMessage(), "Invalid value")))
                        .collect(Collectors.toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        new ErrorResponse()
                                .error(ErrorCode.VALIDATION_ERROR)
                                .message("Request validation failed")
                                .details(details));
    }
}
