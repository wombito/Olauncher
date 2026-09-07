package app.olauncher.helper

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import app.olauncher.data.NoteContent

object NoteRepository {

    /**
     * Reads the note the given persisted [uriString] points to. Runs blocking IO, so call it
     * off the main thread. Returns null when the file is gone or the permission was revoked.
     */
    fun readNote(context: Context, uriString: String): NoteContent? {
        if (uriString.isEmpty()) return null
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return null

        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return null
            NoteContent(
                title = displayName(context, uri),
                markdown = stripFrontmatter(text),
            )
        } catch (exception: Exception) {
            // SecurityException (permission lost), FileNotFoundException (moved/deleted), etc.
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull().orEmpty()

        return name.removeSuffix(".md").ifEmpty { uri.lastPathSegment.orEmpty() }
    }

    /**
     * Drops a leading YAML frontmatter block (`---` ... `---`/`...`) so Obsidian metadata is not
     * rendered as note content. Leaves the text untouched when there is no complete block.
     */
    fun stripFrontmatter(markdown: String): String {
        if (!markdown.startsWith("---")) return markdown

        val lines = markdown.lines()
        if (lines.firstOrNull()?.trim() != "---") return markdown

        val closingOffset = lines.drop(1).indexOfFirst { it.trim() == "---" || it.trim() == "..." }
        if (closingOffset == -1) return markdown

        return lines.drop(closingOffset + 2).joinToString("\n").trimStart('\n')
    }
}
