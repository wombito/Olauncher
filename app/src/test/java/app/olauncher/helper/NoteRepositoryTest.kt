package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRepositoryTest {

    @Test
    fun `strips yaml frontmatter block from the top`() {
        val input = """
            ---
            title: My Note
            tags: [a, b]
            ---

            # Heading

            Body text
        """.trimIndent()

        assertEquals("# Heading\n\nBody text", NoteRepository.stripFrontmatter(input))
    }

    @Test
    fun `leaves content without frontmatter untouched`() {
        val input = "# Heading\n\nBody text"
        assertEquals(input, NoteRepository.stripFrontmatter(input))
    }

    @Test
    fun `does not strip when there is no closing delimiter`() {
        val input = "---\n\nJust a note that opens with a rule\n"
        assertEquals(input, NoteRepository.stripFrontmatter(input))
    }

    @Test
    fun `handles empty frontmatter`() {
        val input = "---\n---\n# Title"
        assertEquals("# Title", NoteRepository.stripFrontmatter(input))
    }

    @Test
    fun `accepts triple-dot closing delimiter`() {
        val input = "---\nkey: value\n...\nBody"
        assertEquals("Body", NoteRepository.stripFrontmatter(input))
    }

    @Test
    fun `returns empty string when note is only frontmatter`() {
        val input = "---\ntitle: x\n---\n"
        assertEquals("", NoteRepository.stripFrontmatter(input))
    }
}
