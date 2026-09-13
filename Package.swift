// swift-tools-version: 5.9
//
//  Package.swift
//  MacroDime
//
//  A SwiftPM package covering the *pure* layer only: `Domain/` and `Engine/`
//  import nothing but Foundation, so they compile and test on any platform
//  Swift runs on — Linux and CI included.
//
//  The rest of the app (`App/`, `Views/`, `ViewModels/`, `Persistence/`) depends
//  on SwiftUI, SwiftData and UIKit, which are Apple-platform-only and closed
//  source. Those are excluded here and build in Xcode, not through this
//  manifest. This split is the practical payoff of keeping the engines free of
//  framework imports: the science and the swap algorithm can be verified
//  without a Mac.
//
//  Usage:
//      swift build
//      swift test
//
//  Xcode users can ignore this file entirely — the app target is built from the
//  Xcode project described in README.md.
//

import PackageDescription

let package = Package(
    name: "MacroDime",
    products: [
        .library(name: "MacroDime", targets: ["MacroDime"])
    ],
    targets: [
        .target(
            name: "MacroDime",
            path: "MacroDime",
            exclude: [
                "App",          // @main, SwiftUI App lifecycle
                "Views",        // SwiftUI
                "ViewModels",   // @Observable + SwiftData ModelContext
                "Persistence"   // SwiftData @Model
            ],
            sources: [
                "Domain",
                "Engine"
            ]
        ),
        .testTarget(
            name: "MacroDimeTests",
            dependencies: ["MacroDime"],
            path: "MacroDimeTests"
        )
    ]
)
