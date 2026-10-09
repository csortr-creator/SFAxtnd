package io.nekohasekai.sfa.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PartialUpdatePolicyTest {

    private fun input(
        trigger: UpdateTrigger,
        received: Int,
        imported: Int,
        rejected: Int = (received - imported).coerceAtLeast(0),
        reliability: StatsReliability = StatsReliability.COMPLETE,
        hadPrevious: Boolean = true,
    ) =
        PartialUpdateInput(
            trigger = trigger,
            received = received,
            imported = imported,
            rejected = rejected,
            reliability = reliability,
            hadPreviousConfig = hadPrevious,
        )

    @Test
    fun firstFull() {
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(input(UpdateTrigger.FIRST_IMPORT, 100, 100, 0, hadPrevious = false)),
        )
    }

    @Test
    fun firstPartial() {
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(input(UpdateTrigger.FIRST_IMPORT, 100, 99, 1, hadPrevious = false)),
        )
    }

    @Test
    fun autoFull() {
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(input(UpdateTrigger.AUTO_UPDATE, 100, 100, 0)),
        )
    }

    @Test
    fun autoPartial() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(input(UpdateTrigger.AUTO_UPDATE, 100, 99, 1)),
        )
    }

    @Test
    fun manualFull() {
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(input(UpdateTrigger.MANUAL_UPDATE, 50, 50, 0)),
        )
    }

    @Test
    fun manualPartial() {
        assertEquals(
            PolicyDecision.AWAIT_CONFIRMATION,
            PartialUpdatePolicy.decide(input(UpdateTrigger.MANUAL_UPDATE, 100, 99, 1)),
        )
    }

    @Test
    fun shrink100to10FullImportIsApply() {
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(input(UpdateTrigger.AUTO_UPDATE, 10, 10, 0)),
        )
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(input(UpdateTrigger.MANUAL_UPDATE, 10, 10, 0)),
        )
    }

    @Test
    fun hardPartial100to1RejectedOnAuto() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(input(UpdateTrigger.AUTO_UPDATE, 100, 1, 99)),
        )
        assertEquals(
            PolicyDecision.AWAIT_CONFIRMATION,
            PartialUpdatePolicy.decide(input(UpdateTrigger.MANUAL_UPDATE, 100, 1, 99)),
        )
    }

    @Test
    fun zeroImported() {
        assertEquals(
            PolicyDecision.REJECT_FIRST,
            PartialUpdatePolicy.decide(
                input(UpdateTrigger.FIRST_IMPORT, 100, 0, 100, hadPrevious = false),
            ),
        )
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(input(UpdateTrigger.AUTO_UPDATE, 100, 0, 100)),
        )
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(input(UpdateTrigger.MANUAL_UPDATE, 0, 0, 0)),
        )
    }

    @Test
    fun incompleteStatsNeverTreatedAsFull() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(
                input(
                    UpdateTrigger.AUTO_UPDATE,
                    10,
                    10,
                    0,
                    StatsReliability.INCOMPLETE,
                    hadPrevious = true,
                ),
            ),
        )
        assertEquals(
            PolicyDecision.AWAIT_CONFIRMATION,
            PartialUpdatePolicy.decide(
                input(
                    UpdateTrigger.MANUAL_UPDATE,
                    10,
                    10,
                    0,
                    StatsReliability.INCOMPLETE,
                    hadPrevious = true,
                ),
            ),
        )
    }

    @Test
    fun unknownStatsConservativeOnAuto() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(
                input(UpdateTrigger.AUTO_UPDATE, 0, 5, 0, StatsReliability.UNKNOWN, true),
            ),
        )
    }

    @Test
    fun noPreviousLkgFirstPartialStillApply() {
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(
                input(UpdateTrigger.FIRST_IMPORT, 5, 3, 2, hadPrevious = false),
            ),
        )
    }

    @Test
    fun invalidNegativeCountersCoerced() {
        assertEquals(
            PolicyDecision.REJECT_FIRST,
            PartialUpdatePolicy.decide(
                PartialUpdateInput(
                    UpdateTrigger.FIRST_IMPORT,
                    received = -1,
                    imported = -5,
                    rejected = -2,
                    reliability = StatsReliability.COMPLETE,
                    hadPreviousConfig = false,
                ),
            ),
        )
    }

    @Test
    fun noPercentageThresholdNinetyNinePercentStillPartial() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(input(UpdateTrigger.AUTO_UPDATE, 100, 99, 1)),
        )
    }


    @Test
    fun reliabilityOfUriListIsComplete() {
        val issues =
            listOf(
                SubscriptionImportIssue(3, "n", "x", ImportIssueCode.PARSE_ERROR),
            )
        val report = SubscriptionImportReport(3, 2, issues, format = "Список ссылок")
        assertEquals(StatsReliability.COMPLETE, PartialUpdatePolicy.reliabilityOf(report))
    }

    @Test
    fun reliabilityOfNullIsUnknown() {
        assertEquals(StatsReliability.UNKNOWN, PartialUpdatePolicy.reliabilityOf(null))
    }

    @Test
    fun autoIncompleteWithLkgRejects() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(
                PartialUpdateInput(
                    UpdateTrigger.AUTO_UPDATE,
                    5,
                    5,
                    0,
                    StatsReliability.INCOMPLETE,
                    true,
                ),
            ),
        )
    }

    @Test
    fun autoWithoutLkgIncompleteStillApplyIfImported() {
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(
                PartialUpdateInput(
                    UpdateTrigger.AUTO_UPDATE,
                    5,
                    5,
                    0,
                    StatsReliability.INCOMPLETE,
                    false,
                ),
            ),
        )
    }

    @Test
    fun reliabilityOfSingBoxFullConfigIsComplete() {
        val report = SubscriptionImportReport(5, 5, emptyList(), format = "sing-box JSON")
        assertEquals(StatsReliability.COMPLETE, PartialUpdatePolicy.reliabilityOf(report))
    }

    @Test
    fun singBoxFullDoesNotBlockAuto() {
        val report = SubscriptionImportReport(8, 8, emptyList(), format = "sing-box JSON")
        val rel = PartialUpdatePolicy.reliabilityOf(report)
        assertEquals(StatsReliability.COMPLETE, rel)
        assertEquals(
            PolicyDecision.APPLY,
            PartialUpdatePolicy.decide(
                PartialUpdateInput(
                    UpdateTrigger.AUTO_UPDATE,
                    report.received,
                    report.imported,
                    report.issues.size,
                    rel,
                    true,
                ),
            ),
        )
    }

    @Test
    fun contradictoryCompleteTreatedAsUncertain() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(
                PartialUpdateInput(
                    UpdateTrigger.AUTO_UPDATE,
                    received = 10,
                    imported = 10,
                    rejected = 1,
                    reliability = StatsReliability.COMPLETE,
                    hadPreviousConfig = true,
                ),
            ),
        )
    }

    @Test
    fun importedGreaterThanReceivedIsUncertain() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(
                PartialUpdateInput(
                    UpdateTrigger.AUTO_UPDATE,
                    received = 5,
                    imported = 9,
                    rejected = 0,
                    reliability = StatsReliability.COMPLETE,
                    hadPreviousConfig = true,
                ),
            ),
        )
    }

    @Test
    fun negativeReceivedNotCoercedToApply() {
        assertEquals(
            PolicyDecision.REJECT_KEEP_LKG,
            PartialUpdatePolicy.decide(
                PartialUpdateInput(
                    UpdateTrigger.AUTO_UPDATE,
                    received = -1,
                    imported = 5,
                    rejected = 0,
                    reliability = StatsReliability.COMPLETE,
                    hadPreviousConfig = true,
                ),
            ),
        )
    }
}
