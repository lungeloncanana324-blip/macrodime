/*
 * HealthDisclaimer.kt
 * MacroDime
 *
 * The statement that calorie targets are estimates, not medical advice.
 * Acknowledged during onboarding and readable afterwards in Settings. The text
 * is the iOS text, word for word, with the platform's own name for the
 * settings screen, except the price paragraph: since Play's Misleading Claims
 * rejection of 6 Oct 2026 it says the app is not a government's and points to
 * the Sources screen, which iOS does not have yet.
 */
package com.lungelo.macrodime.ui.settings

object HealthDisclaimer {

    /** One line, for the Settings footer. */
    const val SHORT = "Estimates, not medical advice."

    /** The full statement. Written plainly on purpose: a disclaimer nobody can read protects nobody. */
    const val BODY = "MacroDime estimates your energy needs with the Mifflin-St Jeor equation and standard " +
        "activity multipliers. These are population averages. Your real metabolic rate can differ from the " +
        "estimate by 10% or more, and no formula can account for your medical history, medication, or training." +
        "\n\n" +
        "Treat the targets as a starting point to adjust from, not a prescription to obey." +
        "\n\n" +
        "Talk to a doctor or registered dietitian before starting a calorie deficit if you are pregnant or " +
        "breastfeeding, managing diabetes, thyroid or heart conditions, taking medication affecting appetite or " +
        "metabolism, or have any history of disordered eating." +
        "\n\n" +
        "MacroDime is for adults. It is not a medical device, and it does not diagnose, treat, or prevent any " +
        "condition." +
        "\n\n" +
        "Ingredient prices are US averages: figures published by US government agencies where they exist, " +
        "estimates for the rest. They are not a quote from any store. MacroDime does not represent any " +
        "government, and Sources links to each original. If you set your own currency in Settings, amounts " +
        "are converted at the rate you enter."

    /** Shown beside the BMI figure, since BMI is the number most often misread. */
    const val BMI_CAVEAT = "BMI is a population statistic. It cannot tell muscle from fat, so a muscular person " +
        "often reads as \"overweight\". Track your waist and photos alongside it."

    /** What deletion covers, for the confirmation dialog. */
    const val DELETE_SUMMARY = "This erases your profile and targets, every planned meal, all grocery lists, and " +
        "all measurements and progress photos." +
        "\n\n" +
        "Everything is stored on this phone only, so this cannot be undone and there is no backup to restore from."

    /**
     * Eating disorder support. NEDIC is Canadian; the iOS gap report already
     * names SADAG as the better fit for a South African launch, which is a
     * product decision, so this keeps the iOS link until that is made.
     */
    const val SUPPORT_NAME = "Eating disorder support (NEDIC)"
    const val SUPPORT_URL = "https://www.nedic.ca/"

    const val PRIVACY_POLICY_URL = "https://lungeloncanana324-blip.github.io/macrodime/privacy-policy.html"
}
