package com.benzaazaa.parcel.auth.token;

import com.benzaazaa.parcel.auth.user.UserAccount;
import com.benzaazaa.parcel.auth.user.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LoginService {

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    /** Hash of a random string: keeps response time equal for unknown users (no username enumeration). */
    private final String dummyHash;

    public LoginService(UserAccountRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("timing-equaliser");
    }

    @Transactional(readOnly = true)
    public TokenService.TokenResponse login(String username, String password) {
        Optional<UserAccount> user = users.findByUsername(username);
        boolean matches = encoder.matches(password, user.map(UserAccount::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches || !user.get().isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.get());
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }
}
