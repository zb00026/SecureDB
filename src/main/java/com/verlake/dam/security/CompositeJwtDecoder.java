package com.verlake.dam.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import java.util.List;

public class CompositeJwtDecoder implements JwtDecoder {

    private final List<JwtDecoder> decoders;

    public CompositeJwtDecoder(JwtDecoder... decoders) {
        this.decoders = List.of(decoders);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        for (JwtDecoder decoder : decoders) {
            try {
                return decoder.decode(token);
            } catch (JwtException ignored) {
                // Try the next decoder
            }
        }
        throw new JwtException("Unable to decode token with any configured decoders");
    }
}