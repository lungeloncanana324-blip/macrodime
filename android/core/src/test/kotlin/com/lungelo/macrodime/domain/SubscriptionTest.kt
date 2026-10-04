/*
 * SubscriptionTest.kt
 *
 * The paywall's promises, pinned down. Everything a person is told before they
 * pay is generated here, so it is tested here: the price after the trial, that
 * it renews, where to cancel, and that no trial is promised to someone the
 * store will charge. Twin of MacroDimeTests/SubscriptionTests.swift.
 */
package com.lungelo.macrodime.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionTest {

    private val annual = ProOffer(ProPlan.Annual, 29.99, "USD", StorePeriod.parse("P2W"))
    private val annualNoTrial = annual.copy(freeTrial = null)
    private val monthly = ProOffer(ProPlan.Monthly, 5.99, "USD")

    private fun usd(amount: Double) = DisplayFormat.currency(amount, "USD")

    // Periods, as the stores write them

    @Test
    fun storePeriodsParseTheFormsBothStoresUse() {
        assertEquals(StorePeriod(14, StorePeriod.Span.Day), StorePeriod.parse("P14D"))
        assertEquals(StorePeriod(2, StorePeriod.Span.Week), StorePeriod.parse("P2W"))
        assertEquals(StorePeriod(1, StorePeriod.Span.Month), StorePeriod.parse("P1M"))
        assertEquals(StorePeriod(1, StorePeriod.Span.Year), StorePeriod.parse("P1Y"))
        assertEquals(StorePeriod(7, StorePeriod.Span.Day), StorePeriod.parse(" p7d "))
    }

    /** Anything else is refused, rather than read as something it is not. */
    @Test
    fun anythingElseIsRefusedRatherThanGuessed() {
        for (bad in listOf("", "P", "P0D", "P-3D", "P1Y2M", "PT1H", "14 days", "P14", "P1000D", "D14P")) {
            assertNull(StorePeriod.parse(bad), "'$bad' should not parse")
        }
    }

    /** A 14-day trial reads the same whether Play Console stores it as days or weeks. */
    @Test
    fun weeksAreSaidInDays() {
        val weeks = assertNotNull(StorePeriod.parse("P2W"))
        val days = assertNotNull(StorePeriod.parse("P14D"))
        assertEquals("14-day", weeks.adjective)
        assertEquals("14-day", days.adjective)
        assertEquals("14 days", weeks.phrase)
        assertEquals("1 day", StorePeriod.parse("P1D")?.phrase)
        assertEquals("1-month", StorePeriod.parse("P1M")?.adjective)
        assertEquals("3 months", StorePeriod.parse("P3M")?.phrase)
    }

    @Test
    fun periodsEndOnTheCalendarNotAfterAFixedNumberOfMillis() {
        val start = LocalDate.of(2027, 1, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = { iso: String -> LocalDate.ofInstant(java.time.Instant.ofEpochMilli(assertNotNull(StorePeriod.parse(iso)).endAfter(start)), ZoneOffset.UTC) }
        assertEquals(LocalDate.of(2027, 2, 14), end("P14D"))
        assertEquals(LocalDate.of(2027, 2, 14), end("P2W"))
        // A month after 31 January is the last day of February, not 3 March.
        assertEquals(LocalDate.of(2027, 2, 28), end("P1M"))
        assertEquals(LocalDate.of(2028, 1, 31), end("P1Y"))
    }

    // The plan cards

    @Test
    fun theYearlyCardShowsItsMonthlyEquivalentItsSavingAndItsTrial() {
        val card = Paywall.card(annual, monthly)
        assertEquals("Yearly", card.title)
        assertEquals("${usd(29.99)} a year", card.price)
        // 29.99 / 12 = 2.4991..., shown to the cent.
        assertEquals("${usd(2.50)} a month", card.perMonth)
        assertEquals("Save 58%", card.badge)
        assertEquals("14 days free", card.trial)
    }

    @Test
    fun theMonthlyCardMakesNoClaimsItCannotBackUp() {
        val card = Paywall.card(monthly, monthly)
        assertEquals("${usd(5.99)} a month", card.price)
        assertNull(card.perMonth)
        assertNull(card.badge)
        assertNull(card.trial)
    }

    /** The badge rounds down, so it never claims a bigger saving than the prices give. */
    @Test
    fun theSavingsBadgeNeverOverstates() {
        // 1 - 29.99 / 71.88 = 58.28%: "58", never "59".
        assertEquals(58, Paywall.annualSavingsPercent(annual, monthly))
        // Exactly half, despite floating point: 30 against 12 x 5.
        assertEquals(50, Paywall.annualSavingsPercent(annual.copy(price = 30.0), monthly.copy(price = 5.0)))
        // No saving, or a loss, gets no badge at all.
        assertNull(Paywall.annualSavingsPercent(annual.copy(price = 71.88), monthly))
        assertNull(Paywall.annualSavingsPercent(annual.copy(price = 80.0), monthly))
        // Under 1% is not worth a badge.
        assertNull(Paywall.annualSavingsPercent(annual.copy(price = 71.50), monthly))
    }

    @Test
    fun aSavingIsNeverWorkedOutAcrossTwoCurrencies() {
        assertNull(Paywall.annualSavingsPercent(annual, monthly.copy(currencyCode = "ZAR")))
        assertNull(Paywall.card(annual, monthly.copy(currencyCode = "ZAR")).badge)
    }

    @Test
    fun theCardsWorkInRandAndInYen() {
        val rand = Paywall.card(ProOffer(ProPlan.Annual, 499.99, "ZAR", StorePeriod.parse("P14D")), ProOffer(ProPlan.Monthly, 99.99, "ZAR"))
        assertEquals("${DisplayFormat.currency(499.99, "ZAR")} a year", rand.price)
        assertEquals("${DisplayFormat.currency(41.67, "ZAR")} a month", rand.perMonth)
        assertEquals("Save 58%", rand.badge)

        // Yen has no minor unit: 4,800 a year is 400 a month, not 400.00.
        val yen = Paywall.card(ProOffer(ProPlan.Annual, 4_800.0, "JPY"), ProOffer(ProPlan.Monthly, 600.0, "JPY"))
        assertEquals("${DisplayFormat.currency(400.0, "JPY")} a month", yen.perMonth)
        assertEquals("Save 33%", yen.badge)
    }

    // The button and the terms

    @Test
    fun theButtonPromisesATrialOnlyWhenTheStoreOffersOne() {
        assertEquals("Start my 14-day free trial", Paywall.callToAction(annual))
        assertEquals("Subscribe for ${usd(29.99)} a year", Paywall.callToAction(annualNoTrial))
        assertEquals("Subscribe for ${usd(5.99)} a month", Paywall.callToAction(monthly))
    }

    @Test
    fun theTermsSayWhatHappensAfterTheTrial() {
        assertEquals(
            "A subscription is required to use MacroDime. " +
                "14 days free, then ${usd(29.99)} a year. MacroDime Pro renews every year until you cancel. " +
                "Cancel in Google Play before the trial ends and you won't be charged.",
            Paywall.disclosure(annual, Store.GooglePlay),
        )
        assertEquals(
            "A subscription is required to use MacroDime. " +
                "${usd(5.99)} a month. MacroDime Pro renews every month until you cancel. Cancel any time in Google Play.",
            Paywall.disclosure(monthly, Store.GooglePlay),
        )
        assertTrue("24 hours" in Paywall.disclosure(annual, Store.AppStore), "Apple's cancellation window must be stated")
    }

    /**
     * Every combination of plan, trial and store states the four things both
     * stores require before purchase: the price, the period, that it renews,
     * and how to cancel. And no generated string carries a dash character.
     */
    @Test
    fun everyCombinationStatesTheFullTermsAndNoDashes() {
        val dashes = listOf(0x2012, 0x2013, 0x2014, 0x2015, 0x2212).map { it.toChar() }
        val trials = listOf(null, "P3D", "P1W", "P2W", "P14D", "P1M").map { it?.let(StorePeriod::parse) }
        for (store in Store.entries) for (plan in ProPlan.entries) for (trial in trials) {
            val offer = ProOffer(plan, if (plan == ProPlan.Annual) 29.99 else 5.99, "USD", trial)
            val terms = Paywall.disclosure(offer, store)
            assertTrue(terms.startsWith(Paywall.SUBSCRIPTION_REQUIRED), "that a subscription is required must come first: $terms")
            assertTrue(Paywall.priceLine(offer) in terms, "price missing: $terms")
            assertTrue("renews" in terms, "renewal missing: $terms")
            assertTrue("cancel" in terms.lowercase(), "cancellation missing: $terms")
            if (trial != null) assertTrue("${trial.phrase} free" in terms.lowercase(), "trial length missing: $terms")

            val everything = buildList {
                add(terms)
                add(Paywall.callToAction(offer))
                Paywall.card(offer, ProOffer(ProPlan.Monthly, 5.99, "USD")).let { addAll(listOfNotNull(it.title, it.price, it.perMonth, it.badge, it.trial)) }
                Paywall.timeline(offer, store).forEach { add(it.title); add(it.detail) }
            }
            for (text in everything) assertFalse(dashes.any { it in text }, "a dash in: $text")
        }
        for (reason in PaywallReason.entries) assertFalse(dashes.any { it in Paywall.headline(reason, null) })
        for (store in Store.entries) {
            val more = listOf(Paywall.onHoldDetail(store), Paywall.onHoldAction(store), EntitlementPolicy.reminderNotice(store))
            for (text in more) assertFalse(dashes.any { it in text }, "a dash in: $text")
        }
        assertFalse(dashes.any { it in Paywall.SUBSCRIPTION_REQUIRED + Paywall.ON_HOLD_HEADLINE })
        for (benefit in Paywall.benefits) assertFalse(dashes.any { it in benefit.title + benefit.detail })
    }

    /** Today, the reminder two days before the end, then the charge: the promise the app keeps. */
    @Test
    fun theTimelineExplainsTheTrialAndIsEmptyWithoutOne() {
        val steps = Paywall.timeline(annual, Store.GooglePlay)
        assertEquals(listOf("Today", "Day 12", "Day 14"), steps.map { it.title })
        assertTrue("No payment today" in steps[0].detail)
        assertEquals("A reminder that your trial ends in 2 days.", steps[1].detail)
        assertTrue(steps[2].detail.startsWith("${usd(29.99)} a year starts, renewing every year."))
        assertTrue("before the trial ends" in steps[2].detail)
        val month = Paywall.timeline(annual.copy(freeTrial = StorePeriod.parse("P1M")), Store.GooglePlay)
        assertEquals(listOf("Today", "Before it ends", "After 1 month"), month.map { it.title })
        // A trial too short for a reminder two days out gets no reminder step, rather than "Day 0".
        assertEquals(listOf("Today", "Day 2"), Paywall.timeline(annual.copy(freeTrial = StorePeriod.parse("P2D")), Store.GooglePlay).map { it.title })
        assertTrue(Paywall.timeline(annualNoTrial, Store.GooglePlay).isEmpty())
        assertTrue(Paywall.timeline(monthly, Store.AppStore).isEmpty())
    }

    @Test
    fun theHeadlineLeadsWithWhatTheUserHasAlreadySaved() {
        assertEquals("Your swaps have saved you $4.50", Paywall.headline(PaywallReason.Returning, "$4.50"))
        assertEquals("Your week is ready", Paywall.headline(PaywallReason.AfterOnboarding, null))
        assertEquals("Welcome back", Paywall.headline(PaywallReason.Returning, null))
    }

    /** A subscription on hold is not a lapsed one: no welcome back, no savings pitch, just what happened. */
    @Test
    fun aSubscriptionOnHoldIsToldWhatHappenedAndWhereToFixIt() {
        assertEquals("Your subscription is on hold", Paywall.headline(PaywallReason.Returning, "$4.50", isOnHold = true))
        assertEquals(
            "Google Play couldn't take your last payment, or the subscription is paused. " +
                "Sort it out in Google Play and your plan opens again, just as you left it.",
            Paywall.onHoldDetail(Store.GooglePlay),
        )
        assertTrue(Paywall.onHoldDetail(Store.AppStore).startsWith("The App Store couldn't"))
        assertEquals("Fix it in Google Play", Paywall.onHoldAction(Store.GooglePlay))
    }

    // What the app remembers about Pro

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private val subscriber = Entitlement(isPro = true, plan = ProPlan.Annual, verifiedAtMillis = now - 2 * day)

    /** The store's answer always wins, including a cancellation the cache has not heard about. */
    @Test
    fun theStoresAnswerAlwaysWins() {
        assertEquals(Entitlement.FREE, EntitlementPolicy.resolve(Entitlement.FREE, subscriber, now))
        val live = Entitlement(isPro = true, plan = ProPlan.Monthly, verifiedAtMillis = now)
        assertEquals(live, EntitlementPolicy.resolve(live, Entitlement.FREE, now))
    }

    @Test
    fun aSubscriberKeepsProOfflineForAWeekAndNoLonger() {
        assertTrue(EntitlementPolicy.resolve(null, subscriber, now).isPro)
        assertTrue(EntitlementPolicy.resolve(null, subscriber.copy(verifiedAtMillis = now - 7 * day), now).isPro)
        assertFalse(EntitlementPolicy.resolve(null, subscriber.copy(verifiedAtMillis = now - 7 * day - 1), now).isPro)
        assertFalse(EntitlementPolicy.resolve(null, Entitlement.FREE, now).isPro)
    }

    /** Setting the clock back must not stretch the offline week indefinitely. */
    @Test
    fun aConfirmationFromTheFutureIsNotBelieved() {
        assertTrue(EntitlementPolicy.resolve(null, subscriber.copy(verifiedAtMillis = now + 5 * 60_000), now).isPro)
        assertFalse(EntitlementPolicy.resolve(null, subscriber.copy(verifiedAtMillis = now + day), now).isPro)
    }

    @Test
    fun theTrialReminderAppearsOnlyInTheLastTwoDays() {
        val trial = { endsIn: Long -> subscriber.copy(trialEndsAtMillis = now + endsIn) }
        assertEquals(3, EntitlementPolicy.trialDaysLeft(trial(3 * day), now))
        assertFalse(EntitlementPolicy.showsTrialReminder(trial(3 * day), now))
        assertEquals(2, EntitlementPolicy.trialDaysLeft(trial(36 * 3_600_000L), now))
        assertTrue(EntitlementPolicy.showsTrialReminder(trial(36 * 3_600_000L), now))
        assertEquals(1, EntitlementPolicy.trialDaysLeft(trial(3_600_000L), now))
        // Over, cancelled, or never recorded: no reminder.
        assertNull(EntitlementPolicy.trialDaysLeft(trial(-1), now))
        assertNull(EntitlementPolicy.trialDaysLeft(trial(day).copy(isPro = false), now))
        assertNull(EntitlementPolicy.trialDaysLeft(subscriber, now))
    }

    /** The notification the paywall promises: two days before the end, only for a trial that will charge. */
    @Test
    fun theReminderNotificationIsDueTwoDaysBeforeAChargeAndNeverAfterACancellation() {
        val trial = Entitlement(isPro = true, plan = ProPlan.Annual, verifiedAtMillis = now, trialEndsAtMillis = now + 14 * day)
        assertEquals(now + 12 * day, EntitlementPolicy.reminderAt(trial, now))
        assertEquals(null, EntitlementPolicy.reminderAt(trial.copy(willRenew = false), now), "a cancelled trial is not warned of a charge")
        assertEquals(null, EntitlementPolicy.reminderAt(trial, now + 13 * day), "the reminder time has passed")
        assertEquals(null, EntitlementPolicy.reminderAt(subscriber, now), "a paying subscriber is in no trial")
        assertEquals(null, EntitlementPolicy.reminderAt(Entitlement.FREE, now))
    }

    @Test
    fun theReminderSaysWhenInPlainWords() {
        assertEquals("Your free trial ends today", EntitlementPolicy.reminderTitle(0))
        assertEquals("Your free trial ends tomorrow", EntitlementPolicy.reminderTitle(1))
        assertEquals("Your free trial ends in 2 days", EntitlementPolicy.reminderTitle(2))
    }

    /** Someone who already cancelled is told they won't be charged, not warned that they will. */
    @Test
    fun theReminderNeverThreatensAChargeAfterACancellation() {
        val cancelled = subscriber.copy(willRenew = false)
        val text = EntitlementPolicy.reminderDetail(cancelled, annual, Store.GooglePlay)
        assertTrue("won't be charged" in text)
        assertFalse(usd(29.99) in text, "a cancelled trial must not quote the price as if it will be charged")
        assertEquals(
            "Then ${usd(29.99)} a year, renewing every year. Cancel in Google Play before the trial ends and you won't be charged.",
            EntitlementPolicy.reminderDetail(subscriber, annual, Store.GooglePlay),
        )
    }

    /**
     * The notification fires from an alarm, without asking the store, so it
     * may reach someone who cancelled in Google Play and has not opened the
     * app since. Its words are true for them as well.
     */
    @Test
    fun theReminderNotificationIsTrueWhetherOrNotTheTrialWasCancelled() {
        assertEquals(
            "If you haven't cancelled, MacroDime Pro then renews at the price you agreed to. " +
                "Cancel in Google Play before the trial ends and you won't be charged.",
            EntitlementPolicy.reminderNotice(Store.GooglePlay),
        )
    }

    // Purchases, as a store reports them

    private fun purchase(
        token: String = "t1",
        product: String = PlayOffers.PRODUCT_ID,
        at: Long = now - day,
        purchased: Boolean = true,
        acknowledged: Boolean = true,
        renewing: Boolean = true,
        suspended: Boolean = false,
    ) = StorePurchase(listOf(product), token, at, purchased, !purchased, acknowledged, renewing, suspended)

    @Test
    fun aPaidPurchaseGivesProAndNothingElseDoes() {
        assertTrue(EntitlementPolicy.fromPurchases(listOf(purchase()), PlayOffers.PRODUCT_ID, null, now).isPro)
        // Pending payment, Play's account hold, or some other product: no Pro.
        for (nothing in listOf(purchase(purchased = false), purchase(suspended = true), purchase(product = "something-else"))) {
            assertFalse(EntitlementPolicy.fromPurchases(listOf(nothing), PlayOffers.PRODUCT_ID, null, now).isPro, "$nothing")
        }
        val free = EntitlementPolicy.fromPurchases(emptyList(), PlayOffers.PRODUCT_ID, null, now)
        assertFalse(free.isPro)
        assertEquals(now, free.verifiedAtMillis, "a confirmed free answer is still a confirmation")
    }

    /** The plan and the trial's end come from this phone's record, and only for that same purchase. */
    @Test
    fun theRecordAppliesOnlyToThePurchaseItWasWrittenFor() {
        val record = PurchaseRecord("t1", ProPlan.Annual, now + 3 * day)
        val mine = EntitlementPolicy.fromPurchases(listOf(purchase(token = "t1")), PlayOffers.PRODUCT_ID, record, now)
        assertEquals(ProPlan.Annual, mine.plan)
        assertEquals(now + 3 * day, mine.trialEndsAtMillis)

        // Restored from another phone: Pro, but no plan or trial date to claim.
        val other = EntitlementPolicy.fromPurchases(listOf(purchase(token = "t2")), PlayOffers.PRODUCT_ID, record, now)
        assertTrue(other.isPro)
        assertNull(other.plan)
        assertNull(other.trialEndsAtMillis)
    }

    /**
     * Play's account hold (a failed payment) and a pause both arrive as a
     * suspended purchase, and only when the app asks for them. No Pro, but the
     * person is on hold, to be sent to Google Play rather than sold again.
     */
    @Test
    fun aSuspendedSubscriptionIsOnHoldNotLapsed() {
        val held = listOf(purchase(suspended = true))
        assertFalse(EntitlementPolicy.fromPurchases(held, PlayOffers.PRODUCT_ID, null, now).isPro)
        assertTrue(EntitlementPolicy.isOnHold(held, PlayOffers.PRODUCT_ID))
        // An active subscription beside it wins: nothing is on hold for this person.
        assertFalse(EntitlementPolicy.isOnHold(held + purchase(token = "t2"), PlayOffers.PRODUCT_ID))
        // Nothing at all, a lapse, a pending payment, or another product's suspension: not on hold.
        assertFalse(EntitlementPolicy.isOnHold(emptyList(), PlayOffers.PRODUCT_ID))
        assertFalse(EntitlementPolicy.isOnHold(listOf(purchase(purchased = false)), PlayOffers.PRODUCT_ID))
        assertFalse(EntitlementPolicy.isOnHold(listOf(purchase(product = "something-else", suspended = true)), PlayOffers.PRODUCT_ID))
    }

    @Test
    fun aCancellationInTheStoreIsCarriedThrough() {
        assertFalse(EntitlementPolicy.fromPurchases(listOf(purchase(renewing = false)), PlayOffers.PRODUCT_ID, null, now).willRenew)
    }

    @Test
    fun everyPaidPurchaseIsAcknowledgedAndNothingElse() {
        assertTrue(EntitlementPolicy.needsAcknowledging(purchase(acknowledged = false)))
        assertFalse(EntitlementPolicy.needsAcknowledging(purchase(acknowledged = true)))
        assertFalse(EntitlementPolicy.needsAcknowledging(purchase(purchased = false, acknowledged = false)))
    }

    // Choosing what to sell from Play's offers

    private fun phase(micros: Long, period: String, mode: Int = PlayOffers.INFINITE_RECURRING, cycles: Int = 0, currency: String = "USD") =
        PlayOffers.Phase(micros, currency, period, cycles, mode)

    private val freeTwoWeeks = phase(0, "P2W", PlayOffers.FINITE_RECURRING, 1)
    private val yearly = phase(29_990_000, "P1Y")
    private val monthlyPhase = phase(5_990_000, "P1M")

    @Test
    fun theYearlyTrialAndTheMonthlyBasePriceAreWhatGetsSold() {
        val selected = PlayOffers.select(
            listOf(
                PlayOffers.Input("annual", null, "annual-base", listOf(yearly)),
                PlayOffers.Input("annual", "trial-14", "annual-trial", listOf(freeTwoWeeks, yearly)),
                PlayOffers.Input("monthly", null, "monthly-base", listOf(monthlyPhase)),
            ),
        )
        assertEquals("annual-trial", selected[ProPlan.Annual]?.offerToken)
        assertEquals(ProOffer(ProPlan.Annual, 29.99, "USD", StorePeriod(2, StorePeriod.Span.Week)), selected[ProPlan.Annual]?.offer)
        assertEquals("monthly-base", selected[ProPlan.Monthly]?.offerToken)
        assertEquals(ProOffer(ProPlan.Monthly, 5.99, "USD"), selected[ProPlan.Monthly]?.offer)
    }

    /** Someone who has had the trial gets no trial offer from Play, and the paywall follows. */
    @Test
    fun withoutAnEligibleTrialTheYearlyPlanSellsAtItsBasePrice() {
        val selected = PlayOffers.select(listOf(PlayOffers.Input("annual", null, "annual-base", listOf(yearly))))
        assertEquals("annual-base", selected[ProPlan.Annual]?.offerToken)
        assertNull(selected[ProPlan.Annual]?.offer?.freeTrial)
        assertNull(selected[ProPlan.Monthly])
    }

    /** Anything the paywall could not describe honestly is skipped, not sold. */
    @Test
    fun offersThePaywallCannotDescribeAreSkipped() {
        val discountedFirstYear = PlayOffers.Input("annual", "intro", "intro", listOf(phase(9_990_000, "P1Y", PlayOffers.FINITE_RECURRING, 1), yearly))
        val twoFreePhases = PlayOffers.Input("annual", "odd", "odd", listOf(freeTwoWeeks, freeTwoWeeks, yearly))
        val yearlyThatRenewsMonthly = PlayOffers.Input("annual", null, "wrong-period", listOf(phase(29_990_000, "P1M")))
        val freeForever = PlayOffers.Input("annual", null, "free", listOf(phase(0, "P1Y")))
        assertTrue(PlayOffers.select(listOf(discountedFirstYear, twoFreePhases, yearlyThatRenewsMonthly, freeForever)).isEmpty())
    }

    @Test
    fun aTrialOnTheMonthlyPlanIsNotSold() {
        val monthlyTrial = PlayOffers.Input("monthly", "m-trial", "monthly-trial", listOf(freeTwoWeeks, monthlyPhase))
        val selected = PlayOffers.select(listOf(monthlyTrial, PlayOffers.Input("monthly", null, "monthly-base", listOf(monthlyPhase))))
        assertEquals("monthly-base", selected[ProPlan.Monthly]?.offerToken)
    }

    @Test
    fun theLongerOfTwoEligibleTrialsWins() {
        val week = PlayOffers.Input("annual", "w", "one-week", listOf(phase(0, "P1W", PlayOffers.FINITE_RECURRING, 1), yearly))
        val fortnight = PlayOffers.Input("annual", "f", "two-weeks", listOf(phase(0, "P1W", PlayOffers.FINITE_RECURRING, 2), yearly))
        val selected = PlayOffers.select(listOf(week, fortnight))[ProPlan.Annual]
        assertEquals("two-weeks", selected?.offerToken)
        assertEquals("14-day", selected?.offer?.freeTrial?.adjective)
    }

    // The launch configuration, end to end

    /**
     * Play Console as docs/play-store-listing.md sets it up: product `pro`, base
     * plan `annual` at $29.99 a year, and the `free-trial-14-days` offer on it.
     * Everything the paywall says is generated from exactly what Play returns.
     */
    @Test
    fun theLaunchConfigurationIsSoldAsFourteenDaysFreeThenTheYearlyPrice() {
        for (trialPhase in listOf(freeTwoWeeks, phase(0, "P14D", PlayOffers.FINITE_RECURRING, 1))) {
            val selected = PlayOffers.select(
                listOf(
                    PlayOffers.Input("annual", null, "annual-base", listOf(yearly)),
                    PlayOffers.Input("annual", "free-trial-14-days", "annual-trial", listOf(trialPhase, yearly)),
                ),
            )
            assertNull(selected[ProPlan.Monthly], "no monthly base plan is active at launch")
            val sold = assertNotNull(selected[ProPlan.Annual])
            assertEquals("annual-trial", sold.offerToken)

            val offer = sold.offer
            val card = Paywall.card(offer, selected[ProPlan.Monthly]?.offer)
            assertEquals("${usd(29.99)} a year", card.price)
            assertEquals("${usd(2.50)} a month", card.perMonth)
            assertNull(card.badge, "no saving can be claimed without a monthly plan to compare")
            assertEquals("14 days free", card.trial)
            assertEquals("Start my 14-day free trial", Paywall.callToAction(offer))
            assertEquals(
                "A subscription is required to use MacroDime. " +
                    "14 days free, then ${usd(29.99)} a year. MacroDime Pro renews every year until you cancel. " +
                    "Cancel in Google Play before the trial ends and you won't be charged.",
                Paywall.disclosure(offer, Store.GooglePlay),
            )
            assertEquals(listOf("Today", "Day 12", "Day 14"), Paywall.timeline(offer, Store.GooglePlay).map { it.title })
        }
    }

    /** Play prices each country itself; the paywall quotes that price in that currency, untouched. */
    @Test
    fun aLocalPriceIsQuotedAsPlayChargesIt() {
        val rand = PlayOffers.select(
            listOf(PlayOffers.Input("annual", "free-trial-14-days", "zar-trial", listOf(phase(0, "P2W", PlayOffers.FINITE_RECURRING, 1, "ZAR"), phase(549_990_000, "P1Y", currency = "ZAR")))),
        )[ProPlan.Annual]
        val offer = assertNotNull(rand).offer
        assertEquals(549.99, offer.price)
        assertEquals("ZAR", offer.currencyCode)
        assertEquals("${DisplayFormat.currency(549.99, "ZAR")} a year", Paywall.priceLine(offer))
    }

    /**
     * License testers get Google's test clock: a free trial of about 3 minutes
     * and yearly renewals every 30 minutes, shown on Google Play's own purchase
     * sheet. The app never states a trial in minutes, and a minutes period is
     * never read as months (PT3M is three minutes; P3M would be three months).
     * Should Play ever describe the trial that way, the trial offer is skipped
     * and the yearly plan is sold at its price with no trial promised.
     */
    @Test
    fun aTestAccountsThreeMinuteTrialIsNeverReadAsThreeMonths() {
        assertNull(StorePeriod.parse("PT3M"))
        val selected = PlayOffers.select(
            listOf(
                PlayOffers.Input("annual", null, "annual-base", listOf(yearly)),
                PlayOffers.Input("annual", "free-trial-14-days", "annual-trial", listOf(phase(0, "PT3M", PlayOffers.FINITE_RECURRING, 1), yearly)),
            ),
        )[ProPlan.Annual]
        assertEquals("annual-base", selected?.offerToken)
        assertNull(selected?.offer?.freeTrial)
        assertEquals("Subscribe for ${usd(29.99)} a year", selected?.offer?.let(Paywall::callToAction))
    }
}
