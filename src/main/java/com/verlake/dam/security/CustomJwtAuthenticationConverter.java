package com.verlake.dam.security;


import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Autowired
    private UserService userService;

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    public CustomJwtAuthenticationConverter() {
        this.jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    }

    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(
            jwt,
            extractAuthorities(jwt)
        );
    }

    protected Collection<? extends GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Extract the user's email from the JWT token
        String email = jwt.getClaimAsString("email");  // Adjust based on how the claim is named

        // Fetch user details from the database
        User user = userService.findByEmail(email);

        // Extract roles from the user and map them to authorities
        if (user != null && user.getRoles() != null && !user.getRoles().isEmpty()) {
            Set<Role> roles = user.getRoles();
            return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().toUpperCase().replace(" ", "_")))
                .collect(Collectors.toList());
        }

        // Fallback to JWT roles if no roles found in the database
        return jwtGrantedAuthoritiesConverter.convert(jwt);
    }
}