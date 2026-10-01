package com.supermarketagent.shopping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The user's own shopping list; items of other users answer 404. */
@RestController
@RequestMapping("/api/shopping-list")
class ShoppingListController {

    private final ShoppingListService service;

    ShoppingListController(ShoppingListService service) {
        this.service = service;
    }

    @GetMapping
    List<ShoppingListItem> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    /** A product from the user's history ({@code productId}) or a free-text item ({@code name}). */
    @PostMapping
    ResponseEntity<ShoppingListItem> add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddItemRequest request) {
        long userId = userId(jwt);
        ShoppingListItem item = request.productId() != null
                ? service.addProduct(userId, request.productId(), request.quantity()).orElseThrow(ShoppingListController::notFound)
                : service.addText(userId, request.name(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PatchMapping("/{id}")
    ShoppingListItem setChecked(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                @Valid @RequestBody CheckRequest request) {
        return service.setChecked(userId(jwt), id, request.checked()).orElseThrow(ShoppingListController::notFound);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        if (!service.remove(userId(jwt), id)) {
            throw notFound();
        }
    }

    /** Clears everything already bought. */
    @DeleteMapping("/checked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeChecked(@AuthenticationPrincipal Jwt jwt) {
        service.removeChecked(userId(jwt));
    }

    record AddItemRequest(
            Long productId,
            @Size(max = 200) String name,
            @DecimalMin(value = "0", inclusive = false) @DecimalMax("9999") BigDecimal quantity) {

        @AssertTrue(message = "send a productId or a name")
        boolean isProductOrName() {
            return productId != null || (name != null && !name.isBlank());
        }
    }

    record CheckRequest(@NotNull Boolean checked) {
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
