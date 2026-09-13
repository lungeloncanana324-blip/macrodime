//
//  FoodCatalog.swift
//  MacroDime
//
//  The curated in-memory ingredient database. Seeded into SwiftData on first
//  launch (see `CatalogSeeder`) so user-created foods and meal references can
//  live alongside it, but the canonical definitions are here in code.
//
//  Macro values are per the stated serving and are drawn from standard
//  reference data (USDA FoodData Central for whole foods, typical label values
//  for packaged goods). Prices are US national-average supermarket prices and
//  are deliberately approximate — they exist to *rank* ingredients against each
//  other, which is all the swap engine needs. They are not converted to local
//  currency; a shipping build should either localise this table or let the user
//  edit `costPerServing` per item.
//

import Foundation

enum FoodCatalog {

    /// Every curated ingredient, in no particular order. `all` is computed once
    /// and cached by the `static let`, so repeated engine calls do not rebuild it.
    static let all: [FoodSnapshot] = proteins + carbohydrates + fats + vegetables + fruits + dairy + condiments

    /// Fast lookup by stable catalogue id.
    static let byID: [String: FoodSnapshot] = Dictionary(
        uniqueKeysWithValues: all.map { ($0.id, $0) }
    )

    static func food(id: String) -> FoodSnapshot? { byID[id] }

    // MARK: - Protein anchors

    private static let proteins: [FoodSnapshot] = [
        // ---- Strict tier: the budget powerhouses ----
        FoodSnapshot(
            id: "eggs-large",
            name: "Large Eggs",
            section: .dairy,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 0.36,
            servingDescription: "2 large eggs",
            servingGrams: 100,
            nutrition: NutritionFacts(calories: 143, protein: 12.6, carbs: 0.7, fat: 9.5),
            satietyIndex: 72
        ),
        FoodSnapshot(
            id: "canned-tuna-water",
            name: "Canned Tuna in Water",
            section: .pantry,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 1.10,
            servingDescription: "1 can (142 g drained)",
            servingGrams: 142,
            nutrition: NutritionFacts(calories: 163, protein: 36.0, carbs: 0, fat: 1.4),
            satietyIndex: 85
        ),
        FoodSnapshot(
            id: "chicken-thighs",
            name: "Chicken Thighs, Boneless Skinless",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 1.15,
            servingDescription: "150 g raw",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 218, protein: 29.6, carbs: 0, fat: 10.8),
            satietyIndex: 74
        ),
        FoodSnapshot(
            id: "chicken-drumsticks",
            name: "Chicken Drumsticks",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 0.92,
            servingDescription: "150 g raw",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 215, protein: 28.4, carbs: 0, fat: 11.2),
            satietyIndex: 72
        ),
        FoodSnapshot(
            id: "dried-lentils",
            name: "Dried Lentils",
            section: .pantry,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 0.34,
            servingDescription: "80 g dry",
            servingGrams: 80,
            nutrition: NutritionFacts(calories: 278, protein: 20.2, carbs: 48.0, fat: 0.9),
            satietyIndex: 88
        ),
        FoodSnapshot(
            id: "canned-black-beans",
            name: "Canned Black Beans",
            section: .pantry,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 0.55,
            servingDescription: "130 g drained",
            servingGrams: 130,
            nutrition: NutritionFacts(calories: 145, protein: 9.0, carbs: 26.0, fat: 0.5),
            satietyIndex: 80
        ),
        FoodSnapshot(
            id: "canned-chickpeas",
            name: "Canned Chickpeas",
            section: .pantry,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 0.58,
            servingDescription: "130 g drained",
            servingGrams: 130,
            nutrition: NutritionFacts(calories: 164, protein: 8.9, carbs: 27.0, fat: 2.6),
            satietyIndex: 78
        ),
        FoodSnapshot(
            id: "greek-yogurt-nonfat",
            name: "Plain Nonfat Greek Yogurt, Store Brand",
            section: .dairy,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 0.90,
            servingDescription: "170 g cup",
            servingGrams: 170,
            nutrition: NutritionFacts(calories: 100, protein: 17.3, carbs: 6.1, fat: 0.7),
            satietyIndex: 82
        ),
        FoodSnapshot(
            id: "ground-beef-80-20",
            name: "Ground Beef, 80/20",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 1.62,
            servingDescription: "113 g raw",
            servingGrams: 113,
            nutrition: NutritionFacts(calories: 287, protein: 19.4, carbs: 0, fat: 22.6),
            satietyIndex: 70
        ),
        FoodSnapshot(
            id: "pork-shoulder",
            name: "Pork Shoulder",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 1.05,
            servingDescription: "150 g raw",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 250, protein: 26.1, carbs: 0, fat: 15.8),
            satietyIndex: 70
        ),
        FoodSnapshot(
            id: "firm-tofu",
            name: "Firm Tofu",
            section: .produce,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 0.82,
            servingDescription: "150 g block portion",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 173, protein: 17.3, carbs: 4.2, fat: 10.0),
            satietyIndex: 70
        ),
        FoodSnapshot(
            id: "canned-sardines",
            name: "Canned Sardines in Oil",
            section: .pantry,
            category: .proteinAnchor,
            costTier: .strict,
            costPerServing: 1.35,
            servingDescription: "1 tin (92 g drained)",
            servingGrams: 92,
            nutrition: NutritionFacts(calories: 191, protein: 22.7, carbs: 0, fat: 10.5),
            satietyIndex: 82
        ),

        // ---- Moderate tier: fresh cuts and specialty proteins ----
        FoodSnapshot(
            id: "salmon-fillet",
            name: "Atlantic Salmon Fillet",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 5.20,
            servingDescription: "140 g raw fillet",
            servingGrams: 140,
            nutrition: NutritionFacts(calories: 291, protein: 34.0, carbs: 0, fat: 17.0),
            satietyIndex: 82
        ),
        FoodSnapshot(
            id: "chicken-breast",
            name: "Chicken Breast, Boneless Skinless",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 2.40,
            servingDescription: "150 g raw",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 165, protein: 34.5, carbs: 0, fat: 3.6),
            satietyIndex: 80
        ),
        FoodSnapshot(
            id: "sirloin-steak",
            name: "Sirloin Steak",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 4.60,
            servingDescription: "150 g raw",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 311, protein: 38.6, carbs: 0, fat: 16.4),
            satietyIndex: 78
        ),
        FoodSnapshot(
            id: "cod-fillet",
            name: "Cod Fillet",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 3.80,
            servingDescription: "150 g raw",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 135, protein: 29.6, carbs: 0, fat: 1.1),
            satietyIndex: 83
        ),
        FoodSnapshot(
            id: "shrimp",
            name: "Raw Shrimp, Peeled",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 3.20,
            servingDescription: "140 g raw",
            servingGrams: 140,
            nutrition: NutritionFacts(calories: 140, protein: 27.0, carbs: 0.7, fat: 1.5),
            satietyIndex: 80
        ),
        FoodSnapshot(
            id: "ground-beef-93-7",
            name: "Grass-Fed Ground Beef, 93/7",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 3.10,
            servingDescription: "113 g raw",
            servingGrams: 113,
            nutrition: NutritionFacts(calories: 172, protein: 21.8, carbs: 0, fat: 9.0),
            satietyIndex: 74
        ),
        FoodSnapshot(
            id: "whey-isolate",
            name: "Whey Protein Isolate",
            section: .pantry,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 1.30,
            servingDescription: "1 scoop (32 g)",
            servingGrams: 32,
            nutrition: NutritionFacts(calories: 120, protein: 25.0, carbs: 2.0, fat: 1.0),
            satietyIndex: 58
        ),
        FoodSnapshot(
            id: "eggs-pasture-organic",
            name: "Organic Pasture-Raised Eggs",
            section: .dairy,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 0.98,
            servingDescription: "2 large eggs",
            servingGrams: 100,
            nutrition: NutritionFacts(calories: 143, protein: 12.6, carbs: 0.7, fat: 9.5),
            satietyIndex: 72
        ),
        FoodSnapshot(
            id: "turkey-breast-deli",
            name: "Sliced Turkey Breast",
            section: .meatAndSeafood,
            category: .proteinAnchor,
            costTier: .moderate,
            costPerServing: 1.90,
            servingDescription: "100 g",
            servingGrams: 100,
            nutrition: NutritionFacts(calories: 104, protein: 17.1, carbs: 4.2, fat: 2.0),
            satietyIndex: 65
        )
    ]

    // MARK: - Carbohydrate bases

    private static let carbohydrates: [FoodSnapshot] = [
        FoodSnapshot(
            id: "rolled-oats",
            name: "Rolled Oats",
            section: .pantry,
            category: .carbBase,
            costTier: .strict,
            costPerServing: 0.22,
            servingDescription: "60 g dry",
            servingGrams: 60,
            nutrition: NutritionFacts(calories: 228, protein: 8.4, carbs: 39.0, fat: 4.2),
            satietyIndex: 85
        ),
        FoodSnapshot(
            id: "white-rice",
            name: "Long-Grain White Rice",
            section: .pantry,
            category: .carbBase,
            costTier: .strict,
            costPerServing: 0.18,
            servingDescription: "75 g dry",
            servingGrams: 75,
            nutrition: NutritionFacts(calories: 271, protein: 5.0, carbs: 59.6, fat: 0.5),
            satietyIndex: 70
        ),
        FoodSnapshot(
            id: "brown-rice",
            name: "Brown Rice",
            section: .pantry,
            category: .carbBase,
            costTier: .strict,
            costPerServing: 0.28,
            servingDescription: "75 g dry",
            servingGrams: 75,
            nutrition: NutritionFacts(calories: 270, protein: 5.6, carbs: 56.6, fat: 2.1),
            satietyIndex: 76
        ),
        FoodSnapshot(
            id: "dried-pasta",
            name: "Dried Pasta",
            section: .pantry,
            category: .carbBase,
            costTier: .strict,
            costPerServing: 0.30,
            servingDescription: "85 g dry",
            servingGrams: 85,
            nutrition: NutritionFacts(calories: 315, protein: 11.0, carbs: 63.4, fat: 1.3),
            satietyIndex: 72
        ),
        FoodSnapshot(
            id: "potatoes",
            name: "Potatoes",
            section: .produce,
            category: .carbBase,
            costTier: .strict,
            costPerServing: 0.35,
            servingDescription: "300 g raw",
            servingGrams: 300,
            nutrition: NutritionFacts(calories: 231, protein: 6.2, carbs: 52.4, fat: 0.3),
            satietyIndex: 95
        ),
        FoodSnapshot(
            id: "sweet-potato",
            name: "Sweet Potato",
            section: .produce,
            category: .carbBase,
            costTier: .strict,
            costPerServing: 0.55,
            servingDescription: "250 g raw",
            servingGrams: 250,
            nutrition: NutritionFacts(calories: 215, protein: 4.0, carbs: 50.1, fat: 0.3),
            satietyIndex: 92
        ),
        FoodSnapshot(
            id: "whole-wheat-bread",
            name: "Whole Wheat Bread, Store Brand",
            section: .pantry,
            category: .carbBase,
            costTier: .strict,
            costPerServing: 0.30,
            servingDescription: "2 slices",
            servingGrams: 60,
            nutrition: NutritionFacts(calories: 150, protein: 7.2, carbs: 26.0, fat: 2.0),
            satietyIndex: 68
        ),
        FoodSnapshot(
            id: "quinoa",
            name: "Quinoa",
            section: .pantry,
            category: .carbBase,
            costTier: .moderate,
            costPerServing: 0.95,
            servingDescription: "75 g dry",
            servingGrams: 75,
            nutrition: NutritionFacts(calories: 278, protein: 10.5, carbs: 48.0, fat: 4.6),
            satietyIndex: 80
        ),
        FoodSnapshot(
            id: "sourdough-bread",
            name: "Sourdough Loaf",
            section: .pantry,
            category: .carbBase,
            costTier: .moderate,
            costPerServing: 0.85,
            servingDescription: "2 slices",
            servingGrams: 70,
            nutrition: NutritionFacts(calories: 190, protein: 7.0, carbs: 37.0, fat: 1.2),
            satietyIndex: 70
        ),
        FoodSnapshot(
            id: "sprouted-grain-bread",
            name: "Sprouted Grain Bread",
            section: .frozen,
            category: .carbBase,
            costTier: .moderate,
            costPerServing: 0.90,
            servingDescription: "2 slices",
            servingGrams: 68,
            nutrition: NutritionFacts(calories: 160, protein: 8.0, carbs: 30.0, fat: 1.0),
            satietyIndex: 74
        )
    ]

    // MARK: - Fat sources

    private static let fats: [FoodSnapshot] = [
        FoodSnapshot(
            id: "canola-oil",
            name: "Canola Oil",
            section: .pantry,
            category: .fatSource,
            costTier: .strict,
            costPerServing: 0.06,
            servingDescription: "1 tbsp (14 g)",
            servingGrams: 14,
            nutrition: NutritionFacts(calories: 124, protein: 0, carbs: 0, fat: 14.0),
            satietyIndex: 20
        ),
        FoodSnapshot(
            id: "peanut-butter",
            name: "Peanut Butter, Store Brand",
            section: .pantry,
            category: .fatSource,
            costTier: .strict,
            costPerServing: 0.25,
            servingDescription: "2 tbsp (32 g)",
            servingGrams: 32,
            nutrition: NutritionFacts(calories: 190, protein: 7.1, carbs: 7.0, fat: 16.0),
            satietyIndex: 60
        ),
        FoodSnapshot(
            id: "sunflower-seeds",
            name: "Sunflower Seeds",
            section: .pantry,
            category: .fatSource,
            costTier: .strict,
            costPerServing: 0.30,
            servingDescription: "28 g",
            servingGrams: 28,
            nutrition: NutritionFacts(calories: 164, protein: 5.8, carbs: 6.8, fat: 14.1),
            satietyIndex: 58
        ),
        FoodSnapshot(
            id: "olive-oil",
            name: "Extra Virgin Olive Oil",
            section: .pantry,
            category: .fatSource,
            costTier: .moderate,
            costPerServing: 0.28,
            servingDescription: "1 tbsp (14 g)",
            servingGrams: 14,
            nutrition: NutritionFacts(calories: 119, protein: 0, carbs: 0, fat: 13.5),
            satietyIndex: 20
        ),
        FoodSnapshot(
            id: "avocado",
            name: "Avocado",
            section: .produce,
            category: .fatSource,
            costTier: .moderate,
            costPerServing: 1.20,
            servingDescription: "1/2 medium (100 g)",
            servingGrams: 100,
            nutrition: NutritionFacts(calories: 160, protein: 2.0, carbs: 8.5, fat: 14.7),
            satietyIndex: 65
        ),
        FoodSnapshot(
            id: "almonds",
            name: "Almonds",
            section: .pantry,
            category: .fatSource,
            costTier: .moderate,
            costPerServing: 0.55,
            servingDescription: "28 g",
            servingGrams: 28,
            nutrition: NutritionFacts(calories: 164, protein: 6.0, carbs: 6.1, fat: 14.2),
            satietyIndex: 62
        )
    ]

    // MARK: - Vegetables

    private static let vegetables: [FoodSnapshot] = [
        FoodSnapshot(
            id: "frozen-mixed-vegetables",
            name: "Frozen Mixed Vegetables",
            section: .frozen,
            category: .vegetable,
            costTier: .strict,
            costPerServing: 0.45,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 80, protein: 4.0, carbs: 14.0, fat: 0.5),
            satietyIndex: 88
        ),
        FoodSnapshot(
            id: "frozen-broccoli",
            name: "Frozen Broccoli Florets",
            section: .frozen,
            category: .vegetable,
            costTier: .strict,
            costPerServing: 0.50,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 51, protein: 4.2, carbs: 10.0, fat: 0.5),
            satietyIndex: 90
        ),
        FoodSnapshot(
            id: "cabbage",
            name: "Green Cabbage",
            section: .produce,
            category: .vegetable,
            costTier: .strict,
            costPerServing: 0.30,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 38, protein: 1.9, carbs: 8.7, fat: 0.2),
            satietyIndex: 90
        ),
        FoodSnapshot(
            id: "carrots",
            name: "Carrots",
            section: .produce,
            category: .vegetable,
            costTier: .strict,
            costPerServing: 0.25,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 61, protein: 1.4, carbs: 14.4, fat: 0.4),
            satietyIndex: 90
        ),
        FoodSnapshot(
            id: "onion",
            name: "Yellow Onion",
            section: .produce,
            category: .vegetable,
            costTier: .strict,
            costPerServing: 0.25,
            servingDescription: "1 medium (110 g)",
            servingGrams: 110,
            nutrition: NutritionFacts(calories: 44, protein: 1.2, carbs: 10.3, fat: 0.1),
            satietyIndex: 70
        ),
        FoodSnapshot(
            id: "fresh-broccoli",
            name: "Fresh Broccoli",
            section: .produce,
            category: .vegetable,
            costTier: .moderate,
            costPerServing: 1.10,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 51, protein: 4.2, carbs: 10.0, fat: 0.5),
            satietyIndex: 90
        ),
        FoodSnapshot(
            id: "baby-spinach",
            name: "Baby Spinach",
            section: .produce,
            category: .vegetable,
            costTier: .moderate,
            costPerServing: 1.25,
            servingDescription: "100 g",
            servingGrams: 100,
            nutrition: NutritionFacts(calories: 23, protein: 2.9, carbs: 3.6, fat: 0.4),
            satietyIndex: 88
        ),
        FoodSnapshot(
            id: "bell-pepper",
            name: "Bell Pepper",
            section: .produce,
            category: .vegetable,
            costTier: .moderate,
            costPerServing: 1.10,
            servingDescription: "1 medium (120 g)",
            servingGrams: 120,
            nutrition: NutritionFacts(calories: 37, protein: 1.2, carbs: 7.2, fat: 0.4),
            satietyIndex: 85
        ),
        FoodSnapshot(
            id: "asparagus",
            name: "Asparagus",
            section: .produce,
            category: .vegetable,
            costTier: .moderate,
            costPerServing: 2.20,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 30, protein: 3.3, carbs: 5.8, fat: 0.2),
            satietyIndex: 88
        )
    ]

    // MARK: - Fruit

    private static let fruits: [FoodSnapshot] = [
        FoodSnapshot(
            id: "banana",
            name: "Banana",
            section: .produce,
            category: .fruit,
            costTier: .strict,
            costPerServing: 0.28,
            servingDescription: "1 medium (118 g)",
            servingGrams: 118,
            nutrition: NutritionFacts(calories: 105, protein: 1.3, carbs: 27.0, fat: 0.4),
            satietyIndex: 90
        ),
        FoodSnapshot(
            id: "frozen-berries",
            name: "Frozen Mixed Berries",
            section: .frozen,
            category: .fruit,
            costTier: .strict,
            costPerServing: 0.80,
            servingDescription: "120 g",
            servingGrams: 120,
            nutrition: NutritionFacts(calories: 70, protein: 1.0, carbs: 16.0, fat: 0.5),
            satietyIndex: 82
        ),
        FoodSnapshot(
            id: "apple",
            name: "Apple",
            section: .produce,
            category: .fruit,
            costTier: .strict,
            costPerServing: 0.55,
            servingDescription: "1 medium (182 g)",
            servingGrams: 182,
            nutrition: NutritionFacts(calories: 95, protein: 0.5, carbs: 25.1, fat: 0.3),
            satietyIndex: 88
        ),
        FoodSnapshot(
            id: "fresh-blueberries",
            name: "Fresh Blueberries",
            section: .produce,
            category: .fruit,
            costTier: .moderate,
            costPerServing: 2.10,
            servingDescription: "120 g",
            servingGrams: 120,
            nutrition: NutritionFacts(calories: 68, protein: 0.9, carbs: 17.4, fat: 0.4),
            satietyIndex: 82
        )
    ]

    // MARK: - Dairy

    private static let dairy: [FoodSnapshot] = [
        FoodSnapshot(
            id: "whole-milk",
            name: "Whole Milk",
            section: .dairy,
            category: .dairy,
            costTier: .strict,
            costPerServing: 0.35,
            servingDescription: "240 ml",
            servingGrams: 244,
            nutrition: NutritionFacts(calories: 149, protein: 7.7, carbs: 11.7, fat: 8.0),
            satietyIndex: 65
        ),
        FoodSnapshot(
            id: "cottage-cheese",
            name: "Cottage Cheese, 2%",
            section: .dairy,
            category: .dairy,
            costTier: .strict,
            costPerServing: 0.85,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 122, protein: 17.3, carbs: 5.4, fat: 3.3),
            satietyIndex: 82
        ),
        FoodSnapshot(
            id: "cheddar-block",
            name: "Cheddar Cheese, Block",
            section: .dairy,
            category: .dairy,
            costTier: .strict,
            costPerServing: 0.55,
            servingDescription: "28 g",
            servingGrams: 28,
            nutrition: NutritionFacts(calories: 113, protein: 7.0, carbs: 0.4, fat: 9.3),
            satietyIndex: 50
        ),
        FoodSnapshot(
            id: "skyr",
            name: "Icelandic Skyr",
            section: .dairy,
            category: .dairy,
            costTier: .moderate,
            costPerServing: 1.75,
            servingDescription: "150 g",
            servingGrams: 150,
            nutrition: NutritionFacts(calories: 95, protein: 17.0, carbs: 5.0, fat: 0.2),
            satietyIndex: 82
        )
    ]

    // MARK: - Condiments
    //
    // Flagged `isSwapCandidate: false`: scaling a near-zero-calorie item to
    // macro-match something produces absurd quantities, and swapping it saves
    // pennies at best.

    private static let condiments: [FoodSnapshot] = [
        FoodSnapshot(
            id: "mixed-spices",
            name: "Mixed Herbs & Spices",
            section: .pantry,
            category: .condiment,
            costTier: .strict,
            costPerServing: 0.10,
            servingDescription: "1 tsp",
            servingGrams: 2,
            nutrition: NutritionFacts(calories: 6, protein: 0.2, carbs: 1.2, fat: 0.1),
            satietyIndex: 0,
            isSwapCandidate: false
        ),
        FoodSnapshot(
            id: "soy-sauce",
            name: "Soy Sauce",
            section: .pantry,
            category: .condiment,
            costTier: .strict,
            costPerServing: 0.08,
            servingDescription: "1 tbsp (16 g)",
            servingGrams: 16,
            nutrition: NutritionFacts(calories: 9, protein: 1.3, carbs: 0.8, fat: 0.1),
            satietyIndex: 0,
            isSwapCandidate: false
        ),
        FoodSnapshot(
            id: "hot-sauce",
            name: "Hot Sauce",
            section: .pantry,
            category: .condiment,
            costTier: .strict,
            costPerServing: 0.07,
            servingDescription: "1 tbsp (15 g)",
            servingGrams: 15,
            nutrition: NutritionFacts(calories: 5, protein: 0.2, carbs: 0.9, fat: 0.1),
            satietyIndex: 0,
            isSwapCandidate: false
        )
    ]
}
