package com.ftwegc.myapplication.domain.model

/** Due-date status of a subscription relative to "today". */
enum class DueStatus {
    /** No due date is known. */
    NONE,

    /** Due date is further away than the "soon" window. */
    ACTIVE,

    /** Due within the "soon" window (1..soonDays days from today, inclusive). */
    DUE_SOON,

    /** Due date is today; not yet expired. */
    DUE_TODAY,

    /** Due date is before today. */
    EXPIRED,

    /** Lifetime / long-term valid subscription. */
    PERMANENT,
}
