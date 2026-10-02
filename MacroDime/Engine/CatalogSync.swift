//
//  CatalogSync.swift
//  MacroDime
//
//  Decides what the seeder must write to bring a stored catalogue up to date.
//
//  It compares values, not a version number. The seeder used to rerun only when
//  a hand-bumped `catalogVersion` rose, and the monthly price job never bumps
//  it, so after launch a price refresh would have reached new installs only.
//  Found by the Android port, which compares values the same way.
//
//  Pure, so it is tested on Linux; `CatalogSeeder` applies the result to
//  SwiftData.
//

import Foundation

enum CatalogSync {

    struct Changes: Equatable, Sendable {
        /// Curated foods the store does not have yet.
        var inserts: [FoodSnapshot]
        /// Curated foods whose stored values differ from the catalogue's. Each
        /// entry is the catalogue's value, to be written over the stored one.
        var updates: [FoodSnapshot]

        var isEmpty: Bool { inserts.isEmpty && updates.isEmpty }
    }

    /// What to write so that every curated food in `stored` matches
    /// `catalogue`.
    ///
    /// `stored` holds curated rows only; user-created foods are never passed in
    /// and never touched. When the store holds two rows for one id, the first
    /// is the one compared, matching how the seeder looks rows up. Curated foods
    /// retired from the catalogue are not reported: a past meal may still point
    /// at one, and deleting it would erase history.
    static func changes(
        stored: [FoodSnapshot],
        catalogue: [FoodSnapshot] = FoodCatalog.all
    ) -> Changes {
        var storedByID: [String: FoodSnapshot] = [:]
        for food in stored where storedByID[food.id] == nil {
            storedByID[food.id] = food
        }

        var changes = Changes(inserts: [], updates: [])
        for wanted in catalogue {
            guard let current = storedByID[wanted.id] else {
                changes.inserts.append(wanted)
                continue
            }
            if current != wanted { changes.updates.append(wanted) }
        }
        return changes
    }
}
