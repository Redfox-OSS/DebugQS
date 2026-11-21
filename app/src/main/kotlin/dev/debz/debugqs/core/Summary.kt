package dev.debz.debugqs.core

/** One-line status for the tile subtitle and panel header: "Dev · USB · Wi‑Fi", "All off". */
fun DevState.summary(): String {
    if (!hasPermission) return "Setup needed"
    val on = listOfNotNull(
        "Dev".takeIf { developer },
        "USB".takeIf { usb },
        "Wi‑Fi".takeIf { wireless },
    )
    return if (on.isEmpty()) "All off" else on.joinToString(" · ")
}

val DevToggle.title: String
    get() = when (this) {
        DevToggle.Developer -> "Developer options"
        DevToggle.Usb -> "USB debugging"
        DevToggle.Wireless -> "Wireless debugging"
    }

fun ToggleResult.message(): String? = when (this) {
    ToggleResult.Ok -> null
    ToggleResult.NoPermission -> "DebugQS needs permission first"
    ToggleResult.NeedsWifi -> "Connect to a Wi‑Fi network first"
    ToggleResult.Failed -> "Android refused the change"
}
