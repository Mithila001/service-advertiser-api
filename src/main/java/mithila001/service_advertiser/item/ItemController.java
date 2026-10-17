package mithila001.service_advertiser.item;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mithila001.service_advertiser.item.dto.*;

@RestController
@RequestMapping("/api/v1")
public class ItemController {
    private final ItemService service;
    public ItemController(ItemService service) { this.service = service; }

    @PostMapping("/businesses/me/items")
    public ResponseEntity<ItemResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(Long.valueOf(jwt.getSubject()), request));
    }
    @GetMapping("/businesses/me/items")
    public List<ItemResponse> own(@AuthenticationPrincipal Jwt jwt) {
        return service.own(Long.valueOf(jwt.getSubject()));
    }
    @PatchMapping("/items/{id}")
    public ItemResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody UpdateItemRequest request) {
        return service.update(Long.valueOf(jwt.getSubject()), id, request);
    }
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(Long.valueOf(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/items/{id}")
    public ItemResponse publicItem(@PathVariable Long id) { return service.publicItem(id); }
    @GetMapping("/businesses/{uniqueName}/items")
    public ItemPageResponse publicItems(@PathVariable String uniqueName,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.publicItems(uniqueName, page, size);
    }
}
