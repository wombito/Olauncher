package app.olauncher.data

enum class WidgetType(
    /** Default viewport height in dp; 0 means wrap to the widget's natural size. */
    val defaultHeightDp: Int,
) {
    CALENDAR(0),
    YEAR_PROGRESS(0),
    EVENTS(320),
    OBSIDIAN_NOTE(300);

    companion object {

        private const val SEPARATOR = ","

        val DEFAULT_ORDER: List<WidgetType> = listOf(CALENDAR, YEAR_PROGRESS, EVENTS)

        fun encode(types: List<WidgetType>): String = types.joinToString(SEPARATOR) { it.name }

        fun decode(value: String): List<WidgetType> {
            if (value.isEmpty()) return emptyList()
            return value.split(SEPARATOR).mapNotNull { name ->
                entries.firstOrNull { it.name == name }
            }
        }

        fun encodeHeights(heights: Map<WidgetType, Int>): String =
            heights.entries.joinToString(SEPARATOR) { "${it.key.name}=${it.value}" }

        fun decodeHeights(value: String): Map<WidgetType, Int> {
            if (value.isEmpty()) return emptyMap()
            return value.split(SEPARATOR).mapNotNull { entry ->
                val (name, px) = entry.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
                val type = entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                val value = px.toIntOrNull() ?: return@mapNotNull null
                type to value
            }.toMap()
        }
    }
}
