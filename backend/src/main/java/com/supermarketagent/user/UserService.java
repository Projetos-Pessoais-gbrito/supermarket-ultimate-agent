package com.supermarketagent.user;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    // Compared against when the e-mail does not exist, so response time does not reveal registered e-mails
    private final String dummyHash;

    UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public User register(String email, String rawPassword) {
        String normalizedEmail = normalize(email);
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            return users.saveAndFlush(new User(normalizedEmail, passwordEncoder.encode(rawPassword)));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        var user = users.findByEmailIgnoreCase(normalize(email));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(rawPassword, hash);
        if (user.isEmpty() || !matches) {
            throw new BadCredentialsException("Invalid e-mail or password");
        }
        return user.get();
    }

    @Transactional(readOnly = true)
    public User get(long id) {
        return users.findById(id).orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
