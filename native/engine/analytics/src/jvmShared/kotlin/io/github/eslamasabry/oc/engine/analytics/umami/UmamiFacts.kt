package io.github.eslamasabry.oc.engine.analytics.umami

/**
 * The envelope facts the app keeps up to date while it runs (style, theme, the kinds of connection and agent): one
 * place the wiring writes and the sender reads, by [UmamiField]. A value not on the field's list is refused here, so a
 * wrong word never waits in memory to be sent. Holds words only, never an id, an address or a name.
 */
public object UmamiFacts {
    @Volatile private var current: Map<String, String> = emptyMap()

    /** Sets [field] to [value] (a listed word); null or an unlisted word clears it. */
    @Synchronized
    public fun put(field: UmamiField, value: String?) {
        current = if (value != null && field.accepts(value)) current + (field.wire to value) else current - field.wire
    }

    public fun snapshot(): Map<String, String> = current

    /** Forgets everything (used when usage is turned off). */
    @Synchronized
    public fun clear() {
        current = emptyMap()
    }
}
