// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "MIearnCore",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(name: "MIearnCore", targets: ["MIearnCore"]),
    ],
    targets: [
        .target(name: "MIearnCore"),
        .testTarget(name: "MIearnCoreTests", dependencies: ["MIearnCore"]),
    ]
)
