package com.ledgerflow.service;

import com.ledgerflow.domain.OrgMember;
import com.ledgerflow.domain.Organization;
import com.ledgerflow.domain.Role;
import com.ledgerflow.domain.User;
import com.ledgerflow.repository.OrgMemberRepository;
import com.ledgerflow.repository.OrganizationRepository;
import com.ledgerflow.repository.UserRepository;
import com.ledgerflow.security.JwtService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgMemberRepository orgMemberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            OrgMemberRepository orgMemberRepository,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.orgMemberRepository = orgMemberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @throws org.springframework.security.core.AuthenticationException if
     *         the username/password pair is invalid.
     */
    public String login(String username, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));

        User user = userRepository.findByUsername(username).orElseThrow();
        OrgMember membership = orgMemberRepository.findByUserIdOrderByOrgIdAsc(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "User " + username + " does not belong to any organization"));

        return jwtService.generateToken(username, membership.getRole(), membership.getOrgId());
    }

    /**
     * Issues a token for a different organization the user belongs to. The
     * membership check is the authorization: without it, anyone could mint a
     * token for any org id and the tenant context would honour it.
     */
    public String switchOrganization(String username, Long orgId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        OrgMember membership = orgMemberRepository
                .findByOrgIdAndUserId(orgId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("You are not a member of that organization"));

        return jwtService.generateToken(username, membership.getRole(), orgId);
    }

    /** Creates a user together with the organization they will administer. */
    @Transactional
    public String signUp(String username, String password, String organizationName) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("That username is already taken");
        }

        Organization organization = new Organization();
        organization.setName(organizationName);
        organization = organizationRepository.save(organization);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);

        OrgMember membership = new OrgMember();
        membership.setOrgId(organization.getId());
        membership.setUserId(user.getId());
        membership.setRole(Role.ADMIN);
        orgMemberRepository.save(membership);

        return jwtService.generateToken(username, Role.ADMIN, organization.getId());
    }

    public List<OrgMember> membershipsOf(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        return orgMemberRepository.findByUserIdOrderByOrgIdAsc(user.getId());
    }

    public Organization organization(Long orgId) {
        return organizationRepository
                .findById(orgId)
                .orElseThrow(() -> new NoSuchElementException("No organization with id " + orgId));
    }
}
