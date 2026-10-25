package com.fitnessai.user.service;

import com.fitnessai.user.model.Role;
import com.fitnessai.user.model.User;
import com.fitnessai.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarantees an admin account exists, so a fresh install can reach the admin portal.
 * Creates it once with the seed password; afterwards the password is never touched
 * (so changing it sticks), but the ADMIN role is restored if it was removed.
 */
@Component
@ConditionalOnProperty(name = "seed.admin.enabled", havingValue = "true", matchIfMissing = true)
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);
    static final String DEFAULT_PASSWORD = "Admin@12345";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder,
                       @Value("${seed.admin.username:admin}") String username,
                       @Value("${seed.admin.password:" + DEFAULT_PASSWORD + "}") String password,
                       @Value("${seed.admin.email:admin@gymai.local}") String email) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.email = email;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        users.findByUsername(username).ifPresentOrElse(existing -> {
            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
                log.warn("Seed admin '{}' had lost the ADMIN role; restored it.", username);
            }
        }, () -> {
            User admin = new User(username, email, "Administrator", passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            users.save(admin);
            log.info("Created seed admin '{}'.", username);
            if (DEFAULT_PASSWORD.equals(password)) {
                log.warn("Seed admin uses the default password; change it in Settings or set SEED_ADMIN_PASSWORD.");
            }
        });
    }
}
