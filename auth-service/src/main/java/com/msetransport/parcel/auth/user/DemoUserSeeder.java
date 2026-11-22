package com.msetransport.parcel.auth.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Demo accounts for the local stack. Courier ids match the couriers seeded by dispatch-service's
 * Flyway migration. Disabled with AUTH_SEED_DEMO_USERS=false.
 */
@Component
@ConditionalOnProperty(name = "auth.seed-demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Passw0rd!";
    public static final UUID COURIER_CASA_1 = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    public static final UUID COURIER_CASA_2 = UUID.fromString("c0000000-0000-0000-0000-000000000002");
    public static final UUID COURIER_RABAT_1 = UUID.fromString("c0000000-0000-0000-0000-000000000003");

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoUserSeeder(UserAccountRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        create("ops", Role.OPS, null, null);
        create("merchant.atlas", Role.MERCHANT, "M-1001", null);
        create("merchant.souk", Role.MERCHANT, "M-1002", null);
        create("courier.casa1", Role.COURIER, null, COURIER_CASA_1);
        create("courier.casa2", Role.COURIER, null, COURIER_CASA_2);
        create("courier.rabat1", Role.COURIER, null, COURIER_RABAT_1);
    }

    private void create(String username, Role role, String merchantId, UUID courierId) {
        if (!users.existsByUsername(username)) {
            users.save(new UserAccount(username, encoder.encode(DEMO_PASSWORD), role, merchantId, courierId,
                    clock.instant()));
            log.info("Seeded demo user {} ({})", username, role);
        }
    }
}
