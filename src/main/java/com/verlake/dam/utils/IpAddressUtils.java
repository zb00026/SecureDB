package com.verlake.dam.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Utility class for extracting and handling IP addresses from HTTP requests.
 * Focuses on extracting IPv4 addresses for audit logging purposes.
 */
public class IpAddressUtils {
    
    private IpAddressUtils() {
        // Private constructor to prevent instantiation
    }
    
    // IPv4 pattern for validation
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^(\\d{1,3}\\.){3}\\d{1,3}$"
    );
    
    // IPv6 localhost patterns
    private static final String[] IPV6_LOCALHOST_PATTERNS = {
        "0:0:0:0:0:0:0:1",
        "::1",
        "0:0:0:0:0:0:0:1%1"
    };
    
    /**
     * Extract the client's IPv4 address from the current HTTP request.
     * Checks multiple sources in order of preference:
     * 1. X-Forwarded-For header (for load balancers/proxies)
     * 2. X-Real-IP header (for nginx proxies)
     * 3. Remote address from request
     * 
     * @return IPv4 address string, or "unknown" if not found
     */
    public static String getCurrentIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();
            return getClientIpAddress(request);
        } catch (Exception e) {
            return Constants.UNKNOWN_VALUE;
        }
    }
    
    /**
     * Extract the client's IPv4 address from an HTTP request.
     * 
     * @param request The HTTP request
     * @return IPv4 address string, or "unknown" if not found
     */
    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return Constants.UNKNOWN_VALUE;
        }
        
        // Check proxy headers first
        String ipFromHeaders = getIpFromProxyHeaders(request);
        if (ipFromHeaders != null) {
            return ipFromHeaders;
        }
        
        // Fall back to remote address
        return getIpFromRemoteAddress(request);
    }
    
    /**
     * Extract IP address from proxy headers
     */
    private static String getIpFromProxyHeaders(HttpServletRequest request) {
        String[] headerNames = {
            "X-Forwarded-For",    // Most common for load balancers
            "X-Real-IP",          // nginx proxy
            "X-Forwarded",        // alternative
            "CF-Connecting-IP",   // Cloudflare
            "True-Client-IP"      // Akamai
        };
        
        for (String headerName : headerNames) {
            String ip = getIpFromHeader(request, headerName);
            if (ip != null) {
                return ip;
            }
        }
        
        return null;
    }
    
    /**
     * Extract IP address from a single header
     */
    private static String getIpFromHeader(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue == null || headerValue.isEmpty()) {
            return null;
        }
        
        String ip = extractFirstIpFromList(headerValue);
        return isValidIpv4(ip) ? ip : null;
    }
    
    /**
     * Extract IP address from remote address
     */
    private static String getIpFromRemoteAddress(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isEmpty()) {
            return Constants.UNKNOWN_VALUE;
        }
        
        // Convert IPv6 localhost to IPv4 localhost
        if (isIpv6Localhost(remoteAddr)) {
            return "127.0.0.1";
        }
        
        // If it's already IPv4, return it
        if (isValidIpv4(remoteAddr)) {
            return remoteAddr;
        }
        
        // Try to convert IPv6 to IPv4 if possible
        String ipv4 = convertToIpv4(remoteAddr);
        return ipv4 != null ? ipv4 : Constants.UNKNOWN_VALUE;
    }
    
    /**
     * Extract the first IP address from a comma-separated list.
     * 
     * @param ipList Comma-separated list of IP addresses
     * @return First IP address, trimmed
     */
    private static String extractFirstIpFromList(String ipList) {
        if (ipList == null || ipList.isEmpty()) {
            return null;
        }
        
        String[] ips = ipList.split(",");
        if (ips.length > 0) {
            return ips[0].trim();
        }
        
        return null;
    }
    
    /**
     * Check if the given string is a valid IPv4 address.
     * 
     * @param ip IP address string to validate
     * @return true if valid IPv4, false otherwise
     */
    private static boolean isValidIpv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // Check against IPv4 pattern
        if (!IPV4_PATTERN.matcher(ip).matches()) {
            return false;
        }
        
        // Additional validation using InetAddress
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            return inetAddress instanceof java.net.Inet4Address;
        } catch (UnknownHostException e) {
            return false;
        }
    }
    
    /**
     * Check if the given IP is an IPv6 localhost address.
     * 
     * @param ip IP address to check
     * @return true if IPv6 localhost, false otherwise
     */
    private static boolean isIpv6Localhost(String ip) {
        if (ip == null) {
            return false;
        }
        
        for (String pattern : IPV6_LOCALHOST_PATTERNS) {
            if (ip.equals(pattern) || ip.startsWith(pattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Attempt to convert an IPv6 address to IPv4 if possible.
     * This handles cases where IPv6 addresses can be mapped to IPv4.
     * 
     * @param ipv6 IPv6 address string
     * @return IPv4 address if conversion is possible, null otherwise
     */
    private static String convertToIpv4(String ipv6) {
        if (ipv6 == null || ipv6.isEmpty()) {
            return null;
        }
        
        try {
            InetAddress inetAddress = InetAddress.getByName(ipv6);
            
            // Check if it's an IPv4-mapped IPv6 address
            if (inetAddress instanceof java.net.Inet6Address) {
                byte[] bytes = inetAddress.getAddress();
                
                // Check for IPv4-mapped IPv6 address (::ffff:x.x.x.x)
                if (bytes.length == 16 && 
                    bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 0 &&
                    bytes[4] == 0 && bytes[5] == 0 && bytes[6] == 0 && bytes[7] == 0 &&
                    bytes[8] == 0 && bytes[9] == 0 && bytes[10] == (byte)0xff && bytes[11] == (byte)0xff) {
                    
                    // Extract IPv4 part
                    return String.format("%d.%d.%d.%d", 
                        bytes[12] & 0xff, bytes[13] & 0xff, 
                        bytes[14] & 0xff, bytes[15] & 0xff);
                }
            }
            
            return null;
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
