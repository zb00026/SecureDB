package com.verlake.dam.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemInfoService {
    private static final Logger logger = LoggerFactory.getLogger(SystemInfoService.class);
    
    // Configuration for manual system identifier override
    @Value("${system.identifier.override:}")
    private String systemIdentifierOverride;
    
    @Value("${system.identifier.use-mac-address:true}")
    private boolean useMacAddressInIdentifier;
    
    // Cache the stable system identifier to prevent changes during runtime
    private volatile String cachedSystemIdentifier = null;
    private final Object identifierLock = new Object();
    
    /**
     * Get system information for node-locked licensing
     */
    public Map<String, String> getSystemInfo() {
        Map<String, String> systemInfo = new LinkedHashMap<>();
        
        try {
            // Basic system properties
            systemInfo.put("hostname", getHostname());
            systemInfo.put("osName", System.getProperty("os.name"));
            systemInfo.put("osVersion", System.getProperty("os.version"));
            systemInfo.put("osArch", System.getProperty("os.arch"));
            systemInfo.put("javaVersion", System.getProperty("java.version"));
            systemInfo.put("javaVendor", System.getProperty("java.vendor"));
            
            // Network information
            List<String> macAddresses = getMacAddresses();
            if (!macAddresses.isEmpty()) {
                systemInfo.put("primaryMacAddress", macAddresses.get(0));
                systemInfo.put("allMacAddresses", String.join(",", macAddresses));
                logger.debug("Selected primary MAC address: {} from {} available addresses", 
                    macAddresses.get(0), macAddresses.size());
            } else {
                logger.warn("No MAC addresses found for system identification");
            }
            
            // System identifiers
            systemInfo.put("userHome", System.getProperty("user.home"));
            systemInfo.put("userName", System.getProperty("user.name"));
            
            // Hardware information (if available)
            systemInfo.put("availableProcessors", String.valueOf(Runtime.getRuntime().availableProcessors()));
            systemInfo.put("maxMemory", String.valueOf(Runtime.getRuntime().maxMemory()));
            
            // Generate unique system fingerprint
            systemInfo.put("systemFingerprint", generateSystemFingerprint(systemInfo));
            
            // Add stable system identifier
            systemInfo.put("stableSystemIdentifier", getSystemIdentifier());
            
            // Add configuration info
            systemInfo.put("systemIdentifierOverride", systemIdentifierOverride != null && !systemIdentifierOverride.trim().isEmpty() ? "SET" : "NOT_SET");
            systemInfo.put("useMacAddressInIdentifier", String.valueOf(useMacAddressInIdentifier));
            
        } catch (Exception e) {
            logger.error("Error collecting system information", e);
            systemInfo.put("error", "Failed to collect system information: " + e.getMessage());
        }
        
        return systemInfo;
    }
    
    /**
     * Get the system hostname
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            logger.warn("Could not determine hostname", e);
            return "unknown";
        }
    }
    
    /**
     * Get all MAC addresses from network interfaces
     */
    private List<String> getMacAddresses() {
        List<String> ethernetMacs = new ArrayList<>();
        List<String> wifiMacs = new ArrayList<>();
        List<String> otherMacs = new ArrayList<>();
        
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                if (shouldSkipNetworkInterface(networkInterface)) {
                    continue;
                }
                
                String macAddress = extractMacAddress(networkInterface);
                if (macAddress != null) {
                    categorizeAndAddMacAddress(macAddress, networkInterface.getName().toLowerCase(), 
                                             ethernetMacs, wifiMacs, otherMacs);
                }
            }
            
            return buildSortedMacList(ethernetMacs, wifiMacs, otherMacs);
            
        } catch (Exception e) {
            logger.warn("Could not determine MAC addresses", e);
            return new ArrayList<>();
        }
    }
    
    private boolean shouldSkipNetworkInterface(NetworkInterface networkInterface) {
        try {
            String interfaceName = networkInterface.getName().toLowerCase();
            return networkInterface.isLoopback() || 
                                            networkInterface.isVirtual() || 
                                            !networkInterface.isUp() ||
                   isVirtualOrContainerInterface(interfaceName);
        } catch (Exception e) {
            return true; // Skip interfaces that cause exceptions
        }
    }
    
    private boolean isVirtualOrContainerInterface(String interfaceName) {
        return interfaceName.startsWith("docker") || 
                                            interfaceName.startsWith("veth") || 
                                            interfaceName.startsWith("vmnet") || 
                                            interfaceName.startsWith("vbox") ||
                                            interfaceName.startsWith("br-") ||
                                            interfaceName.contains("virtual");
    }
    
    private String extractMacAddress(NetworkInterface networkInterface) {
        try {
                byte[] mac = networkInterface.getHardwareAddress();
            if (mac == null || mac.length != 6) {
                return null;
            }
            
                    StringBuilder macAddress = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        macAddress.append(String.format("%02X", mac[i]));
                        if (i < mac.length - 1) {
                            macAddress.append(":");
                        }
                    }
            return macAddress.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    private void categorizeAndAddMacAddress(String macAddress, String interfaceName, 
                                          List<String> ethernetMacs, List<String> wifiMacs, List<String> otherMacs) {
        if (isEthernetInterface(interfaceName)) {
            ethernetMacs.add(macAddress);
            logger.debug("Added ethernet MAC: {} from interface: {}", macAddress, interfaceName);
        } else if (isWifiInterface(interfaceName)) {
            wifiMacs.add(macAddress);
            logger.debug("Added wifi MAC: {} from interface: {}", macAddress, interfaceName);
        } else {
            otherMacs.add(macAddress);
            logger.debug("Added other MAC: {} from interface: {}", macAddress, interfaceName);
        }
    }
    
    private boolean isEthernetInterface(String interfaceName) {
        return interfaceName.startsWith("eth") || 
               (interfaceName.startsWith("en") && !interfaceName.contains("wifi") && !interfaceName.contains("wl"));
    }
    
    private boolean isWifiInterface(String interfaceName) {
        return interfaceName.contains("wifi") || 
                               interfaceName.startsWith("wl") || 
                               interfaceName.contains("wireless") ||
               interfaceName.startsWith("wlan");
    }
    
    private List<String> buildSortedMacList(List<String> ethernetMacs, List<String> wifiMacs, List<String> otherMacs) {
            // Sort each category for consistency
            Collections.sort(ethernetMacs);
            Collections.sort(wifiMacs);
            Collections.sort(otherMacs);
            
            // Prioritize: Ethernet first, then WiFi, then others
            List<String> allMacs = new ArrayList<>();
            allMacs.addAll(ethernetMacs);
            allMacs.addAll(wifiMacs);
            allMacs.addAll(otherMacs);
            
            return allMacs;
    }
    
    /**
     * Generate a stable system fingerprint based on immutable system properties
     * This excludes volatile elements like MAC addresses, memory, and processor count
     */
    private String generateSystemFingerprint(Map<String, String> systemInfo) {
        try {
            // Use only stable system properties for fingerprint generation
            List<String> fingerprintComponents = Arrays.asList(
                systemInfo.getOrDefault("hostname", ""),
                systemInfo.getOrDefault("osName", ""),
                systemInfo.getOrDefault("osVersion", ""),
                systemInfo.getOrDefault("osArch", ""),
                systemInfo.getOrDefault("userHome", ""),
                systemInfo.getOrDefault("userName", ""),
                systemInfo.getOrDefault("javaVendor", "")
            );
            
            String fingerprintString = fingerprintComponents.stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("|"));
            
            logger.debug("Fingerprint components: {}", fingerprintString);
            
            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fingerprintString.getBytes("UTF-8"));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString().toUpperCase();
            
        } catch (Exception e) {
            logger.error("Error generating system fingerprint", e);
            return "FINGERPRINT_ERROR";
        }
    }
    
    /**
     * Generate a stable hardware fingerprint that includes the most stable MAC address
     */
    private String generateHardwareFingerprint() {
        try {
            List<String> hardwareComponents = new ArrayList<>();
            
            // Always include these stable components
            hardwareComponents.add(getHostname());
            hardwareComponents.add(System.getProperty("os.name", ""));
            hardwareComponents.add(System.getProperty("os.arch", ""));
            hardwareComponents.add(System.getProperty("user.home", ""));
            
            // Optionally include MAC address
            if (useMacAddressInIdentifier) {
                List<String> allMacs = getMacAddresses();
                String stableMac = findMostStableMacAddress(allMacs);
                hardwareComponents.add(stableMac != null ? stableMac.replace(":", "") : "NOMAC");
            }
            
            String hardwareString = hardwareComponents.stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining("|"));
            
            logger.debug("Hardware fingerprint components: {}", hardwareString);
            
            // Generate SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hardwareString.getBytes("UTF-8"));
            
            // Convert to hex string and take first 12 characters
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 6); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString().toUpperCase();
            
        } catch (Exception e) {
            logger.error("Error generating hardware fingerprint", e);
            return "HW_ERROR";
        }
    }
    
    /**
     * Find the most stable MAC address from available interfaces
     * Prioritizes built-in ethernet over WiFi and virtual interfaces
     */
    private String findMostStableMacAddress(List<String> allMacs) {
        if (allMacs.isEmpty()) {
            return null;
        }
        
        try {
            // Get all network interfaces and find the most stable one
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            String bestMac = null;
            int bestPriority = Integer.MAX_VALUE;
            
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                
                // Combined condition to skip unwanted interfaces
                boolean shouldSkipInterface = networkInterface.isLoopback() || 
                                            networkInterface.isVirtual() ||
                                            mac == null || 
                                            mac.length != 6;
                
                if (shouldSkipInterface) {
                    continue;
                }
                
                String macStr = formatMacAddress(mac);
                String interfaceName = networkInterface.getName().toLowerCase();
                
                // Assign priority (lower is better)
                int priority = getPriorityForInterface(interfaceName);
                
                if (priority < bestPriority) {
                    bestPriority = priority;
                    bestMac = macStr;
                    logger.debug("Selected MAC {} from interface {} (priority: {})", macStr, interfaceName, priority);
                }
            }
            
            return bestMac != null ? bestMac : allMacs.get(0);
            
        } catch (Exception e) {
            logger.warn("Error finding stable MAC address, using first available", e);
            return allMacs.get(0);
        }
    }
    
    /**
     * Get priority for network interface (lower is better/more stable)
     */
    private int getPriorityForInterface(String interfaceName) {
        // Built-in ethernet (most stable)
        if (interfaceName.equals("en0") || interfaceName.startsWith("eth0")) {
            return 1;
        }
        // Other ethernet interfaces
        if (interfaceName.startsWith("eth") || interfaceName.startsWith("en")) {
            return 2;
        }
        // WiFi interfaces
        if (interfaceName.contains("wifi") || interfaceName.startsWith("wl") || interfaceName.startsWith("wlan")) {
            return 3;
        }
        // Other physical interfaces
        return 4;
    }
    
    /**
     * Format MAC address bytes to string
     */
    private String formatMacAddress(byte[] mac) {
        StringBuilder macAddress = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            macAddress.append(String.format("%02X", mac[i]));
            if (i < mac.length - 1) {
                macAddress.append(":");
            }
        }
        return macAddress.toString();
    }
    
    /**
     * Get a stable system identifier that doesn't change between restarts
     * This method caches the identifier to ensure consistency during runtime
     */
    public String getSystemIdentifier() {
        if (cachedSystemIdentifier != null) {
            return cachedSystemIdentifier;
        }
        
        synchronized (identifierLock) {
            if (cachedSystemIdentifier != null) {
                return cachedSystemIdentifier;
            }
            
            try {
                // Check for manual override first
                if (systemIdentifierOverride != null && !systemIdentifierOverride.trim().isEmpty()) {
                    cachedSystemIdentifier = systemIdentifierOverride.trim().toUpperCase();
                    logger.info("Using manual system identifier override: {}", cachedSystemIdentifier);
                    return cachedSystemIdentifier;
                }
                
                String hostname = getHostname();
                String hardwareFingerprint = generateHardwareFingerprint();
                
                // Create a stable identifier using hostname and hardware fingerprint
                cachedSystemIdentifier = String.format("%s-%s", 
                    hostname.toUpperCase(), 
                    hardwareFingerprint
                );
                
                logger.info("Generated stable system identifier: {} (MAC address included: {})", 
                    cachedSystemIdentifier, useMacAddressInIdentifier);
                return cachedSystemIdentifier;
                
            } catch (Exception e) {
                logger.error("Error generating system identifier", e);
                cachedSystemIdentifier = "SYSTEM_ERROR";
                return cachedSystemIdentifier;
            }
        }
    }
    
    /**
     * Force regeneration of system identifier (for testing purposes)
     */
    public void clearCachedIdentifier() {
        synchronized (identifierLock) {
            cachedSystemIdentifier = null;
            logger.info("Cleared cached system identifier");
        }
    }
} 