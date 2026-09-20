package com.ledgerflow.web;

import com.ledgerflow.service.ItemService;
import com.ledgerflow.web.dto.ItemRequest;
import com.ledgerflow.web.dto.ItemResponse;
import com.ledgerflow.web.dto.PagedResponse;
import jakarta.validation.Valid;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/items")
public class ItemController {

    private static final Set<String> SORTABLE = Set.of("name", "sku", "createdAt", "updatedAt");

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public PagedResponse<ItemResponse> listItems(
            @RequestParam(required = false) String q,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("name").ascending());
        return PagedResponse.from(itemService.search(q, includeArchived, sorted), ItemResponse::from);
    }

    @GetMapping("/{id}")
    public ItemResponse getItem(@PathVariable Long id) {
        return ItemResponse.from(itemService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse createItem(@Valid @RequestBody ItemRequest request) {
        return ItemResponse.from(itemService.create(request.toDraft()));
    }

    @PutMapping("/{id}")
    public ItemResponse updateItem(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return ItemResponse.from(itemService.update(id, request.toDraft()));
    }

    @PostMapping("/{id}/archive")
    public ItemResponse archiveItem(@PathVariable Long id) {
        return ItemResponse.from(itemService.archive(id));
    }

    @PostMapping("/{id}/restore")
    public ItemResponse restoreItem(@PathVariable Long id) {
        return ItemResponse.from(itemService.restore(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id) {
        itemService.delete(id);
    }
}
