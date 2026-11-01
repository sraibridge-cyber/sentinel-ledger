package io.sentinel.core

// Who the owner can be told made a sensor request, and how sure we are.
sealed class Requester {
    // An app was in the foreground at that moment. That is a clue, not proof it asked.
    data class InferredForeground(val packageName: String) : Requester()
    // No app was in the foreground, or usage access is off. Android does not report the requester.
    object NotKnown : Requester()
}

data class Attribution(
    val sensor: Sensor,
    val change: Change,
    val atMillis: Long,
    val screenOn: Boolean,
    val androidDetail: String?,
    val requester: Requester,
    val note: String,
)

object Mapper {
    // Maps one sensor event to what the owner can see. It never claims a requester it cannot support.
    fun attribute(event: SensorEvent, foreground: List<ForegroundInterval>): Attribution {
        val hit = foreground
            .filter { event.atMillis >= it.startMillis && event.atMillis <= it.endMillis }
            .maxByOrNull { it.startMillis }
        val requester = if (hit != null) Requester.InferredForeground(hit.packageName) else Requester.NotKnown
        val note = when (requester) {
            is Requester.InferredForeground ->
                "${requester.packageName} was in the foreground at this moment. Android does not confirm it made the request."
            Requester.NotKnown ->
                "Android did not report which app made this request, and no app was in the foreground."
        }
        return Attribution(event.sensor, event.change, event.atMillis, event.screenOn, event.androidDetail, requester, note)
    }
}
