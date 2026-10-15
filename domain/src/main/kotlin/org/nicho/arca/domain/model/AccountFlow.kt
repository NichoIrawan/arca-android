package org.nicho.arca.domain.model

// Money into and out of one account over a period, transfers included (Account Detail).
data class AccountFlow(
    val inMinor: Long,
    val outMinor: Long,
    val netMinor: Long,
)
