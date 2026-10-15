package mithila001.service_advertiser.common.exception;

public class RoleNotFoundException extends RuntimeException {
    public RoleNotFoundException() {
        super("Required role is missing");
    }
}
