package com.verlake.dam.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompositeJwtDecoder implements JwtDecoder {

    private final List<JwtDecoder> decoders;

    public CompositeJwtDecoder(JwtDecoder... decoders) {
        this.decoders = List.of(decoders);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < decoders.size(); i++) {
            JwtDecoder decoder = decoders.get(i);
            try {
                log.debug("Attempting to decode token with decoder {} of {}", i + 1, decoders.size());
                Jwt jwt = decoder.decode(token);
                log.debug("Successfully decoded token with decoder {}", i + 1);
                return jwt;
            } catch (JwtException e) {
                String errorMsg = String.format("Decoder %d failed: %s", i + 1, e.getMessage());
                log.debug(errorMsg);
                errors.add(errorMsg);
                // Try the next decoder
            } catch (Exception e) {
                String errorMsg = String.format("Decoder %d threw unexpected exception: %s", i + 1, e.getMessage());
                log.warn(errorMsg, e);
                errors.add(errorMsg);
            }
        }
        
        String allErrors = String.join("; ", errors);
        log.error("Unable to decode token with any configured decoders. Errors: {}", allErrors);
        throw new JwtException("Unable to decode token with any configured decoders. Errors: " + allErrors);
    }
}