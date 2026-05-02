package com.aivoice.input.ai

/**
 * Local JSON repair utility for common malformed JSON issues.
 */
object JsonRepair {

    /**
     * Attempt to repair malformed JSON.
     * Handles: trailing commas, missing brackets, illegal escape sequences.
     *
     * @param json The potentially malformed JSON string
     * @return Repaired JSON string, or original if no repair needed
     */
    fun repair(json: String): String {
        var result = json.trim()

        // 1. Remove trailing commas before ] and }
        result = result.replace(Regex(",\\s*]"), "]")
        result = result.replace(Regex(",\\s*}"), "}")

        // 2. Fix missing closing braces
        val openBraces = result.count { it == '{' }
        val closeBraces = result.count { it == '}' }
        if (openBraces > closeBraces) {
            result += "}".repeat(openBraces - closeBraces)
        }

        // 3. Fix missing closing brackets
        val openBrackets = result.count { it == '[' }
        val closeBrackets = result.count { it == ']' }
        if (openBrackets > closeBrackets) {
            result += "]".repeat(openBrackets - closeBrackets)
        }

        // 4. Remove illegal escape sequences (keep only valid JSON escapes)
        // Valid escapes: \", \\, \/, \b, \f, \n, \r, \t, \uXXXX
        result = result.replace(Regex("\\\\(?!['\"\\\\/bfnrtu])"), "")

        // 5. Fix unescaped quotes in string values (simple cases)
        // This is tricky - only fix obvious cases like {"key": "value with "quote" inside"}
        // Skip complex cases to avoid breaking valid JSON

        return result
    }

    /**
     * Check if JSON appears repairable.
     */
    fun isRepairable(json: String): Boolean {
        val trimmed = json.trim()
        // Must start with { or [
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }
}
