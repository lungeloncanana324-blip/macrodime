//
//  CatalogSeeder.swift
//  MacroDime
//
//  Mirrors the curated `FoodCatalog` into SwiftData. Idempotent and run on every
//  launch, which is how catalogue corrections (a monthly price refresh, a fixed
//  macro value) reach existing installs without a migration.
//
//  What changed is decided by `CatalogSync`, by comparing values. There is no
//  version number to bump by hand: the old one was never bumped by the price
//  job, so refreshed prices would have reached new installs only.
//

import Foundation
import SwiftData

@MainActor
enum CatalogSeeder {

    /// Inserts missing curated foods and rewrites the ones whose values have
    /// changed. User-created foods are never touched, and a launch with nothing
    /// to change writes nothing. The cost is one fetch of 57 rows.
    static func seedIfNeeded(context: ModelContext) {
        do {
            let descriptor = FetchDescriptor<FoodItem>(
                predicate: #Predicate { $0.isUserCreated == false }
            )
            let existing = try context.fetch(descriptor)
            let changes = CatalogSync.changes(stored: existing.map(\.snapshot))
            guard !changes.isEmpty else { return }

            let existingByID = Dictionary(
                existing.map { ($0.catalogID, $0) },
                uniquingKeysWith: { first, _ in first }
            )
            for snapshot in changes.updates {
                existingByID[snapshot.id]?.update(from: snapshot)
            }
            for snapshot in changes.inserts {
                context.insert(FoodItem(snapshot: snapshot))
            }

            // Curated foods that have been retired from the catalogue are left
            // in place rather than deleted: a past meal may still point at one,
            // and nullifying that reference would erase history.

            try context.save()
        } catch {
            // A failed seed is recoverable: nothing was marked done, so the
            // next launch compares again and retries.
            assertionFailure("Catalog seed failed: \(error)")
        }
    }

    /// Creates the single `UserProfile` row if the store is empty, and returns
    /// the profile either way.
    static func ensureProfile(context: ModelContext) -> UserProfile? {
        do {
            let existing = try context.fetch(FetchDescriptor<UserProfile>())
            if let profile = existing.first { return profile }

            let profile = UserProfile()
            context.insert(profile)
            try context.save()
            return profile
        } catch {
            assertionFailure("Profile bootstrap failed: \(error)")
            return nil
        }
    }
}
