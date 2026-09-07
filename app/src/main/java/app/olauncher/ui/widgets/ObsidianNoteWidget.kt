package app.olauncher.ui.widgets

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.NoteContent
import app.olauncher.data.Prefs
import app.olauncher.databinding.WidgetObsidianNoteBinding
import app.olauncher.helper.NoteRepository
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObsidianNoteWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val binding = WidgetObsidianNoteBinding.inflate(LayoutInflater.from(context), this, true)
    private val markwon = Markwon.builder(context)
        // Obsidian renders a single newline as a line break; CommonMark collapses it.
        .usePlugin(SoftBreakAddsNewLinePlugin.create())
        .usePlugin(TaskListPlugin.create(context))
        .build()

    fun bind(scope: CoroutineScope, onPickNote: () -> Unit) {
        binding.notePick.setOnClickListener { onPickNote() }
        binding.noteChange.setOnClickListener { onPickNote() }
        binding.noteError.setOnClickListener { onPickNote() }

        val uriString = Prefs(context).obsidianNoteUri
        if (uriString.isEmpty()) {
            showState(pick = true)
            return
        }

        showState(content = true)
        scope.launch {
            val note = withContext(Dispatchers.IO) {
                NoteRepository.readNote(context, uriString)
            }
            if (note == null) showState(error = true) else render(note)
        }
    }

    private fun render(note: NoteContent) {
        showState(content = true)
        binding.noteTitle.text = note.title
        markwon.setMarkdown(binding.noteBody, note.markdown)

        // Tapping the card (anywhere but a link) opens the note in Obsidian.
        val open = View.OnClickListener { openInObsidian(note.title) }
        setOnClickListener(open)
        binding.noteTitle.setOnClickListener(open)
        binding.noteBody.setOnClickListener(open)
        binding.noteScroll.scrollTo(0, 0)
    }

    private fun openInObsidian(noteName: String) {
        val uri = "obsidian://open?file=${Uri.encode(noteName)}".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(context, R.string.obsidian_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showState(pick: Boolean = false, error: Boolean = false, content: Boolean = false) {
        binding.notePick.isVisible = pick
        binding.noteError.isVisible = error
        binding.noteHeader.isVisible = content
        binding.noteScroll.isVisible = content
        if (!content) {
            setOnClickListener(null)
            isClickable = false
            binding.noteBody.setOnClickListener(null)
            binding.noteBody.isClickable = false
            binding.noteTitle.setOnClickListener(null)
            binding.noteTitle.isClickable = false
        }
    }
}
