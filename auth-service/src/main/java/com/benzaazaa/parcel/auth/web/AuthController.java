package com.benzaazaa.parcel.auth.web;

import com.benzaazaa.parcel.auth.token.LoginService;
import com.benzaazaa.parcel.auth.token.TokenService;
import com.benzaazaa.parcel.support.security.AuthenticatedUser;
import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
public class AuthController {

    private final LoginService loginService;
    private final JWKSet jwkSet;

    public AuthController(LoginService loginService, JWKSet jwkSet) {
        this.loginService = loginService;
        this.jwkSet = jwkSet;
    }

    @Operation(summary = "Exchange username/password for an RS256 access token")
    @PostMapping("/api/auth/login")
    public TokenService.TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request.username(), request.password());
    }

    @Operation(summary = "Claims of the current token")
    @GetMapping("/api/auth/me")
    public AuthenticatedUser me(@AuthenticationPrincipal Jwt jwt) {
        return AuthenticatedUser.from(jwt);
    }

    /** Public keys only: toPublicJWKSet() strips the private exponent. Resource servers cache this. */
    @Operation(summary = "JSON Web Key Set used by resource servers to verify tokens")
    @GetMapping({"/.well-known/jwks.json", "/api/auth/jwks"})
    public Map<String, Object> jwks() {
        return jwkSet.toPublicJWKSet().toJSONObject();
    }

    @ExceptionHandler(LoginService.InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(LoginService.InvalidCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        pd.setType(URI.create("https://parcel.benzaazaa.com/problems/invalid-credentials"));
        pd.setTitle("Authentication failed");
        return pd;
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 128) String password) {
    }
}
