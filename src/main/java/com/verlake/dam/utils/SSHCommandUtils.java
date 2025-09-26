package com.verlake.dam.utils;

import java.util.Arrays;

/**
 * Static utility class for SSH command operations
 * Avoids circular dependencies by providing static methods
 */
public class SSHCommandUtils {

    /**
     * Private constructor to prevent instantiation of utility class
     */
    private SSHCommandUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Common prompt patterns for command completion detection
    private static final String[] PROMPT_PATTERNS = {
        "\\$\\s*+$",                    // $ at end of line (possessive)
        "#\\s*+$",                      // # at end of line (root prompt, possessive)
        ">\\s*+$",                      // > at end of line (possessive)
        "\\w++@\\w++:[\\S]{0,200}\\$\\s*+$",      // user@host:path$ pattern (bounded, possessive)
        "\\w++@\\w++:[\\S]{0,200}#\\s*+$",       // user@host:path# pattern (root, bounded, possessive)
        "\\[\\w++@\\w++\\s++\\w++\\]\\$\\s*+$", // [user@host dir]$ pattern (possessive)
        "\\[\\w++@\\w++\\s++\\w++\\]#\\s*+$",    // [user@host dir]# pattern (root, possessive)
        "\\w++@\\w++\\$\\s*+$",           // user@host$ pattern (no path, possessive)
        "\\w++@\\w++#\\s*+$"              // user@host# pattern (root, no path, possessive)
    };

    /**
     * Extract only the command output from the full terminal output
     * This is the unified version used by all services
     */
    public static String extractCommandOutput(String fullOutput, String command) {
        StringBuilder result = new StringBuilder();
        
        if (fullOutput == null || fullOutput.isEmpty()) {
            return result.toString();
        }

        String[] lines = fullOutput.split("\n");
        boolean foundCommand = false;
        boolean foundPrompt = false;

        for (String line : lines) {
            String trimmedLine = line.trim();
        
            boolean skipLine = trimmedLine.isEmpty() || trimmedLine.contains(command);
        
            if (skipLine) {
                if (trimmedLine.contains(command)) {
                    foundCommand = true;
                }
            } else if (foundCommand && !foundPrompt) {
                if (isPromptLine(trimmedLine)) {
                    foundPrompt = true;
                    break; // ✅ only one exit statement
                }
                result.append(trimmedLine).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Check if a line contains a shell prompt
     */
    private static boolean isPromptLine(String line) {
        return line.contains("$ ") || line.contains("# ") || 
               line.contains("~$") || line.contains("~#") ||
               line.contains("root@") || line.contains("user@");
    }

    /**
     * Detect if output contains a shell prompt (indicating command completion)
     * This is the unified version used by all services
     */
    public static boolean isPromptDetected(String output) {
        if (output == null || output.trim().isEmpty()) {
            return false;
        }
        
        // Remove ANSI escape sequences for cleaner pattern matching using bounded quantifiers
        String cleanOutput = output.replaceAll("\\x1b\\[[0-9;]{0,50}[a-zA-Z]", "").replaceAll("\\x1b\\[\\?[0-9;]{0,50}[a-zA-Z]", "");
        
        // Check each pattern using secure approach with possessive quantifiers
        for (String pattern : PROMPT_PATTERNS) {
            // Use direct pattern matching with possessive quantifiers for security
            if (cleanOutput.matches(pattern)) {
                return true;
            }
        }
        
        // Additional check: look for prompt at the end of the output
        String lastLine = cleanOutput.trim();
        return lastLine.endsWith("$") || lastLine.endsWith("#") || lastLine.endsWith(">");
    }

    /**
     * Clean up command result by removing command echo and final prompt
     * This is the unified version used by all services
     */
    public static String cleanCommandResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            return result;
        }
        
        // First, remove ANSI escape sequences using bounded quantifiers for security
        String cleanResult = result.replaceAll("\\x1b\\[[0-9;]{0,50}[a-zA-Z]", "").replaceAll("\\x1b\\[\\?[0-9;]{0,50}[a-zA-Z]", "");
        
        // Remove trailing prompt patterns (but preserve the command output)
        // Using secure regex patterns following OWASP guidelines to prevent ReDoS attacks
        // Use possessive quantifiers and bounded quantifiers for security
        cleanResult = cleanResult.replaceAll("\\$\\s*+$", "");
        cleanResult = cleanResult.replaceAll("#\\s*+$", "");
        cleanResult = cleanResult.replaceAll(">\\s*+$", "");
        
        // Use possessive quantifiers and bounded quantifiers for user@host patterns
        // Limit path matching to reasonable length to prevent exponential backtracking
        cleanResult = cleanResult.replaceAll("\\w++@\\w++:[\\S]{0,200}\\$\\s*+$", "");
        cleanResult = cleanResult.replaceAll("\\w++@\\w++:[\\S]{0,200}#\\s*+$", "");
        cleanResult = cleanResult.replaceAll("\\[\\w++@\\w++\\s++\\w++\\]\\$\\s*+$", "");
        cleanResult = cleanResult.replaceAll("\\[\\w++@\\w++\\s++\\w++\\]#\\s*+$", "");
        
        // Remove command echo (the command itself being displayed at the beginning)
        // Split into lines and remove the first line if it's just the command
        String[] lines = cleanResult.split("\n");
        if (lines.length > 1) {
            String firstLine = lines[0].trim();
            // If the first line is just a simple command (no prompt), remove it
            if (firstLine.matches("^[a-zA-Z0-9_\\-\\./]+$") && !firstLine.contains("@") && !firstLine.contains("$") && !firstLine.contains("#")) {
                // Remove the first line (command echo)
                cleanResult = String.join("\n", Arrays.copyOfRange(lines, 1, lines.length));
            }
        }
        
        // Trim whitespace
        cleanResult = cleanResult.trim();
        
        return cleanResult;
    }
}
