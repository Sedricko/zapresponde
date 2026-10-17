package app.zapresponde

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract

/** The phone's address book, read only to pick contacts and to tell saved contacts from unknown numbers. */
object Contacts {
    private var cache: Set<String> = emptySet()
    private var cachedAt = 0L

    fun allowed(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Display names, sorted and without repeats; empty without permission. */
    fun all(ctx: Context): List<String> {
        if (!allowed(ctx)) return emptyList()
        val out = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add) }
        return out.toList()
    }

    /** Normalized names for [Engine.decide], re-read at most every 10 minutes. */
    fun names(ctx: Context): Set<String> {
        val now = System.currentTimeMillis()
        if (now - cachedAt > 10 * 60_000L) {
            cache = all(ctx).map { Engine.normalize(it) }.toSet()
            cachedAt = now
        }
        return cache
    }
}
