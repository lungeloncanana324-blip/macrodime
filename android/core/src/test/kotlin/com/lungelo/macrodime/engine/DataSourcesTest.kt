/*
 * DataSourcesTest.kt
 *
 * Google Play rejected MacroDime on 6 Oct 2026 under the Misleading Claims
 * policy: the app showed government prices without a link to where they came
 * from, and the description did not say the app is not a government's. These
 * hold both fixes in place: every government figure the app uses links to a
 * .gov original, and the store description carries the same links and the
 * same statement as the Sources screen.
 *
 * The second review, on 7 Oct 2026, failed on a "broken or inaccessible source
 * link": www.bls.gov turns automated visitors away with a 403, and Play's check
 * is automated. The blocked hosts are kept out here; whether the rest answer is
 * a network question, so scripts/check_source_links.py asks it, not this test.
 */
package com.lungelo.macrodime.engine

import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSourcesTest {

    // The links

    @Test
    fun everyLinkIsHttpsAndEveryGovernmentLinkIsOnAGovSite() {
        for (source in DataSources.all) {
            val uri = URI(source.url)
            assertEquals("https", uri.scheme, source.title)
            if (source.isGovernment) assertTrue(uri.host.endsWith(".gov"), "${source.title} is not on a .gov site")
        }
    }

    /**
     * www.bls.gov and download.bls.gov answer curl and headless browsers with
     * "Access Denied"; data.bls.gov serves the same data to anyone.
     */
    @Test
    fun noLinkIsOnABlsHostThatTurnsAutomatedVisitorsAway() {
        val blocked = setOf("www.bls.gov", "bls.gov", "download.bls.gov")
        for (source in DataSources.all) assertTrue(URI(source.url).host !in blocked, "${source.title} links ${source.url}")
        for (host in blocked) assertTrue("https://$host/" !in description, "the store description links $host")
    }

    @Test
    fun eachSourceIsListedOnce() {
        assertEquals(DataSources.all.size, DataSources.all.map { it.url }.toSet().size)
    }

    /** What is printed is the address itself, so a reader sees the .gov before tapping. */
    @Test
    fun theShownLinkIsTheAddressWithoutItsScheme() {
        assertEquals("data.bls.gov/timeseries/CUUR0000SAF113", DataSources.CONSUMER_PRICE_INDEX.shownUrl)
        for (source in DataSources.all) assertTrue(source.url.endsWith(source.shownUrl) || source.url.endsWith(source.shownUrl + "/"))
    }

    // Every price names its source

    @Test
    fun everySourcedPriceComesFromAListedDataset() {
        for (entry in PriceTable.entries) assertTrue(entry.source.dataset in DataSources.prices, entry.foodId)
    }

    @Test
    fun theCountsOnTheSourcesScreenAddUpToTheSummary() {
        val bls = PriceTable.foodsPricedFrom(DataSources.AVERAGE_PRICES)
        val ers = PriceTable.foodsPricedFrom(DataSources.FRUIT_AND_VEGETABLE_PRICES)
        assertTrue(bls.isNotEmpty() && ers.isNotEmpty())
        assertEquals(PriceTable.summary.sourcedCount, bls.size + ers.size)
        assertTrue(bls.none { it in ers })
        assertTrue("${bls.size} foods" in DataSources.useOf(DataSources.AVERAGE_PRICES))
        assertTrue("${ers.size} foods" in DataSources.useOf(DataSources.FRUIT_AND_VEGETABLE_PRICES))
    }

    @Test
    fun everySourceSaysWhatTheAppTakesFromIt() {
        for (source in DataSources.all) {
            val use = DataSources.useOf(source)
            assertTrue(use.isNotBlank() && use != source.publisher, "${source.title} has no use line")
        }
        assertTrue(PriceTable.latestPeriod in DataSources.useOf(DataSources.CONSUMER_PRICE_INDEX))
    }

    @Test
    fun theStatementSaysTheAppIsNotAGovernments() {
        assertTrue("does not represent any government" in DataSources.INDEPENDENCE)
        assertTrue("not affiliated with or endorsed by" in DataSources.INDEPENDENCE)
        assertTrue("does not represent any government" in DataSources.INDEPENDENCE_SHORT)
    }

    // The store description

    private val description: String by lazy {
        val path = System.getProperty("macrodime.listing") ?: error("run through Gradle, which passes the listing's path")
        // A Windows checkout may carry CRLF; the description is compared as Play shows it.
        val listing = File(path).readText().replace("\r\n", "\n")
        val section = listing.substringAfter("### Full description (4,000)", "")
        assertTrue(section.isNotEmpty(), "the listing has no full description section")
        section.substringAfter("```\n").substringBefore("```").trimEnd()
    }

    @Test
    fun theDescriptionLinksEveryGovernmentSource() {
        for (source in DataSources.all.filter { it.isGovernment }) {
            assertTrue(source.url in description, "the store description does not link ${source.title}")
        }
    }

    @Test
    fun theDescriptionCarriesTheSameStatementAsTheApp() {
        assertTrue(DataSources.INDEPENDENCE in description)
        assertTrue("Sources screen" in description)
    }

    /** Play's limit, with room for each line break to count as two if the console sends CRLF. */
    @Test
    fun theDescriptionFitsPlaysLimit() {
        assertTrue(description.length + description.count { it == '\n' } <= 4000, "${description.length} characters")
    }
}
