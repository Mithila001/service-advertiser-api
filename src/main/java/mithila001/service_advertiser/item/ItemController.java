package mithila001.service_advertiser.item;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mithila001.service_advertiser.item.dto.*;
import mithila001.service_advertiser.common.PageResponse;

@Tag(name = "Items", description = "Public products and services, and management of the current business's items.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1")
public class ItemController {
    private final ItemService service;
    public ItemController(ItemService service) { this.service = service; }

    @Operation(summary = "Create an item", description =
            "Requires an active PROVIDER account with an ACTIVE owned business. Creates an ACTIVE item. " +
            "Default business limit is 10 items, including inactive items; configurable per deployment.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Item created"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    })
    @PostMapping("/businesses/me/items")
    public ResponseEntity<ItemResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(Long.valueOf(jwt.getSubject()), request));
    }
    @Operation(summary = "List your items", description =
            "Requires an active PROVIDER with an owned business. Includes inactive items, ordered by ID. " +
            "Available even when the business is suspended or disabled.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @GetMapping("/businesses/me/items")
    public List<ItemResponse> own(@AuthenticationPrincipal Jwt jwt) {
        return service.own(Long.valueOf(jwt.getSubject()));
    }
    @Operation(summary = "Partially update your item", description =
            "Requires an active PROVIDER with an ACTIVE owned business. Omitted fields are preserved. " +
            "Explicit null clears description or price; null title, type, status, or categoryIds is invalid. " +
            "An empty categoryIds array clears categories. Another owner's item returns 404.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @PatchMapping("/items/{id}")
    public ItemResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody UpdateItemRequest request) {
        return service.update(Long.valueOf(jwt.getSubject()), id, request);
    }
    @Operation(summary = "Delete your item", description =
            "Permanently deletes an item from your ACTIVE owned business. Requires an active PROVIDER. " +
            "Another owner's item returns 404.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Item deleted; no response body", content = @Content),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(Long.valueOf(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Get a public item", description =
            "Both the item and its business must be ACTIVE; otherwise returns 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @GetMapping("/items/{id}")
    public ItemResponse publicItem(@PathVariable Long id) { return service.publicItem(id); }
    @Operation(summary = "List a business's public items", description =
            "Returns ACTIVE items of an ACTIVE business, ordered by ID. Unknown or inactive businesses return " +
            "an empty page.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping("/businesses/{uniqueName}/items")
    public PageResponse<ItemResponse> publicItems(@PathVariable String uniqueName,
            @Parameter(description = "Zero-based page number; defaults to 0. page * size must not exceed the configured maximum offset (default 10000).")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size; defaults to the configured size (10), with a configured maximum (100). Minimum 1.")
            @RequestParam(required = false) Integer size) {
        return service.publicItems(uniqueName, page, size);
    }
}
