package mithila001.service_advertiser.business;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.category.CategoryService;
import mithila001.service_advertiser.business.dto.*;
import mithila001.service_advertiser.common.exception.RoleNotFoundException;
import mithila001.service_advertiser.user.*;
import mithila001.service_advertiser.location.LocationCatalogService;

@Service
@Transactional
public class BusinessService {
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final RoleRepository roles;
    private final Clock clock;
    private final CategoryService categories;
    private final LocationCatalogService locations;

    public BusinessService(BusinessRepository businesses, UserRepository users, RoleRepository roles, Clock clock,
            CategoryService categories, LocationCatalogService locations) {
        this.businesses = businesses;
        this.users = users;
        this.roles = roles;
        this.clock = clock;
        this.categories = categories;
        this.locations = locations;
    }

    public BusinessResponse register(Long userId, RegisterBusinessRequest request) {
        User user = activeUser(userId);
        if (user.getBusinessId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already owns a business");
        }
        if (user.getRole().getName() != UserRole.CUSTOMER) {
            throw new AccessDeniedException("Only customers can register a business");
        }
        if (businesses.existsByUniqueName(request.uniqueName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business unique name is already registered");
        }
        var provider = roles.findByName(UserRole.PROVIDER).orElseThrow(RoleNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        var city = locations.requireCity(request.information().cityId());
        Business business = new Business(request.uniqueName(), request.information(), city, now);
        business.setCategories(categories.businessSelection(request.information().categoryIds()));
        business = businesses.saveAndFlush(business);
        user.setBusinessId(business.getId());
        user.setRole(provider);
        user.setUpdatedAt(now);
        return BusinessResponse.from(business);
    }

    public BusinessResponse own(Long userId) {
        return BusinessResponse.from(ownedBusiness(activeUser(userId)));
    }

    public BusinessResponse update(Long userId, BusinessInformationRequest information) {
        Business business = ownedBusiness(activeUser(userId));
        var city = locations.requireCity(information.cityId());
        business.update(information, city, LocalDateTime.now(clock));
        business.setCategories(categories.businessSelection(information.categoryIds()));
        return BusinessResponse.from(business);
    }

    public void delete(Long userId) {
        User user = activeUser(userId);
        Business business = ownedBusiness(user);
        var customer = roles.findByName(UserRole.CUSTOMER).orElseThrow(RoleNotFoundException::new);
        user.setBusinessId(null);
        user.setRole(customer);
        user.setUpdatedAt(LocalDateTime.now(clock));
        // Release the existing foreign key before deleting its target.
        users.flush();
        businesses.delete(business);
    }

    @Transactional(readOnly = true)
    public BusinessResponse publicProfile(String uniqueName) {
        return BusinessResponse.from(businesses.findByUniqueName(uniqueName).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found")));
    }

    public Business providerBusiness(Long userId) {
        User user = activeUser(userId);
        if (user.getRole().getName() != UserRole.PROVIDER) {
            throw new AccessDeniedException("Only providers can manage items");
        }
        return ownedBusiness(user);
    }

    private User activeUser(Long userId) {
        User user = users.findForUpdate(userId).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Account is inactive");
        }
        return user;
    }

    private Business ownedBusiness(User user) {
        if (user.getBusinessId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found");
        }
        return businesses.findById(user.getBusinessId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
    }
}
