package io.csslabs.sdk

// Where the app's consent switches are kept. Everything is off by default.
interface ConsentBackend {
    fun isGranted(signal: Signal): Boolean
    fun set(signal: Signal, granted: Boolean)
}

class MemoryConsent : ConsentBackend {
    private val on = HashSet<Signal>()

    @Synchronized
    override fun isGranted(signal: Signal): Boolean = signal in on

    @Synchronized
    override fun set(signal: Signal, granted: Boolean) {
        if (granted) on.add(signal) else on.remove(signal)
    }
}
