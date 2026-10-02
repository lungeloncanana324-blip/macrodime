/*
 * FoodCatalog.kt
 * MacroDime
 *
 * The curated in-memory ingredient database. Port of
 * MacroDime/Engine/FoodCatalog.swift, generated from it field for field, so the
 * two apps plan with the same 57 foods.
 *
 * Seeded into the Room database on launch (see CatalogSeeder) so meal portions
 * can hold a real reference, but the canonical definitions are here in code.
 * Macros are per the stated serving, from USDA FoodData Central for whole foods
 * and typical label values for packaged goods. The prices written below are
 * hand-set US estimates, frozen as [reference] because the engine tests are
 * worked against them. The app uses [all], which replaces each with a published
 * average wherever PriceTable has one.
 *
 * Dietary traits and preparation times are held in lookup tables at the end of
 * the file rather than in each declaration, and applied by annotated().
 */
package com.lungelo.macrodime.engine

import com.lungelo.macrodime.domain.BudgetTier
import com.lungelo.macrodime.domain.FoodCategory
import com.lungelo.macrodime.domain.FoodSnapshot
import com.lungelo.macrodime.domain.FoodTraits
import com.lungelo.macrodime.domain.GrocerySection
import com.lungelo.macrodime.domain.NutritionFacts
import com.lungelo.macrodime.domain.SwapGroup

object FoodCatalog {

    // Protein anchors

    private val proteins: List<FoodSnapshot> = listOf(
        // Strict tier: the budget powerhouses
        FoodSnapshot(
            id = "eggs-large",
            name = "Large Eggs",
            section = GrocerySection.Dairy,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 0.36,
            servingDescription = "2 large eggs",
            servingGrams = 100.0,
            nutrition = NutritionFacts(calories = 143.0, protein = 12.6, carbs = 0.7, fat = 9.5),
            satietyIndex = 72.0,
        ),
        FoodSnapshot(
            id = "canned-tuna-water",
            name = "Canned Tuna in Water",
            section = GrocerySection.Pantry,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 1.10,
            servingDescription = "1 can (142 g drained)",
            servingGrams = 142.0,
            nutrition = NutritionFacts(calories = 163.0, protein = 36.0, carbs = 0.0, fat = 1.4),
            satietyIndex = 85.0,
        ),
        FoodSnapshot(
            id = "chicken-thighs",
            name = "Chicken Thighs, Boneless Skinless",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 1.15,
            servingDescription = "150 g raw",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 218.0, protein = 29.6, carbs = 0.0, fat = 10.8),
            satietyIndex = 74.0,
        ),
        FoodSnapshot(
            id = "chicken-drumsticks",
            name = "Chicken Drumsticks",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 0.92,
            servingDescription = "150 g raw",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 215.0, protein = 28.4, carbs = 0.0, fat = 11.2),
            satietyIndex = 72.0,
        ),
        FoodSnapshot(
            id = "dried-lentils",
            name = "Dried Lentils",
            section = GrocerySection.Pantry,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 0.34,
            servingDescription = "80 g dry",
            servingGrams = 80.0,
            nutrition = NutritionFacts(calories = 278.0, protein = 20.2, carbs = 48.0, fat = 0.9),
            satietyIndex = 88.0,
        ),
        FoodSnapshot(
            id = "canned-black-beans",
            name = "Canned Black Beans",
            section = GrocerySection.Pantry,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 0.55,
            servingDescription = "130 g drained",
            servingGrams = 130.0,
            nutrition = NutritionFacts(calories = 145.0, protein = 9.0, carbs = 26.0, fat = 0.5),
            satietyIndex = 80.0,
        ),
        FoodSnapshot(
            id = "canned-chickpeas",
            name = "Canned Chickpeas",
            section = GrocerySection.Pantry,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 0.58,
            servingDescription = "130 g drained",
            servingGrams = 130.0,
            nutrition = NutritionFacts(calories = 164.0, protein = 8.9, carbs = 27.0, fat = 2.6),
            satietyIndex = 78.0,
        ),
        FoodSnapshot(
            id = "greek-yogurt-nonfat",
            name = "Plain Nonfat Greek Yogurt, Store Brand",
            section = GrocerySection.Dairy,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 0.90,
            servingDescription = "1 tub (170 g)",
            servingGrams = 170.0,
            nutrition = NutritionFacts(calories = 100.0, protein = 17.3, carbs = 6.1, fat = 0.7),
            satietyIndex = 82.0,
        ),
        FoodSnapshot(
            id = "ground-beef-80-20",
            name = "Ground Beef, 80/20",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 1.62,
            servingDescription = "113 g raw",
            servingGrams = 113.0,
            nutrition = NutritionFacts(calories = 287.0, protein = 19.4, carbs = 0.0, fat = 22.6),
            satietyIndex = 70.0,
        ),
        FoodSnapshot(
            id = "pork-shoulder",
            name = "Pork Shoulder",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 1.05,
            servingDescription = "150 g raw",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 250.0, protein = 26.1, carbs = 0.0, fat = 15.8),
            satietyIndex = 70.0,
        ),
        FoodSnapshot(
            id = "firm-tofu",
            name = "Firm Tofu",
            section = GrocerySection.Produce,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 0.82,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 173.0, protein = 17.3, carbs = 4.2, fat = 10.0),
            satietyIndex = 70.0,
        ),
        FoodSnapshot(
            id = "canned-sardines",
            name = "Canned Sardines in Oil",
            section = GrocerySection.Pantry,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Strict,
            costPerServing = 1.35,
            servingDescription = "1 tin (92 g drained)",
            servingGrams = 92.0,
            nutrition = NutritionFacts(calories = 191.0, protein = 22.7, carbs = 0.0, fat = 10.5),
            satietyIndex = 82.0,
        ),
        // Moderate tier: fresh cuts and specialty proteins
        FoodSnapshot(
            id = "salmon-fillet",
            name = "Atlantic Salmon Fillet",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 5.20,
            servingDescription = "140 g raw fillet",
            servingGrams = 140.0,
            nutrition = NutritionFacts(calories = 291.0, protein = 34.0, carbs = 0.0, fat = 17.0),
            satietyIndex = 82.0,
        ),
        FoodSnapshot(
            id = "chicken-breast",
            name = "Chicken Breast, Boneless Skinless",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 2.40,
            servingDescription = "150 g raw",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 165.0, protein = 34.5, carbs = 0.0, fat = 3.6),
            satietyIndex = 80.0,
        ),
        FoodSnapshot(
            id = "sirloin-steak",
            name = "Sirloin Steak",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 4.60,
            servingDescription = "150 g raw",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 311.0, protein = 38.6, carbs = 0.0, fat = 16.4),
            satietyIndex = 78.0,
        ),
        FoodSnapshot(
            id = "cod-fillet",
            name = "Cod Fillet",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 3.80,
            servingDescription = "150 g raw",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 135.0, protein = 29.6, carbs = 0.0, fat = 1.1),
            satietyIndex = 83.0,
        ),
        FoodSnapshot(
            id = "shrimp",
            name = "Raw Shrimp, Peeled",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 3.20,
            servingDescription = "140 g raw",
            servingGrams = 140.0,
            nutrition = NutritionFacts(calories = 140.0, protein = 27.0, carbs = 0.7, fat = 1.5),
            satietyIndex = 80.0,
        ),
        FoodSnapshot(
            id = "ground-beef-93-7",
            name = "Grass-Fed Ground Beef, 93/7",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 3.10,
            servingDescription = "113 g raw",
            servingGrams = 113.0,
            nutrition = NutritionFacts(calories = 172.0, protein = 21.8, carbs = 0.0, fat = 9.0),
            satietyIndex = 74.0,
        ),
        FoodSnapshot(
            id = "whey-isolate",
            name = "Whey Protein Isolate",
            section = GrocerySection.Pantry,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 1.30,
            servingDescription = "1 scoop (32 g)",
            servingGrams = 32.0,
            nutrition = NutritionFacts(calories = 120.0, protein = 25.0, carbs = 2.0, fat = 1.0),
            satietyIndex = 58.0,
        ),
        FoodSnapshot(
            id = "eggs-pasture-organic",
            name = "Organic Pasture-Raised Eggs",
            section = GrocerySection.Dairy,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 0.98,
            servingDescription = "2 large eggs",
            servingGrams = 100.0,
            nutrition = NutritionFacts(calories = 143.0, protein = 12.6, carbs = 0.7, fat = 9.5),
            satietyIndex = 72.0,
        ),
        FoodSnapshot(
            id = "turkey-breast-deli",
            name = "Sliced Turkey Breast",
            section = GrocerySection.MeatAndSeafood,
            category = FoodCategory.ProteinAnchor,
            costTier = BudgetTier.Moderate,
            costPerServing = 1.90,
            servingDescription = "100 g",
            servingGrams = 100.0,
            nutrition = NutritionFacts(calories = 104.0, protein = 17.1, carbs = 4.2, fat = 2.0),
            satietyIndex = 65.0,
        ),
    )

    // Carbohydrate bases

    private val carbohydrates: List<FoodSnapshot> = listOf(
        FoodSnapshot(
            id = "rolled-oats",
            name = "Rolled Oats",
            section = GrocerySection.Pantry,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Strict,
            costPerServing = 0.22,
            servingDescription = "60 g dry",
            servingGrams = 60.0,
            nutrition = NutritionFacts(calories = 228.0, protein = 8.4, carbs = 39.0, fat = 4.2),
            satietyIndex = 85.0,
        ),
        FoodSnapshot(
            id = "white-rice",
            name = "Long-Grain White Rice",
            section = GrocerySection.Pantry,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Strict,
            costPerServing = 0.18,
            servingDescription = "75 g dry",
            servingGrams = 75.0,
            nutrition = NutritionFacts(calories = 271.0, protein = 5.0, carbs = 59.6, fat = 0.5),
            satietyIndex = 70.0,
        ),
        FoodSnapshot(
            id = "brown-rice",
            name = "Brown Rice",
            section = GrocerySection.Pantry,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Strict,
            costPerServing = 0.28,
            servingDescription = "75 g dry",
            servingGrams = 75.0,
            nutrition = NutritionFacts(calories = 270.0, protein = 5.6, carbs = 56.6, fat = 2.1),
            satietyIndex = 76.0,
        ),
        FoodSnapshot(
            id = "dried-pasta",
            name = "Dried Pasta",
            section = GrocerySection.Pantry,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Strict,
            costPerServing = 0.30,
            servingDescription = "85 g dry",
            servingGrams = 85.0,
            nutrition = NutritionFacts(calories = 315.0, protein = 11.0, carbs = 63.4, fat = 1.3),
            satietyIndex = 72.0,
        ),
        FoodSnapshot(
            id = "potatoes",
            name = "Potatoes",
            section = GrocerySection.Produce,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Strict,
            costPerServing = 0.35,
            servingDescription = "300 g raw",
            servingGrams = 300.0,
            nutrition = NutritionFacts(calories = 231.0, protein = 6.2, carbs = 52.4, fat = 0.3),
            satietyIndex = 95.0,
        ),
        FoodSnapshot(
            id = "sweet-potato",
            name = "Sweet Potato",
            section = GrocerySection.Produce,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Strict,
            costPerServing = 0.55,
            servingDescription = "250 g raw",
            servingGrams = 250.0,
            nutrition = NutritionFacts(calories = 215.0, protein = 4.0, carbs = 50.1, fat = 0.3),
            satietyIndex = 92.0,
        ),
        FoodSnapshot(
            id = "whole-wheat-bread",
            name = "Whole Wheat Bread, Store Brand",
            section = GrocerySection.Pantry,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Strict,
            costPerServing = 0.30,
            servingDescription = "2 slices",
            servingGrams = 60.0,
            nutrition = NutritionFacts(calories = 150.0, protein = 7.2, carbs = 26.0, fat = 2.0),
            satietyIndex = 68.0,
        ),
        FoodSnapshot(
            id = "quinoa",
            name = "Quinoa",
            section = GrocerySection.Pantry,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Moderate,
            costPerServing = 0.95,
            servingDescription = "75 g dry",
            servingGrams = 75.0,
            nutrition = NutritionFacts(calories = 278.0, protein = 10.5, carbs = 48.0, fat = 4.6),
            satietyIndex = 80.0,
        ),
        FoodSnapshot(
            id = "sourdough-bread",
            name = "Sourdough Loaf",
            section = GrocerySection.Pantry,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Moderate,
            costPerServing = 0.85,
            servingDescription = "2 slices",
            servingGrams = 70.0,
            nutrition = NutritionFacts(calories = 190.0, protein = 7.0, carbs = 37.0, fat = 1.2),
            satietyIndex = 70.0,
        ),
        FoodSnapshot(
            id = "sprouted-grain-bread",
            name = "Sprouted Grain Bread",
            section = GrocerySection.Frozen,
            category = FoodCategory.CarbBase,
            costTier = BudgetTier.Moderate,
            costPerServing = 0.90,
            servingDescription = "2 slices",
            servingGrams = 68.0,
            nutrition = NutritionFacts(calories = 160.0, protein = 8.0, carbs = 30.0, fat = 1.0),
            satietyIndex = 74.0,
        ),
    )

    // Fat sources

    private val fats: List<FoodSnapshot> = listOf(
        FoodSnapshot(
            id = "canola-oil",
            name = "Canola Oil",
            section = GrocerySection.Pantry,
            category = FoodCategory.FatSource,
            costTier = BudgetTier.Strict,
            costPerServing = 0.06,
            servingDescription = "1 tbsp (14 g)",
            servingGrams = 14.0,
            nutrition = NutritionFacts(calories = 124.0, protein = 0.0, carbs = 0.0, fat = 14.0),
            satietyIndex = 20.0,
        ),
        FoodSnapshot(
            id = "peanut-butter",
            name = "Peanut Butter, Store Brand",
            section = GrocerySection.Pantry,
            category = FoodCategory.FatSource,
            costTier = BudgetTier.Strict,
            costPerServing = 0.25,
            servingDescription = "2 tbsp (32 g)",
            servingGrams = 32.0,
            nutrition = NutritionFacts(calories = 190.0, protein = 7.1, carbs = 7.0, fat = 16.0),
            satietyIndex = 60.0,
        ),
        FoodSnapshot(
            id = "sunflower-seeds",
            name = "Sunflower Seeds",
            section = GrocerySection.Pantry,
            category = FoodCategory.FatSource,
            costTier = BudgetTier.Strict,
            costPerServing = 0.30,
            servingDescription = "28 g",
            servingGrams = 28.0,
            nutrition = NutritionFacts(calories = 164.0, protein = 5.8, carbs = 6.8, fat = 14.1),
            satietyIndex = 58.0,
        ),
        FoodSnapshot(
            id = "olive-oil",
            name = "Extra Virgin Olive Oil",
            section = GrocerySection.Pantry,
            category = FoodCategory.FatSource,
            costTier = BudgetTier.Moderate,
            costPerServing = 0.28,
            servingDescription = "1 tbsp (14 g)",
            servingGrams = 14.0,
            nutrition = NutritionFacts(calories = 119.0, protein = 0.0, carbs = 0.0, fat = 13.5),
            satietyIndex = 20.0,
        ),
        FoodSnapshot(
            id = "avocado",
            name = "Avocado",
            section = GrocerySection.Produce,
            category = FoodCategory.FatSource,
            costTier = BudgetTier.Moderate,
            costPerServing = 1.20,
            servingDescription = "½ medium (100 g)",
            servingGrams = 100.0,
            nutrition = NutritionFacts(calories = 160.0, protein = 2.0, carbs = 8.5, fat = 14.7),
            satietyIndex = 65.0,
        ),
        FoodSnapshot(
            id = "almonds",
            name = "Almonds",
            section = GrocerySection.Pantry,
            category = FoodCategory.FatSource,
            costTier = BudgetTier.Moderate,
            costPerServing = 0.55,
            servingDescription = "28 g",
            servingGrams = 28.0,
            nutrition = NutritionFacts(calories = 164.0, protein = 6.0, carbs = 6.1, fat = 14.2),
            satietyIndex = 62.0,
        ),
    )

    // Vegetables
    //
    // Every vegetable names its culinary family explicitly. SwapGroup.defaultFor
    // maps Vegetable to Unclassified, which matches nothing, so a vegetable added
    // without a family has no substitutes rather than every other vegetable.
    // testEveryVegetableHasACulinaryFamily enforces that.

    private val vegetables: List<FoodSnapshot> = listOf(
        FoodSnapshot(
            id = "frozen-mixed-vegetables",
            name = "Frozen Mixed Vegetables",
            section = GrocerySection.Frozen,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.MixedFrozen,
            costTier = BudgetTier.Strict,
            costPerServing = 0.45,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 80.0, protein = 4.0, carbs = 14.0, fat = 0.5),
            satietyIndex = 88.0,
        ),
        FoodSnapshot(
            id = "frozen-broccoli",
            name = "Frozen Broccoli Florets",
            section = GrocerySection.Frozen,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.Cruciferous,
            costTier = BudgetTier.Strict,
            costPerServing = 0.50,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 51.0, protein = 4.2, carbs = 10.0, fat = 0.5),
            satietyIndex = 90.0,
        ),
        FoodSnapshot(
            id = "cabbage",
            name = "Green Cabbage",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.LeafyGreen,
            costTier = BudgetTier.Strict,
            costPerServing = 0.30,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 38.0, protein = 1.9, carbs = 8.7, fat = 0.2),
            satietyIndex = 90.0,
        ),
        FoodSnapshot(
            id = "carrots",
            name = "Carrots",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.Root,
            costTier = BudgetTier.Strict,
            costPerServing = 0.25,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 61.0, protein = 1.4, carbs = 14.4, fat = 0.4),
            satietyIndex = 90.0,
        ),
        FoodSnapshot(
            id = "onion",
            name = "Yellow Onion",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.Allium,
            costTier = BudgetTier.Strict,
            costPerServing = 0.25,
            servingDescription = "1 medium (110 g)",
            servingGrams = 110.0,
            nutrition = NutritionFacts(calories = 44.0, protein = 1.2, carbs = 10.3, fat = 0.1),
            satietyIndex = 70.0,
        ),
        FoodSnapshot(
            id = "fresh-broccoli",
            name = "Fresh Broccoli",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.Cruciferous,
            costTier = BudgetTier.Moderate,
            costPerServing = 1.10,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 51.0, protein = 4.2, carbs = 10.0, fat = 0.5),
            satietyIndex = 90.0,
        ),
        FoodSnapshot(
            id = "baby-spinach",
            name = "Baby Spinach",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.LeafyGreen,
            costTier = BudgetTier.Moderate,
            costPerServing = 1.25,
            servingDescription = "100 g",
            servingGrams = 100.0,
            nutrition = NutritionFacts(calories = 23.0, protein = 2.9, carbs = 3.6, fat = 0.4),
            satietyIndex = 88.0,
        ),
        FoodSnapshot(
            id = "bell-pepper",
            name = "Bell Pepper",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.Fruiting,
            costTier = BudgetTier.Moderate,
            costPerServing = 1.10,
            servingDescription = "1 medium (120 g)",
            servingGrams = 120.0,
            nutrition = NutritionFacts(calories = 37.0, protein = 1.2, carbs = 7.2, fat = 0.4),
            satietyIndex = 85.0,
        ),
        FoodSnapshot(
            id = "asparagus",
            name = "Asparagus",
            section = GrocerySection.Produce,
            category = FoodCategory.Vegetable,
            swapGroup = SwapGroup.Stem,
            costTier = BudgetTier.Moderate,
            costPerServing = 2.20,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 30.0, protein = 3.3, carbs = 5.8, fat = 0.2),
            satietyIndex = 88.0,
        ),
    )

    // Fruit

    private val fruits: List<FoodSnapshot> = listOf(
        FoodSnapshot(
            id = "banana",
            name = "Banana",
            section = GrocerySection.Produce,
            category = FoodCategory.Fruit,
            costTier = BudgetTier.Strict,
            costPerServing = 0.28,
            servingDescription = "1 medium (118 g)",
            servingGrams = 118.0,
            nutrition = NutritionFacts(calories = 105.0, protein = 1.3, carbs = 27.0, fat = 0.4),
            satietyIndex = 90.0,
        ),
        FoodSnapshot(
            id = "frozen-berries",
            name = "Frozen Mixed Berries",
            section = GrocerySection.Frozen,
            category = FoodCategory.Fruit,
            costTier = BudgetTier.Strict,
            costPerServing = 0.80,
            servingDescription = "120 g",
            servingGrams = 120.0,
            nutrition = NutritionFacts(calories = 70.0, protein = 1.0, carbs = 16.0, fat = 0.5),
            satietyIndex = 82.0,
        ),
        FoodSnapshot(
            id = "apple",
            name = "Apple",
            section = GrocerySection.Produce,
            category = FoodCategory.Fruit,
            costTier = BudgetTier.Strict,
            costPerServing = 0.55,
            servingDescription = "1 medium (182 g)",
            servingGrams = 182.0,
            nutrition = NutritionFacts(calories = 95.0, protein = 0.5, carbs = 25.1, fat = 0.3),
            satietyIndex = 88.0,
        ),
        FoodSnapshot(
            id = "fresh-blueberries",
            name = "Fresh Blueberries",
            section = GrocerySection.Produce,
            category = FoodCategory.Fruit,
            costTier = BudgetTier.Moderate,
            costPerServing = 2.10,
            servingDescription = "120 g",
            servingGrams = 120.0,
            nutrition = NutritionFacts(calories = 68.0, protein = 0.9, carbs = 17.4, fat = 0.4),
            satietyIndex = 82.0,
        ),
    )

    // Dairy

    private val dairy: List<FoodSnapshot> = listOf(
        FoodSnapshot(
            id = "whole-milk",
            name = "Whole Milk",
            section = GrocerySection.Dairy,
            category = FoodCategory.Dairy,
            costTier = BudgetTier.Strict,
            costPerServing = 0.35,
            servingDescription = "240 ml",
            servingGrams = 244.0,
            nutrition = NutritionFacts(calories = 149.0, protein = 7.7, carbs = 11.7, fat = 8.0),
            satietyIndex = 65.0,
        ),
        FoodSnapshot(
            id = "cottage-cheese",
            name = "Cottage Cheese, 2%",
            section = GrocerySection.Dairy,
            category = FoodCategory.Dairy,
            costTier = BudgetTier.Strict,
            costPerServing = 0.85,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 122.0, protein = 17.3, carbs = 5.4, fat = 3.3),
            satietyIndex = 82.0,
        ),
        FoodSnapshot(
            id = "cheddar-block",
            name = "Cheddar Cheese, Block",
            section = GrocerySection.Dairy,
            category = FoodCategory.Dairy,
            costTier = BudgetTier.Strict,
            costPerServing = 0.55,
            servingDescription = "28 g",
            servingGrams = 28.0,
            nutrition = NutritionFacts(calories = 113.0, protein = 7.0, carbs = 0.4, fat = 9.3),
            satietyIndex = 50.0,
        ),
        FoodSnapshot(
            id = "skyr",
            name = "Icelandic Skyr",
            section = GrocerySection.Dairy,
            category = FoodCategory.Dairy,
            costTier = BudgetTier.Moderate,
            costPerServing = 1.75,
            servingDescription = "150 g",
            servingGrams = 150.0,
            nutrition = NutritionFacts(calories = 95.0, protein = 17.0, carbs = 5.0, fat = 0.2),
            satietyIndex = 82.0,
        ),
    )

    // Condiments
    //
    // Flagged isSwapCandidate = false: scaling a near-zero-calorie item to
    // macro-match something produces absurd quantities, and swapping it saves
    // pennies at best.

    private val condiments: List<FoodSnapshot> = listOf(
        FoodSnapshot(
            id = "mixed-spices",
            name = "Mixed Herbs & Spices",
            section = GrocerySection.Pantry,
            category = FoodCategory.Condiment,
            costTier = BudgetTier.Strict,
            costPerServing = 0.10,
            servingDescription = "1 tsp",
            servingGrams = 2.0,
            nutrition = NutritionFacts(calories = 6.0, protein = 0.2, carbs = 1.2, fat = 0.1),
            satietyIndex = 0.0,
            isSwapCandidate = false,
        ),
        FoodSnapshot(
            id = "soy-sauce",
            name = "Soy Sauce",
            section = GrocerySection.Pantry,
            category = FoodCategory.Condiment,
            costTier = BudgetTier.Strict,
            costPerServing = 0.08,
            servingDescription = "1 tbsp (16 g)",
            servingGrams = 16.0,
            nutrition = NutritionFacts(calories = 9.0, protein = 1.3, carbs = 0.8, fat = 0.1),
            satietyIndex = 0.0,
            isSwapCandidate = false,
        ),
        FoodSnapshot(
            id = "hot-sauce",
            name = "Hot Sauce",
            section = GrocerySection.Pantry,
            category = FoodCategory.Condiment,
            costTier = BudgetTier.Strict,
            costPerServing = 0.07,
            servingDescription = "1 tbsp (15 g)",
            servingGrams = 15.0,
            nutrition = NutritionFacts(calories = 5.0, protein = 0.2, carbs = 0.9, fat = 0.1),
            satietyIndex = 0.0,
            isSwapCandidate = false,
        ),
    )

    // Dietary annotations
    //
    // Two lookup tables instead of two more arguments in all 57 literals. The
    // declarations above stay about food and macros; the dietary view of the
    // same catalogue is readable, and auditable, in one screen.

    /**
     * Foods that are not plant-only, or that carry a declarable allergen. A
     * missing entry means "plant-only, no declarable allergen", the correct
     * reading for every vegetable, fruit, oil and spice in the list.
     */
    private val traitTable: Map<String, FoodTraits> = with(FoodTraits) {
        mapOf(
            // Protein anchors
            "eggs-large" to EGG,
            "eggs-pasture-organic" to EGG,
            "canned-tuna-water" to FISH,
            "canned-sardines" to FISH,
            "salmon-fillet" to FISH,
            "cod-fillet" to FISH,
            "shrimp" to SHELLFISH,
            "chicken-thighs" to MEAT,
            "chicken-drumsticks" to MEAT,
            "chicken-breast" to MEAT,
            "turkey-breast-deli" to MEAT,
            "ground-beef-80-20" to MEAT + RED_MEAT,
            "ground-beef-93-7" to MEAT + RED_MEAT,
            "sirloin-steak" to MEAT + RED_MEAT,
            "pork-shoulder" to MEAT + RED_MEAT + PORK,
            "firm-tofu" to SOY,
            "whey-isolate" to DAIRY,
            "greek-yogurt-nonfat" to DAIRY,

            // Carbohydrate bases
            "dried-pasta" to GLUTEN,
            "whole-wheat-bread" to GLUTEN,
            "sourdough-bread" to GLUTEN,
            "sprouted-grain-bread" to GLUTEN,
            // Rolled oats carry no gluten of their own. They are frequently milled
            // on shared lines, which is a supply-chain fact this catalogue cannot
            // know, so they are not flagged; the disclaimer covers it.

            // Fat sources
            "peanut-butter" to NUTS,
            "almonds" to NUTS,

            // Dairy
            "whole-milk" to DAIRY,
            "cottage-cheese" to DAIRY,
            "cheddar-block" to DAIRY,
            "skyr" to DAIRY,

            // Condiments
            // Most soy sauce is brewed with wheat, so it is flagged for both. A
            // user avoiding gluten loses a condiment, which is the safe direction.
            "soy-sauce" to SOY + GLUTEN,
        )
    }

    /** Active preparation time in minutes, where the category default is wrong. */
    private val prepTable: Map<String, Int> = mapOf(
        // Proteins
        "whey-isolate" to 1,
        "canned-tuna-water" to 0,
        "canned-sardines" to 0,
        "turkey-breast-deli" to 0,
        "greek-yogurt-nonfat" to 0,
        "eggs-large" to 8,
        "eggs-pasture-organic" to 8,
        "shrimp" to 10,
        "ground-beef-80-20" to 12,
        "ground-beef-93-7" to 12,
        "firm-tofu" to 15,
        "cod-fillet" to 15,
        "sirloin-steak" to 15,
        "salmon-fillet" to 18,
        "chicken-breast" to 20,
        "canned-black-beans" to 0,
        "canned-chickpeas" to 0,
        "chicken-thighs" to 25,
        "chicken-drumsticks" to 30,
        "dried-lentils" to 40,
        "pork-shoulder" to 90,

        // Carbohydrates
        "whole-wheat-bread" to 0,
        "sourdough-bread" to 0,
        "sprouted-grain-bread" to 0,
        "rolled-oats" to 6,
        "dried-pasta" to 12,
        "white-rice" to 20,
        "potatoes" to 25,
        "quinoa" to 25,
        "sweet-potato" to 30,
        "brown-rice" to 35,

        // Vegetables
        "frozen-mixed-vegetables" to 0,
        "frozen-broccoli" to 0,
        "baby-spinach" to 2,
        "bell-pepper" to 5,
        "cabbage" to 8,
        "asparagus" to 8,
        "onion" to 8,
        "carrots" to 10,
        "fresh-broccoli" to 10,

        // Fruit
        "frozen-berries" to 0,
        "banana" to 1,
        "apple" to 1,
        "fresh-blueberries" to 1,

        // Fats
        "avocado" to 2,
    )

    /**
     * Default preparation time by category, for foods absent from prepTable.
     * Oils, spices, dairy and fruit need no work; a protein or a grain does.
     */
    private fun defaultPrepMinutes(category: FoodCategory, section: GrocerySection): Int = when (category) {
        FoodCategory.ProteinAnchor -> 20
        FoodCategory.CarbBase -> 15
        FoodCategory.FatSource -> 0
        FoodCategory.Vegetable -> if (section == GrocerySection.Frozen) 0 else 5
        FoodCategory.Fruit -> 1
        FoodCategory.Dairy -> 0
        FoodCategory.Condiment -> 0
    }

    /** Applies the two tables above to one food declaration. */
    private fun annotated(food: FoodSnapshot): FoodSnapshot = food.annotated(
        traits = traitTable[food.id] ?: FoodTraits.NONE,
        prepMinutes = prepTable[food.id] ?: defaultPrepMinutes(food.category, food.section),
    )

    /** Ids named in the annotation tables, for the catalogue tests to audit. */
    val annotatedIds: Set<String> get() = traitTable.keys + prepTable.keys

    // The catalogue itself. Declared after every table it reads, because Kotlin
    // initialises an object's properties in source order.

    /**
     * Every curated ingredient at the hand-set prices above. Frozen on purpose:
     * the engine tests are worked by hand against these figures, and a monthly
     * price refresh must not move them.
     */
    val reference: List<FoodSnapshot> =
        (proteins + carbohydrates + fats + vegetables + fruits + dairy + condiments).map(::annotated)

    /** What the app uses: [reference] with each price replaced by a published average where one exists. */
    val all: List<FoodSnapshot> = reference.map(PriceTable::priced)

    private val byId: Map<String, FoodSnapshot> = all.associateBy { it.id }
    private val referenceById: Map<String, FoodSnapshot> = reference.associateBy { it.id }

    /** A food at the price the app uses. */
    fun food(id: String): FoodSnapshot? = byId[id]

    /** A food at its hand-set reference price. For tests. */
    fun referenceFood(id: String): FoodSnapshot? = referenceById[id]
}
