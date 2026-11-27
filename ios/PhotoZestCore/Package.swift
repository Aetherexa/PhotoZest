// swift-tools-version: 5.9
import PackageDescription
let package = Package(
    name: "PhotoZestCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "PhotoZestCore", targets: ["PhotoZestCore"])],
    targets: [
        .target(name: "PhotoZestCore"),
        .testTarget(name: "PhotoZestCoreTests", dependencies: ["PhotoZestCore"])
    ]
)
