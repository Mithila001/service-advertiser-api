package mithila001.service_advertiser.config;
import java.util.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import io.github.bucket4j.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@Service
@EnableConfigurationProperties(ProtectionProperties.class)
public class RateLimiter {
    private final ProtectionProperties properties;
    private final TimeMeter time;
    private final Map<Key, Entry> entries = new HashMap<>();
    private record Key(String policy, String ip, String account) {}
    private record Entry(Bucket bucket, long touched, long ttl) {}
    @org.springframework.beans.factory.annotation.Autowired
    public RateLimiter(ProtectionProperties properties) { this(properties, TimeMeter.SYSTEM_NANOTIME); }
    RateLimiter(ProtectionProperties properties, TimeMeter time) { this.properties = properties; this.time = time; }
    public void login(String email, String ip) {
        try {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
            check("login-account-ip", ip, digest, properties.rateLimit().loginAccountIp());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public void ip(String route, String ip) {
        var limits = properties.rateLimit();
        switch (route) {
            case "/api/v1/auth/login" -> check("login-ip", ip, "", limits.loginIp());
            case "/api/v1/auth/register" -> check("register-ip", ip, "", limits.registerIp());
            case "/api/v1/auth/refresh" -> check("refresh-ip", ip, "", limits.refreshIp());
            default -> { }
        }
    }
    // ponytail: one lock and <=10,000 buckets; use per-key coordination if contention becomes measurable.
    private synchronized void check(String policy, String ip, String account, ProtectionProperties.Policy limit) {
        if (!properties.rateLimit().enabled()) return;
        long now = time.currentTimeNanos();
        Key key = new Key(policy, ip, account);
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= 10000) cleanup();
            if (entries.size() >= 10000) throw new Rejected(503, 60, policy);
            entry = new Entry(Bucket.builder().withCustomTimePrecision(time).addLimit(b -> b.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period())).build(), now,
                    Math.max(Duration.ofMinutes(15).toNanos(), limit.period().toNanos()));
        }
        entries.put(key, new Entry(entry.bucket(), now, entry.ttl()));
        var probe = entry.bucket().tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) throw new Rejected(429, Math.max(1, (probe.getNanosToWaitForRefill() - 1) / 1000000000 + 1), policy);
    }
    @Scheduled(fixedDelay = 60000)
    public synchronized void cleanup() {
        long now = time.currentTimeNanos();
        entries.values().removeIf(e -> now - e.touched() >= e.ttl());
    }
    public static class Rejected extends RuntimeException {
        public final int status; public final long retry; public final String policy;
        Rejected(int status, long retry, String policy) { super("Request admission rejected"); this.status=status; this.retry=retry; this.policy=policy; }
    }
}
