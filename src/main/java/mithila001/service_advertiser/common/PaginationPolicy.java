package mithila001.service_advertiser.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaginationPolicy {
    private final int defaultSize;
    private final int maxSize;
    private final long maxOffset;

    public PaginationPolicy(@Value("${app.pagination.default-size:10}") int defaultSize,
            @Value("${app.pagination.max-size:100}") int maxSize,
            @Value("${app.pagination.max-offset:10000}") long maxOffset) {
        if (defaultSize < 1 || maxSize < defaultSize || maxSize > Integer.MAX_VALUE
                || maxOffset < 0 || maxOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid pagination configuration");
        }
        this.defaultSize = defaultSize;
        this.maxSize = maxSize;
        this.maxOffset = maxOffset;
    }

    public Pageable page(Integer page, Integer size, Sort sort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? defaultSize : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > maxSize
                || (long) pageNumber * pageSize > maxOffset) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
        }
        return PageRequest.of(pageNumber, pageSize, sort);
    }
}
