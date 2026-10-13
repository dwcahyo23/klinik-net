package net.klinik.rental.domain.validation

/**
 * Thrown when a rupiah amount is zero or negative.
 *
 * Two rules need this and neither tolerates a silent pass-through. AD-3 requires every
 * `ALLOCATION.amount` to be `> 0`, so a zero row would satisfy `Σ allocation == amount`
 * while storing nothing at all. And a zero-valued [net.klinik.rental.domain.model.Payment]
 * would enter the queue and leave no trace of the fact that it was counted at all.
 *
 * The prototype failed in the opposite direction — `parseInt(x) || 250000`, where a bad
 * parse quietly became a good amount. This is the fail-loud end of the same rule (AD-4):
 * money is rejected once, where it enters the domain, and never defaulted.
 *
 * The message is built from [MIN_AMOUNT] rather than restating "greater than zero", so the
 * text cannot end up contradicting the rule the code enforces if the bound ever moves —
 * the same reason `InvalidCutoffDayException` quotes `CutoffPeriod`'s constants.
 */
class InvalidAmountException(
    /** The offending value, carried so callers can report or log it. */
    val amount: Long,
) : IllegalArgumentException(
    "amount must be at least $MIN_AMOUNT rupiah, but was $amount",
) {
    companion object {
        /**
         * Smallest legal amount, in whole rupiah (AD-2).
         *
         * One rupiah is the floor because this system has no sub-rupiah unit anywhere:
         * every amount is `Long` rupiah, so everything below this is zero or a negative
         * number — never a fraction that could round into a valid day.
         */
        const val MIN_AMOUNT = 1L

        /**
         * Validates an amount without building anything — mirrors
         * `CutoffPeriod.requireValidCutoffDay`, and is what `Payment` and `Allocation`
         * call from their initialisers.
         */
        fun requireValid(amount: Long) {
            if (amount < MIN_AMOUNT) throw InvalidAmountException(amount)
        }
    }
}