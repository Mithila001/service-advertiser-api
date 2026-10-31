package mithila001.service_advertiser.item;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import mithila001.service_advertiser.business.BusinessStatus;
import mithila001.service_advertiser.category.ItemCategory;
import mithila001.service_advertiser.common.SearchText;

final class ItemDiscovery {
    private ItemDiscovery() {}

    static Specification<Item> filter(String text, List<Integer> categoryIds, Integer cityId,
            Integer districtId, Integer provinceId, ItemType type, BigDecimal minPrice,
            BigDecimal maxPrice, String sortField, Sort.Direction direction) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.<ItemStatus>get("status"), ItemStatus.ACTIVE));
            predicates.add(cb.equal(root.get("business").<BusinessStatus>get("status"), BusinessStatus.ACTIVE));
            if (!text.isEmpty()) {
                String pattern = SearchText.likePattern(text);
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (type != null) predicates.add(cb.equal(root.<ItemType>get("type"), type));
            if (cityId != null) predicates.add(cb.equal(root.get("business").get("cityCatalogEntry").get("id"), cityId));
            if (districtId != null) predicates.add(cb.equal(root.get("business").get("cityCatalogEntry").get("district").get("id"), districtId));
            if (provinceId != null) predicates.add(cb.equal(root.get("business").get("cityCatalogEntry").get("district").get("province").get("id"), provinceId));
            if (!categoryIds.isEmpty()) {
                Subquery<Integer> matching = query.subquery(Integer.class);
                Root<ItemCategory> category = matching.from(ItemCategory.class);
                Join<ItemCategory, Item> assigned = category.join("items");
                matching.select(cb.literal(1)).where(category.get("id").in(categoryIds),
                        cb.equal(assigned.get("id"), root.get("id")));
                predicates.add(cb.exists(matching));
            }
            if (minPrice != null) predicates.add(cb.and(cb.isNotNull(root.get("price")), cb.ge(root.get("price"), minPrice)));
            if (maxPrice != null) predicates.add(cb.or(cb.isNull(root.get("price")), cb.le(root.get("price"), maxPrice)));
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                if (sortField.equals("price")) {
                    var nullOrder = direction.isAscending()
                            ? cb.<Integer>selectCase().when(cb.isNull(root.<BigDecimal>get("price")), 0).otherwise(1)
                            : cb.<Integer>selectCase().when(cb.isNull(root.<BigDecimal>get("price")), 1).otherwise(0);
                    query.orderBy(cb.asc(nullOrder), direction.isAscending() ? cb.asc(root.get("price")) : cb.desc(root.get("price")), cb.asc(root.get("id")));
                } else {
                    query.orderBy(direction.isAscending()
                            ? List.of(cb.asc(cb.lower(root.get("title"))), cb.asc(root.get("id")))
                            : List.of(cb.desc(cb.lower(root.get("title"))), cb.asc(root.get("id"))));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
