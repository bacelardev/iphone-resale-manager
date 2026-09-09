package io.github.bacelardev.iphoneresale.application.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

public final class PageableFactory {

    private PageableFactory() {
    }

    public static Pageable create(
            int page,
            int size,
            String sort,
            Map<String, String> allowed,
            String defaultProperty,
            Sort.Direction defaultDirection
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Paginação inválida.");
        }
        String property = defaultProperty;
        Sort.Direction direction = defaultDirection;
        if (sort != null && !sort.isBlank()) {
            String[] tokens = sort.split(",", -1);
            property = allowed.get(tokens[0]);
            if (property == null || tokens.length > 2) {
                throw BusinessException.badRequest("INVALID_SORT", "Ordenação não permitida.");
            }
            if (tokens.length == 2) {
                try {
                    direction = Sort.Direction.fromString(tokens[1]);
                } catch (IllegalArgumentException exception) {
                    throw BusinessException.badRequest("INVALID_SORT", "Direção de ordenação inválida.");
                }
            }
        }
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
