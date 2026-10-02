package com.supermarketagent.shopping;

import com.supermarketagent.shopping.SavedShoppingListService.SavedShoppingList;
import com.supermarketagent.shopping.ShoppingListService.AddedItems;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Saved copies of the user's shopping list ("Compra do mês"); lists of other users answer 404. */
@RestController
@RequestMapping("/api/shopping-list/saved")
class SavedShoppingListController {

    private final SavedShoppingListService service;

    SavedShoppingListController(SavedShoppingListService service) {
        this.service = service;
    }

    @GetMapping
    List<SavedShoppingList> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt));
    }

    /** Saves the current list; an existing saved list with the same name is replaced. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavedShoppingList save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveRequest request) {
        return service.saveCurrent(userId(jwt), request.name())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "The shopping list is empty"));
    }

    /** Adds the saved items to the current list. */
    @PostMapping("/{id}/apply")
    AddedItems apply(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.apply(userId(jwt), id).orElseThrow(SavedShoppingListController::notFound);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        if (!service.delete(userId(jwt), id)) {
            throw notFound();
        }
    }

    record SaveRequest(@NotBlank @Size(max = 80) String name) {
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved list not found");
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
