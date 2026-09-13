//
//  UnitConversion.swift
//  MacroDime
//
//  Metric is the storage format everywhere: the science engine, SwiftData and
//  the food catalogue all speak kg/cm/g. Imperial exists only at the UI edge,
//  and this is the only place the two meet.
//

import Foundation

enum UnitConversion {

    // MARK: Mass

    static let poundsPerKilogram: Double = 2.20462262185

    static func kilograms(fromPounds pounds: Double) -> Double {
        pounds / poundsPerKilogram
    }

    static func pounds(fromKilograms kilograms: Double) -> Double {
        kilograms * poundsPerKilogram
    }

    // MARK: Length

    static let centimetresPerInch: Double = 2.54
    static let inchesPerFoot: Double = 12

    static func centimetres(fromInches inches: Double) -> Double {
        inches * centimetresPerInch
    }

    static func inches(fromCentimetres centimetres: Double) -> Double {
        centimetres / centimetresPerInch
    }

    /// Splits a height in centimetres into whole feet plus remaining inches.
    /// Inches are rounded, and a result of 12 is carried into the feet value so
    /// the picker can never show `5' 12"`.
    static func feetAndInches(fromCentimetres centimetres: Double) -> (feet: Int, inches: Int) {
        let totalInches = inches(fromCentimetres: centimetres)
        var feet = Int(totalInches / inchesPerFoot)
        var remainder = Int((totalInches - Double(feet) * inchesPerFoot).rounded())
        if remainder >= Int(inchesPerFoot) {
            feet += 1
            remainder = 0
        }
        return (feet, remainder)
    }

    static func centimetres(fromFeet feet: Int, inches inchesValue: Int) -> Double {
        centimetres(fromInches: Double(feet) * inchesPerFoot + Double(inchesValue))
    }
}

// MARK: - Formatting

/// Display formatting used across the views. Centralised so a unit toggle or a
/// locale change never leaves one screen disagreeing with another.
enum DisplayFormat {

    /// Currency in the device locale. The catalogue's prices are authored in
    /// USD, so this formats the *number* natively without pretending to convert
    /// the amount — see the note in `FoodCatalog`.
    static func currency(_ amount: Double) -> String {
        amount.formatted(.currency(code: Locale.current.currency?.identifier ?? "USD"))
    }

    /// Whole kilocalories, e.g. `"1,842 kcal"`.
    static func calories(_ value: Double) -> String {
        "\(Int(value.rounded())) kcal"
    }

    /// Whole grams, e.g. `"148 g"`.
    static func grams(_ value: Double) -> String {
        "\(Int(value.rounded())) g"
    }

    /// Weight in the user's chosen system, one decimal place.
    static func weight(kilograms: Double, system: MeasurementSystem) -> String {
        switch system {
        case .metric:
            "\(kilograms.formatted(.number.precision(.fractionLength(1)))) kg"
        case .imperial:
            "\(UnitConversion.pounds(fromKilograms: kilograms).formatted(.number.precision(.fractionLength(1)))) lb"
        }
    }

    /// Height in the user's chosen system.
    static func height(centimetres: Double, system: MeasurementSystem) -> String {
        switch system {
        case .metric:
            return "\(Int(centimetres.rounded())) cm"
        case .imperial:
            let parts = UnitConversion.feetAndInches(fromCentimetres: centimetres)
            return "\(parts.feet)' \(parts.inches)\""
        }
    }

    /// A percentage from a 0-1 fraction, e.g. `"87%"`.
    static func percent(_ fraction: Double) -> String {
        fraction.formatted(.percent.precision(.fractionLength(0)))
    }

    /// A bare number at a fixed number of decimal places, e.g. `"24.3"`.
    static func number(_ value: Double, decimals: Int = 1) -> String {
        value.formatted(.number.precision(.fractionLength(decimals)))
    }
}
