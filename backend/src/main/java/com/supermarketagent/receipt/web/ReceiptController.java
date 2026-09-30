package com.supermarketagent.receipt.web;

import com.supermarketagent.receipt.importing.ReceiptImportResult;
import com.supermarketagent.receipt.importing.ReceiptDeletionService;
import com.supermarketagent.receipt.importing.ReceiptImportService;
import com.supermarketagent.receipt.query.ReceiptDetails;
import com.supermarketagent.receipt.query.ReceiptQueryService;
import com.supermarketagent.receipt.query.ReceiptSummary;
import com.supermarketagent.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/receipts")
class ReceiptController {

    private final ReceiptImportService importService;
    private final ReceiptQueryService queryService;
    private final ReceiptDeletionService deletionService;

    ReceiptController(ReceiptImportService importService, ReceiptQueryService queryService,
                      ReceiptDeletionService deletionService) {
        this.importService = importService;
        this.queryService = queryService;
        this.deletionService = deletionService;
    }

    /** 201 when the receipt was imported now, 200 when the user already had it. */
    @PostMapping
    ResponseEntity<ReceiptDetails> importReceipt(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody ImportReceiptRequest request) {
        long userId = userId(jwt);
        return respond(userId, importService.importFromQrCode(userId, request.qrCodeUrl()));
    }

    private ResponseEntity<ReceiptDetails> respond(long userId, ReceiptImportResult result) {
        ReceiptDetails details = queryService.details(userId, result.receiptId());
        if (!result.created()) {
            return ResponseEntity.ok(details);
        }
        return ResponseEntity.created(URI.create("/api/receipts/" + details.id())).body(details);
    }

    /**
     * Imports the SEFAZ page the user opened in the app after solving the captcha (links that only
     * carry the access key). 201 when new, 200 when the user already had it.
     */
    @PostMapping("/page")
    ResponseEntity<ReceiptDetails> importPage(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody ImportPageRequest request) {
        long userId = userId(jwt);
        ReceiptImportResult result = importService.importFromCaptchaPage(userId, request.accessKey(), request.html());
        return respond(userId, result);
    }

    @GetMapping
    PageResponse<ReceiptSummary> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.list(userId(jwt), page, size));
    }

    /** Removes a receipt imported by mistake; 404 when it is not the user's. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        deletionService.delete(userId(jwt), id);
    }

    @GetMapping("/{id}")
    ReceiptDetails details(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return queryService.details(userId(jwt), id);
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
