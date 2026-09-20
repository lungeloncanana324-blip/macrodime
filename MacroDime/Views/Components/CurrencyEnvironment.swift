//
//  CurrencyEnvironment.swift
//  MacroDime
//
//  How a view learns which currency to show, without being told by every caller.
//
//  Injected once at the root from the profile and read by `@Environment(\.currency)`.
//  The default is the catalogue's own currency, so a view rendered outside the
//  app shell (a preview, a sheet that forgot the injection) still shows a true
//  number rather than a wrong one.
//

import SwiftUI

private struct CurrencySettingsKey: EnvironmentKey {
    static let defaultValue = CurrencySettings.usd
}

extension EnvironmentValues {
    /// The conversion and currency code money should be rendered with.
    ///
    /// Read it as `@Environment(\.currency) private var prices` and format with
    /// `prices.format(usdAmount)`. Never format a catalogue amount with a bare
    /// locale: that is the bug this whole mechanism exists to prevent.
    var currency: CurrencySettings {
        get { self[CurrencySettingsKey.self] }
        set { self[CurrencySettingsKey.self] = newValue }
    }
}

extension View {
    /// Applies a profile's currency choice to a subtree.
    func currencySettings(_ settings: CurrencySettings) -> some View {
        environment(\.currency, settings)
    }
}
