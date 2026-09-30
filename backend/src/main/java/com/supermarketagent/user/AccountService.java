package com.supermarketagent.user;

import com.supermarketagent.receipt.persistence.ReceiptRepository;
import com.supermarketagent.receipt.query.ReceiptQueryService;
import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** LGPD data subject rights: export all personal data and erase the account. */
@Service
public class AccountService {

    private final UserRepository users;
    private final ReceiptRepository receipts;
    private final ReceiptQueryService receiptQueries;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    AccountService(UserRepository users, ReceiptRepository receipts, ReceiptQueryService receiptQueries,
                   PasswordEncoder passwordEncoder, JdbcTemplate jdbc, Clock clock) {
        this.users = users;
        this.receipts = receipts;
        this.receiptQueries = receiptQueries;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountExport export(long userId) {
        User user = users.findById(userId).orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        var createdAt = jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?",
                Timestamp.class, userId).toInstant();
        var details = receipts.findIdsByUserId(userId).stream()
                .map(receiptId -> receiptQueries.details(userId, receiptId))
                .toList();
        return new AccountExport(clock.instant(), new AccountExport.Account(userId, user.getEmail(), createdAt),
                details);
    }

    /**
     * Deletes the user; the database cascades to receipts, items, payments and sessions.
     * Stores and products are shared reference data and are kept.
     */
    @Transactional
    public void delete(long userId, String password) {
        User user = users.findById(userId).orElseThrow(WrongPasswordException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        users.delete(user);
    }
}
