package com.github.peanut996.codeprompthelper

/**
 * Data class representing a single prompt preset.
 * Using 'var' to allow modification in the settings UI.
 */
data class Preset(
    var name: String = "New Preset",
    var prefix: String = "",
    var suffix: String = ""
    // Consider adding a description field later if needed
    // var description: String = ""
) {
    // Override toString for display in lists if not using a custom cell renderer
    override fun toString(): String = name
}