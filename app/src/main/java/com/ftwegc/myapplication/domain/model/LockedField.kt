package com.ftwegc.myapplication.domain.model

/**
 * Fields the user edited in the app; a locked field is never overwritten by Komari sync.
 * Stored as an `Int` bit mask ([mask]). Bit values are persisted: never reorder or reuse them.
 */
enum class LockedField(val mask: Int) {
    NAME(1 shl 0),
    PRICE(1 shl 1),
    CURRENCY(1 shl 2),
    CYCLE(1 shl 3),
    DUE_DATE(1 shl 4),
    AUTO_RENEW(1 shl 5),
    TRAFFIC_LIMIT(1 shl 6),
    TRAFFIC_LIMIT_TYPE(1 shl 7);

    /** True when this field's bit is set in [lockedMask]. */
    fun isLockedIn(lockedMask: Int): Boolean = lockedMask and mask != 0

    companion object {
        /** Nothing locked. */
        const val NONE: Int = 0

        /** Every field locked. */
        val ALL: Int = entries.fold(0) { acc, f -> acc or f.mask }

        /** Mask with exactly [fields] set. */
        fun maskOf(vararg fields: LockedField): Int = fields.fold(0) { acc, f -> acc or f.mask }

        /** Fields whose bits are set in [lockedMask]; unknown bits are ignored. */
        fun fromMask(lockedMask: Int): Set<LockedField> =
            entries.filterTo(mutableSetOf()) { it.isLockedIn(lockedMask) }

        /** [lockedMask] with [field] added. */
        fun lock(lockedMask: Int, field: LockedField): Int = lockedMask or field.mask

        /** [lockedMask] with [field] removed. */
        fun unlock(lockedMask: Int, field: LockedField): Int = lockedMask and field.mask.inv()
    }
}
