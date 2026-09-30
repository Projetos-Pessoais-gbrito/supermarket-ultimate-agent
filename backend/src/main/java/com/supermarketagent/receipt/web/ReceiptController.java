package com.supermarketagent.receipt.web;

import com.supermarketagent.receipt.importing.ReceiptImportResult;
import com.supermarketagent.receipt.importing.ReceiptImportService;
import com.supermarketagent.receipt.query.ReceiptDetails;
import com.supermarketagent.receipt.query.ReceiptQueryService;
import com.supermarketagent.receipt.query.ReceiptSummary;
import com.supermarketagent.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/receipts")
class ReceiptController {

    private final ReceiptImportService importService;
    private final ReceiptQueryService queryService;

    ReceiptController(ReceiptImportService importService, ReceiptQueryService queryService) {
        this.importService = importService;
        this.queryService = queryService;
    }

    /** 201 when the receipt was imported now, 200 when the user already had it. */
    @PostMapping
    ResponseEntity<ReceiptDetails> importReceipt(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody ImportReceiptRequest request) {
        long userId = userId(jwt);
        ReceiptImportResult result = importService.importFromQrCode(userId, request.qrCodeUrl());
        ReceiptDetails details = queryService.details(userId, result.receiptId());
        if (!result.created()) {
            return ResponseEntity.ok(details);
        }
        return ResponseEntity.created(URI.create("/api/receipts/" + details.id())).body(details);
    }

    @GetMapping
    PageResponse<ReceiptSummary> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(queryService.list(userId(jwt), page, size));
    }

    @GetMapping("/{id}")
    ReceiptDetails details(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return queryService.details(userId(jwt), id);
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
