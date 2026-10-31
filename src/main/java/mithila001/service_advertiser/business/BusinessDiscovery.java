package mithila001.service_advertiser.business;

import java.util.List;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import mithila001.service_advertiser.category.BusinessCategory;
import mithila001.service_advertiser.common.SearchText;

final class BusinessDiscovery {
    private BusinessDiscovery() {}

    static Specification<Business> filter(String text, List<Integer> categoryIds, Integer cityId,
            Integer districtId, Integer provinceId, Sort.Direction direction) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>();
            predicates.add(cb.equal(root.<BusinessStatus>get("status"), BusinessStatus.ACTIVE));
            if (!text.isEmpty()) {
                String pattern = SearchText.likePattern(text);
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            if (cityId != null) predicates.add(cb.equal(root.get("cityCatalogEntry").get("id"), cityId));
            if (districtId != null) predicates.add(cb.equal(root.get("cityCatalogEntry").get("district").get("id"), districtId));
            if (provinceId != null) predicates.add(cb.equal(root.get("cityCatalogEntry").get("district").get("province").get("id"), provinceId));
            if (!categoryIds.isEmpty()) {
                Subquery<Integer> matching = query.subquery(Integer.class);
                Root<BusinessCategory> category = matching.from(BusinessCategory.class);
                Join<BusinessCategory, Business> assigned = category.join("businesses");
                matching.select(cb.literal(1)).where(category.get("id").in(categoryIds),
                        cb.equal(assigned.get("id"), root.get("id")));
                predicates.add(cb.exists(matching));
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(direction.isAscending()
                        ? List.of(cb.asc(cb.lower(root.get("name"))), cb.asc(root.get("id")))
                        : List.of(cb.desc(cb.lower(root.get("name"))), cb.asc(root.get("id"))));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
