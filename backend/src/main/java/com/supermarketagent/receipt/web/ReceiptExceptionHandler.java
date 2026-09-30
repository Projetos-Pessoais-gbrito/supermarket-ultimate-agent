package com.supermarketagent.receipt.web;

import com.supermarketagent.receipt.domain.InvalidAccessKeyException;
import com.supermarketagent.receipt.provider.NfcePageParseException;
import com.supermarketagent.receipt.provider.SefazUnavailableException;
import com.supermarketagent.receipt.provider.UnsupportedStateException;
import com.supermarketagent.receipt.provider.UntrustedReceiptUrlException;
import com.supermarketagent.receipt.query.ReceiptNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReceiptController.class)
class ReceiptExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReceiptExceptionHandler.class);

    @ExceptionHandler({InvalidAccessKeyException.class, UntrustedReceiptUrlException.class})
    ProblemDetail invalidQrCode(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnsupportedStateException.class)
    ProblemDetail unsupportedState(UnsupportedStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(NfcePageParseException.class)
    ProblemDetail unreadablePage(NfcePageParseException e) {
        log.warn("Could not read SEFAZ page: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "The receipt could not be read from SEFAZ: " + e.getMessage());
    }

    @ExceptionHandler(SefazUnavailableException.class)
    ProblemDetail sefazUnavailable(SefazUnavailableException e) {
        log.warn("SEFAZ unavailable: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "SEFAZ is not responding right now, please try again in a few minutes");
    }

    @ExceptionHandler(ReceiptNotFoundException.class)
    ProblemDetail notFound(ReceiptNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
