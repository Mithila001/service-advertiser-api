package mithila001.service_advertiser.config;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
@ConfigurationProperties("app.protection")
public record ProtectionProperties(@DefaultValue("64KB") DataSize maxJsonBodySize, @DefaultValue RateLimit rateLimit) {
    public ProtectionProperties {
        if (maxJsonBodySize == null || maxJsonBodySize.toBytes() < 1 || maxJsonBodySize.toBytes() >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("JSON body limit must fit a bounded byte array");
    }
    public record RateLimit(@DefaultValue("true") boolean enabled, @DefaultValue Policy loginIp, @DefaultValue Policy loginAccountIp, @DefaultValue Policy registerIp, @DefaultValue Policy refreshIp) {}
    public record Policy(@DefaultValue("5") long capacity, @DefaultValue("1m") Duration period) {
        public Policy {
            if (capacity < 1 || period == null || period.isNegative() || period.isZero())
                throw new IllegalArgumentException("Rate capacity and period must be positive");
            period.toNanos();
        }
    }
}
