package com.ledgerflow.service;

import com.ledgerflow.domain.Role;
import com.ledgerflow.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * @throws org.springframework.security.core.AuthenticationException if
     *         the username/password pair is invalid.
     */
    public String login(String username, String password) {
        Authentication authResult =
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));

        String authority = authResult.getAuthorities().iterator().next().getAuthority();
        Role role = Role.valueOf(authority.replace("ROLE_", ""));
        return jwtService.generateToken(username, role);
    }
}
