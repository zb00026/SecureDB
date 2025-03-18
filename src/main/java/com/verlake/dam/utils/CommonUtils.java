package com.verlake.dam.utils;


import org.springframework.http.ResponseEntity;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommonUtils {

    private CommonUtils() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    // Generate a random hex string of given length
    public static String generateInviteCode(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder hexCode = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hexCode.append(Integer.toHexString(random.nextInt(16))); // Generate random hex digit
        }
        return hexCode.toString().toUpperCase(); // Convert to uppercase
    }

    public static ResponseEntity<Map<String, Object>> getSuccessResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(Constants.STATUS_NAME, Constants.STATUS_SUCCESS);
        response.put(Constants.ERROR_MSG_NAME, "");
        return ResponseEntity.ok(response);
    }
}
