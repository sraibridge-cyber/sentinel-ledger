package io.csslabs.sdk

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

// The CC and SS layer. The framework (Coherence Calculus and System Sanity,
// Technical Report v3) defines the formula, but it leaves the choice of
// dimensions, scoring functions, weights and threshold to the operator, and
// it requires the threshold to come from a reference battery. The SDK supplies
// the formula and the interface. It does not supply a mapping from device
// signals to dimensions, because none exists yet.
interface CoherenceScorer {
    // Dimension names the scorer needs, for example "exposure" and "timing".
    val dimensions: List<String>

    // One weight per dimension. The weights must sum to 1.0, and there must be
    // at least two dimensions.
    val weights: Map<String, Double>

    // Returns a score in (0, 1] for each dimension this reading supports, or
    // null when this reading cannot be scored.
    fun score(reading: Map<String, Any?>): Map<String, Double>?
}

// The default. It computes nothing and says so.
object NoScorer : CoherenceScorer {
    override val dimensions: List<String> = emptyList()
    override val weights: Map<String, Double> = emptyMap()
    override fun score(reading: Map<String, Any?>): Map<String, Double>? = null
}

object Coherence {
    // mu(S) = exp( sum over i of w_i * ln s_i ). Returns null unless there are at
    // least two dimensions, the weights sum to 1.0, and every dimension has a
    // score in (0, 1].
    fun geometricMean(scores: Map<String, Double>, weights: Map<String, Double>): Double? {
        if (weights.size < 2) return null
        if (abs(weights.values.sum() - 1.0) > 1e-9) return null
        var acc = 0.0
        for ((dim, w) in weights) {
            val s = scores[dim] ?: return null
            if (!(s > 0.0 && s <= 1.0)) return null
            acc += w * ln(s)
        }
        return exp(acc)
    }
}

// What a sample says about coherence. Until an operator sets a threshold from
// reference data, calibrated is false and tau is null, and no verdict is given.
data class CoherenceReport(val value: Double?, val calibrated: Boolean, val tau: Double?, val note: String)

// The sanity predicate Sanity(S) = (mu >= tau) and R(S) and G(S). Here R is
// "the log is intact" and G is "every reading was taken while its signal was
// granted". The framework leaves the boundary between rules and gates to the
// operator, so this choice is documented rather than presented as settled.
data class SanityStatus(
    val ruleLogIntact: Boolean,
    val gateConsentHeld: Boolean,
    val threshold: Double?,
    val verdict: String,
)
