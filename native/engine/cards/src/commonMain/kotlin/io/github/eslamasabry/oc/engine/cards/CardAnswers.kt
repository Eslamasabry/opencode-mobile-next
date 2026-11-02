package io.github.eslamasabry.oc.engine.cards

import kotlinx.collections.immutable.toImmutableList

/** Normalised map → model (Flutter `gen_ui_mapping.dart`). Only ever called on validator output. */
internal object CardMapping {
    private fun str(m: Map<*, *>, k: String): String = m[k] as String

    private fun list(m: Map<*, *>, k: String): List<*> = (m[k] as List<*>?) ?: emptyList<Any?>()

    private fun dbl(v: Any?): Double = CardValidator.asDouble(v!!)

    private fun option(o: Map<*, *>): CardOption = CardOption(str(o, "id"), str(o, "label"), o["detail"] as String?)

    private fun field(f: Map<*, *>): CardField =
        CardField(
            id = str(f, "id"),
            label = str(f, "label"),
            type = CardFieldType.entries.first { it.name.equals(str(f, "type"), ignoreCase = true) },
            required = f["required"] as Boolean,
            placeholder = f["placeholder"] as String?,
            defaultValue = f["default"],
            options = list(f, "options").map { option(it as Map<*, *>) }.toImmutableList(),
            min = f["min"]?.let(::dbl),
            max = f["max"]?.let(::dbl),
        )

    fun ask(a: Map<*, *>): CardAsk =
        when (a["kind"]) {
            "choice" -> CardAsk.Choice(list(a, "options").map { option(it as Map<*, *>) }.toImmutableList(), a["multi"] as Boolean)
            "form" -> CardAsk.Form(list(a, "fields").map { field(it as Map<*, *>) }.toImmutableList(), a["submitLabel"] as String?)
            "confirm" -> CardAsk.Confirm(a["confirmLabel"] as String?, a["cancelLabel"] as String?, a["tone"] == "danger")
            "photo" -> CardAsk.Photo(str(a, "purpose"), (a["max"] as Long).toInt())
            else -> CardValidator.fail()
        }

    fun node(n: Map<*, *>): CardNode =
        when (n["type"]) {
            "text" -> CardNode.Text(str(n, "text"))
            "keyValue" ->
                CardNode.KeyValue(list(n, "rows").map { val r = it as Map<*, *>; CardNode.KeyValueRow(str(r, "key"), str(r, "value")) }.toImmutableList())
            "list" ->
                CardNode.Checklist(
                    list(n, "items").map { val r = it as Map<*, *>; CardNode.ListItem(str(r, "text"), r["done"] as Boolean) }.toImmutableList(),
                    if (n["style"] == "check") CardNode.ListStyle.Check else CardNode.ListStyle.Bullet,
                )
            "table" ->
                CardNode.Table(
                    list(n, "columns").map { it as String }.toImmutableList(),
                    list(n, "rows").map { row -> (row as List<*>).map { it as String }.toImmutableList() }.toImmutableList(),
                )
            "chart" ->
                CardNode.Chart(
                    if (n["kind"] == "line") CardNode.ChartKind.Line else CardNode.ChartKind.Bar,
                    n["unit"] as String?,
                    list(n, "labels").map { it as String }.toImmutableList(),
                    list(n, "series").map {
                        val r = it as Map<*, *>
                        CardNode.Series(str(r, "name"), list(r, "values").map(::dbl).toImmutableList())
                    }.toImmutableList(),
                )
            "code" -> CardNode.Code(n["language"] as String?, str(n, "text"))
            "diffStat" ->
                CardNode.DiffStat(
                    list(n, "files").map {
                        val r = it as Map<*, *>
                        CardNode.FileStat(str(r, "path"), r["added"] as Long, r["removed"] as Long)
                    }.toImmutableList()
                )
            "progress" -> CardNode.Progress(str(n, "label"), dbl(n["value"]))
            "callout" ->
                CardNode.Callout(
                    when (n["tone"]) {
                        "warning" -> CardNode.CalloutTone.Warning
                        "success" -> CardNode.CalloutTone.Success
                        else -> CardNode.CalloutTone.Info
                    },
                    str(n, "text"),
                )
            "link" -> CardNode.Link(str(n, "label"), str(n, "url"))
            else -> CardValidator.fail()
        }
}

/** The answer value rules (Flutter `_structuralAnswer`, `_validatedAnswer`, `_answerSummary`). */
internal object CardAnswers {
    private val V = CardValidator

    /** Shape only: exactly one of choice/form/confirm/photo, bounded. */
    fun structural(raw: Any?) {
        val o = V.obj(raw, listOf("choice", "form", "confirm", "photo"))
        if (o.size != 1) V.fail()
        when {
            o.containsKey("choice") -> V.unique(V.arr(o["choice"], 1, 8).map { V.id(it, V.OPTION_ID) })
            o.containsKey("form") -> {
                val form = o["form"] as? Map<*, *> ?: V.fail()
                if (form.size > 12) V.fail()
                for ((k, value) in form) {
                    V.id(k, V.FIELD_ID)
                    when (value) {
                        is String -> if (V.scalars(value) > 2000) V.fail()
                        is Long, is Int, is Double -> V.num(value)
                        is Boolean -> Unit
                        else -> V.fail()
                    }
                }
            }
            o.containsKey("confirm") -> V.bool(o["confirm"])
            else -> V.int(o["photo"], 1, 4)
        }
    }

    /** Against the ORIGINAL ask, at encode and at decode (B-CHAT-032). */
    fun validated(ask: CardAsk, raw: Any?): Map<String, Any?> {
        structural(raw)
        val value = raw as Map<*, *>
        return when (ask) {
            is CardAsk.Choice -> {
                val ids = V.arr(value["choice"], 1, if (ask.multi) ask.options.size else 1).map { it as String }
                if (ids.any { id -> ask.options.none { it.id == id } }) V.fail()
                linkedMapOf("choice" to ids)
            }
            is CardAsk.Form -> {
                val form = V.obj(value["form"], ask.fields.map { it.id })
                val result = LinkedHashMap<String, Any?>()
                for (f in ask.fields) {
                    if (!form.containsKey(f.id)) {
                        if (f.required) V.fail()
                        continue
                    }
                    val desc = LinkedHashMap<String, Any?>()
                    desc["type"] = f.type.name.lowercase()
                    desc["required"] = f.required
                    f.min?.let { desc["min"] = it }
                    f.max?.let { desc["max"] = it }
                    desc["options"] = f.options.map { mapOf("id" to it.id) }
                    result[f.id] = V.fieldValue(form[f.id], desc)
                }
                linkedMapOf("form" to result)
            }
            is CardAsk.Confirm -> linkedMapOf("confirm" to V.bool(value["confirm"]))
            is CardAsk.Photo -> linkedMapOf("photo" to V.int(value["photo"], 1, ask.max.toLong()))
        }
    }

    fun summary(ask: CardAsk, value: Map<String, Any?>): String {
        val raw =
            when (ask) {
                is CardAsk.Choice -> (value["choice"] as List<*>).joinToString(", ") { id -> ask.options.first { it.id == id }.label }
                is CardAsk.Form -> "Submitted ${(value["form"] as Map<*, *>).size} fields"
                is CardAsk.Confirm -> if (value["confirm"] == true) ask.confirmLabel ?: "Confirmed" else ask.cancelLabel ?: "Declined"
                is CardAsk.Photo -> {
                    val n = value["photo"] as Long
                    "Submitted $n photo${if (n == 1L) "" else "s"}"
                }
            }
        val collapsed = StringBuilder()
        var space = false
        for (c in V.stripControls(raw)) {
            if (V.isJsSpace(c)) {
                space = true
            } else {
                if (space) collapsed.append(' ')
                space = false
                collapsed.append(c)
            }
        }
        if (space) collapsed.append(' ')
        val clean = V.dartTrim(collapsed.toString())
        // At most 240 scalars.
        var scalars = 0
        var i = 0
        while (i < clean.length && scalars < 240) {
            i += if (clean[i].isHighSurrogate() && i + 1 < clean.length) 2 else 1
            scalars++
        }
        return clean.substring(0, i)
    }
}
