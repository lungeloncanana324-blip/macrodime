/*
 * DataSources.kt
 * MacroDime
 *
 * Where the app's published figures come from, each with a link to the
 * original. Google Play's Misleading Claims policy requires an app that shows
 * government information, affiliated or not, to link to the source and to say
 * that it does not represent a government. Play rejected the first review on
 * 6 Oct 2026 for exactly that: the app named BLS and USDA but linked neither.
 *
 * The Sources screen reads from here, and DataSourcesTest holds the store
 * description to the same links and the same statement, so the app and the
 * listing cannot drift apart. Android only for now: the iOS app names the same
 * agencies in Settings but does not link them yet (SHIPPING-GAPS section 0).
 */
package com.lungelo.macrodime.engine

/** A published dataset or reference, named the way its publisher names it. */
data class DataSource(
    val title: String,
    val publisher: String,
    /** The original, opened in the browser. */
    val url: String,
    /** A US government agency, as opposed to an international body. */
    val isGovernment: Boolean,
) {
    /** The link as printed on screen: no scheme and no trailing slash, so the domain is read first. */
    val shownUrl: String get() = url.removePrefix("https://").removeSuffix("/")
}

object DataSources {

    val AVERAGE_PRICES = DataSource(
        title = "Average Price Data",
        publisher = "US Bureau of Labor Statistics",
        url = "https://www.bls.gov/cpi/factsheets/average-prices.htm",
        isGovernment = true,
    )

    val FRUIT_AND_VEGETABLE_PRICES = DataSource(
        title = "Fruit and Vegetable Prices",
        publisher = "USDA Economic Research Service",
        url = "https://www.ers.usda.gov/data-products/fruit-and-vegetable-prices",
        isGovernment = true,
    )

    val CONSUMER_PRICE_INDEX = DataSource(
        title = "Consumer Price Index",
        publisher = "US Bureau of Labor Statistics",
        url = "https://www.bls.gov/cpi/",
        isGovernment = true,
    )

    val FOOD_DATA_CENTRAL = DataSource(
        title = "FoodData Central",
        publisher = "US Department of Agriculture",
        url = "https://fdc.nal.usda.gov/",
        isGovernment = true,
    )

    val BMI = DataSource(
        title = "Body mass index (BMI)",
        publisher = "World Health Organization",
        url = "https://www.who.int/data/gho/data/themes/topics/topic-details/GHO/body-mass-index",
        isGovernment = false,
    )

    /** What prices are built from, in the order the Sources screen lists them. */
    val prices: List<DataSource> = listOf(AVERAGE_PRICES, FRUIT_AND_VEGETABLE_PRICES, CONSUMER_PRICE_INDEX)

    /** What nutrition and body figures are built from. */
    val nutrition: List<DataSource> = listOf(FOOD_DATA_CENTRAL, BMI)

    val all: List<DataSource> = prices + nutrition

    /**
     * First on the Sources screen, and the same words open the store
     * description's sources section. It covers the WHO as well, which is not a
     * government but is not MacroDime either.
     */
    const val INDEPENDENCE = "MacroDime is an independent app. It does not represent any government or " +
        "government agency, and it is not affiliated with or endorsed by any organisation listed here. It uses " +
        "figures these organisations publish for anyone to use, and each link opens the original."

    /** The same statement in one line, for places that point to the Sources screen. */
    const val INDEPENDENCE_SHORT = "MacroDime is an independent app and does not represent any government."

    /** What MacroDime takes from [source], in a line under its name. */
    fun useOf(source: DataSource): String = when (source) {
        AVERAGE_PRICES ->
            "US city average prices for ${PriceTable.foodsPricedFrom(source).size} foods, latest ${PriceTable.latestPeriod}."
        FRUIT_AND_VEGETABLE_PRICES ->
            "Average prices for ${PriceTable.foodsPricedFrom(source).size} foods in ${PriceTable.ersYear}, from store scanner data."
        CONSUMER_PRICE_INDEX ->
            "Carries the fruit and vegetable prices from ${PriceTable.ersYear} to ${PriceTable.latestPeriod}."
        FOOD_DATA_CENTRAL ->
            "Calories, protein, carbohydrate and fat for whole foods. Packaged foods use typical label values."
        BMI ->
            "The adult BMI bands shown next to your BMI."
        else -> source.publisher
    }
}
