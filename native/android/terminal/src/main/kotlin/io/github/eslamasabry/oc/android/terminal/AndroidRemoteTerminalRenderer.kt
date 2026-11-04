package io.github.eslamasabry.oc.android.terminal

import android.app.Application
import android.graphics.Typeface
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.domain.RemoteTerminalRenderSession
import io.github.eslamasabry.oc.core.domain.RemoteTerminalRenderer
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Real VT emulation for daemon PTYs; the external-I/O constructor never launches a local process.
 */
class AndroidRemoteTerminalRenderer(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
) : RemoteTerminalRenderer {
    override suspend fun open(
        onInput: (String) -> Unit,
        onResize: (rows: Int, cols: Int) -> Unit,
    ): Outcome<RemoteTerminalRenderSession> =
        withContext(dispatchers.main) {
            attempt({ Failure.Device() }) { Session(onInput, onResize) }
        }

    private inner class Session(onInput: (String) -> Unit, onResize: (Int, Int) -> Unit) :
        RemoteTerminalRenderSession {
        private var closed = false
        private val text = MutableStateFlow("")
        override val transcript = text.asStateFlow()
        private val grid =
            TerminalView(app, null).apply {
                setTerminalViewClient(TerminalInputClient(this))
                setIsTerminalViewKeyLoggingEnabled(false)
                setTextSize(
                    (14 * app.resources.displayMetrics.scaledDensity).toInt().coerceAtLeast(14)
                )
                setTypeface(Typeface.MONOSPACE)
                importantForAutofill =
                    android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
        private val session =
            TerminalSession(
                object : TerminalSession.ExternalIo {
                    override fun write(bytes: ByteArray, offset: Int, count: Int) {
                        if (!closed && count in 1..65_536)
                            onInput(bytes.copyOfRange(offset, offset + count).decodeToString())
                    }

                    override fun resize(columns: Int, rows: Int, cellWidth: Int, cellHeight: Int) {
                        if (!closed && columns > 0 && rows > 0) onResize(rows, columns)
                    }

                    override fun close() {
                        closed = true
                    }
                },
                2000,
                object : QuietSessionClient() {
                    override fun onTextChanged(session: TerminalSession) {
                        val raw =
                            redactor.redact(session.emulator?.screen?.transcriptText.orEmpty())
                        var start = (raw.length - 100_000).coerceAtLeast(0)
                        if (
                            start > 0 &&
                                start < raw.length &&
                                raw[start].isLowSurrogate() &&
                                raw[start - 1].isHighSurrogate()
                        )
                            start++
                        text.value = raw.substring(start)
                        grid.onScreenUpdated()
                    }
                },
            )
        override val view =
            object : PlatformViewHandle {
                override val native: Any = grid
            }

        init {
            session.updateSize(80, 24, 0, 0)
            grid.attachSession(session)
        }

        override suspend fun append(text: String): Outcome<Unit> =
            withContext(dispatchers.main) {
                if (closed) return@withContext Failure.Refused(RefusalReason.StaleScope).failed()
                val bytes = text.encodeToByteArray()
                if (bytes.size > 1024 * 1024) return@withContext Failure.UnreadableReply().failed()
                attempt({ Failure.Device() }) { session.appendExternalOutput(bytes) }
            }

        override suspend fun close(): Outcome<Unit> =
            withContext(dispatchers.main) {
                attempt({ Failure.Device() }) {
                    session.finishIfRunning()
                    closed = true
                }
            }
    }
}
