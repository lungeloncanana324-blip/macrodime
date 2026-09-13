//
//  Theme.swift
//  MacroDime
//
//  The brand palette. One source of truth for colour, so a hue is never
//  hard-coded into a view.
//
//  Direction: warm provisions. Gold carries the budget — the premium, the
//  money, the appetite — and takes the accent role that would otherwise be
//  default iOS blue. The macros sit in food colours (berry, clay, olive)
//  rather than a system rainbow. Green and red stay reserved for one job only:
//  whether you are inside your budget.
//
//  Every colour is declared as a dynamic `UIColor`, so light and dark are
//  defined together and resolve automatically — including inside widgets and
//  for `UIKit` interop, which a plain `Color(light:dark:)` helper cannot do.
//

import SwiftUI
import UIKit

enum Brand {

    /// Builds a colour that resolves per appearance.
    private static func dynamic(light: UInt, dark: UInt) -> Color {
        Color(uiColor: UIColor { traits in
            UIColor(hex: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }

    // MARK: Identity

    /// Primary accent: buttons, tab bar, links, the calorie ring.
    static let gold = dynamic(light: 0xA87B1E, dark: 0xE3B45C)
    /// Deep counterpart for text on gold surfaces.
    static let goldInk = dynamic(light: 0x6B4C0B, dark: 0xF3DDAE)

    // MARK: Macros

    static let protein = dynamic(light: 0xB03060, dark: 0xE8628F)
    static let carbs   = dynamic(light: 0xC05A2B, dark: 0xF0854A)
    static let fat     = dynamic(light: 0x77803C, dark: 0xB7C06A)

    // MARK: Budget status
    //
    // Reserved. Green and red mean "inside budget" and "over budget" and are
    // never used decoratively — the moment they appear elsewhere, the cost
    // tracker stops reading at a glance.

    static let underBudget = dynamic(light: 0x2E7D52, dark: 0x4FBF85)
    static let overBudget  = dynamic(light: 0xC0392B, dark: 0xFF6B5A)

    // MARK: Supporting

    /// Body-measurement metrics — deliberately outside the macro palette, since
    /// waist and photos are a different kind of evidence from macros.
    static let measurement = dynamic(light: 0x3C7A85, dark: 0x64BECB)
    /// "Already have this" on the grocery list.
    static let pantry = dynamic(light: 0x5B4B8A, dark: 0x9B8BD0)
}

// MARK: - Hex

private extension UIColor {
    /// 24-bit RGB, e.g. `0xA87B1E`.
    convenience init(hex: UInt) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

// MARK: - Accent

extension View {
    /// Applies the brand accent. Set once at the root so every stock control
    /// (buttons, pickers, toggles) inherits it instead of iOS blue.
    func brandAccent() -> some View {
        tint(Brand.gold)
    }
}
