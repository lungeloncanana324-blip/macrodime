//
//  CatalogSeeder.swift
//  MacroDime
//
//  Mirrors the curated `FoodCatalog` into SwiftData. Idempotent: safe to run on
//  every launch, which is how catalogue corrections (a price change, a fixed
//  macro value) reach existing installs without a migration.
//

import Foundation
import SwiftData

@MainActor
enum CatalogSeeder {

    /// Bumped whenever the curated catalogue's *values* change. Stored in
    /// `UserDefaults`, so the common launch does one integer comparison instead
    /// of a full table diff.
    static let catalogVersion = 1
    private static let versionKey = "MacroDime.catalogVersion"

    /// Inserts missing curated foods and refreshes the ones already present.
    /// User-created foods are never touched.
    static func seedIfNeeded(
        context: ModelContext,
        defaults: UserDefaults = .standard,
        force: Bool = false
    ) {
        let installed = defaults.integer(forKey: versionKey)
        guard force || installed < catalogVersion else { return }

        do {
            let descriptor = FetchDescriptor<FoodItem>(
                predicate: #Predicate { $0.isUserCreated == false }
            )
            let existing = try context.fetch(descriptor)
            var existingByID = Dictionary(
                existing.map { ($0.catalogID, $0) },
                uniquingKeysWith: { first, _ in first }
            )

            for snapshot in FoodCatalog.all {
                if let record = existingByID.removeValue(forKey: snapshot.id) {
                    record.update(from: snapshot)
                } else {
                    context.insert(FoodItem(snapshot: snapshot))
                }
            }

            // Curated foods that have been retired from the catalogue. They are
            // left in place rather than deleted: a past meal may still point at
            // one, and nullifying that reference would erase history.

            try context.save()
            defaults.set(catalogVersion, forKey: versionKey)
        } catch {
            // A failed seed is recoverable — the next launch retries, since the
            // version key is only written on success.
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
