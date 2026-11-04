package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.projects.terminal.*
import io.github.eslamasabry.oc.ui.kit.content.terminal.AndroidRemoteTerminalView

@Inject
@ContributesBinding(AppScope::class, replaces = [NoTerminalClipboard::class])
class AndroidTerminalClipboard(private val app: Application) : TerminalClipboard {
    private val clipboard
        get() = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copy(value: String): Boolean {
        clipboard.setPrimaryClip(ClipData.newPlainText("", value))
        return true
    }

    override fun read(): String? =
        clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(app)
            ?.toString()
}

@Inject
@ContributesBinding(AppScope::class, replaces = [NoTerminalGrids::class])
class AndroidTerminalGrids(private val app: Application) : TerminalGrids {
    override fun create(send: (String) -> Unit, resize: (Int, Int) -> Unit): TerminalGrid {
        val terminal = AndroidRemoteTerminalView(app, send, resize)
        return object : TerminalGrid {
            override val view =
                object : PlatformViewHandle {
                    override val native: Any = terminal
                }

            override fun append(text: String) {
                terminal.append(text)
            }

            override fun reset() {
                terminal.clear()
            }

            override fun inputEnabled(on: Boolean) {
                terminal.enableInput(on)
            }

            override fun selectedText(): String = terminal.selectedText()

            override fun selectText(on: Boolean) {
                terminal.selectText(on)
            }

            override fun paste(text: String) {
                terminal.paste(text)
            }

            override fun key(key: String, control: Boolean, alt: Boolean) {
                terminal.key(key, control, alt)
            }

            override fun publishSize() {
                terminal.publishSize()
            }
        }
    }
}

@Inject
@ContributesBinding(AppScope::class, replaces = [NoTerminalPrivacy::class])
class AndroidTerminalPrivacy(private val redactor: Redactor) : TerminalPrivacy {
    override fun describe(
        process: io.github.eslamasabry.oc.core.model.TerminalProcess
    ): TerminalDetails =
        TerminalDetails(
            redactor.details(
                redactor.diagnostics(
                    (listOf(process.command) + process.arguments).joinToString(" ")
                )
            ),
            redactor.details(redactor.diagnostics(process.directory)),
            redactor.details(redactor.diagnostics(process.pid.toString())),
            redactor.details(redactor.diagnostics(process.exitCode?.toString().orEmpty())),
        )
}
