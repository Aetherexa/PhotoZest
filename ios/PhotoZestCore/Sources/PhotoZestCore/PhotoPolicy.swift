import Foundation

public enum PhotoTier: String, CaseIterable, Codable, Sendable {
    case free, plus, pro, max
}

public enum PhotoFeature: String, CaseIterable, Sendable {
    case categorySearch, exifDetails, metadataEditing, locationSorting, customTags
    case basicOCR, advancedOCR, semanticSearch, multiCategoryShare, advancedVision
}

public struct TierCapabilities: Sendable {
    public let tier: PhotoTier
    /// Nil means no commercial cap; memory and available storage still apply.
    public let maximumPhotosPerScan: Int?
    public let maximumPrimaryCategories: Int?
    public let maximumTagsPerPhoto: Int?
    public let features: Set<PhotoFeature>

    public func permits(_ feature: PhotoFeature) -> Bool { features.contains(feature) }
    public func accepts(count: Int) -> Bool {
        count >= 0 && (maximumPhotosPerScan.map { count <= $0 } ?? true)
    }
}

public enum PhotoEntitlements {
    public static func capabilities(for tier: PhotoTier) -> TierCapabilities {
        let plus: Set<PhotoFeature> = [.categorySearch]
        let pro: Set<PhotoFeature> = plus.union([
            .exifDetails, .metadataEditing, .locationSorting, .customTags,
            .basicOCR, .advancedVision
        ])
        let max: Set<PhotoFeature> = pro.union([
            .advancedOCR, .semanticSearch, .multiCategoryShare
        ])
        switch tier {
        case .free:
            return .init(tier: tier, maximumPhotosPerScan: 100,
                         maximumPrimaryCategories: 5, maximumTagsPerPhoto: 0, features: [])
        case .plus:
            return .init(tier: tier, maximumPhotosPerScan: 300,
                         maximumPrimaryCategories: 7, maximumTagsPerPhoto: 0, features: plus)
        case .pro:
            return .init(tier: tier, maximumPhotosPerScan: 500,
                         maximumPrimaryCategories: 15, maximumTagsPerPhoto: 30, features: pro)
        case .max:
            return .init(tier: tier, maximumPhotosPerScan: nil,
                         maximumPrimaryCategories: nil, maximumTagsPerPhoto: nil, features: max)
        }
    }
}

public enum PhotoCategory: String, CaseIterable, Codable, Sendable {
    case groups = "Group photos"
    case selfies = "Selfies"
    case portraits = "Portraits"
    case landscapes = "Landscapes"
    case screenshots = "Screenshots"
    case needsReview = "Needs review"
}

/// Conservative deterministic policy shared in spirit with Android.
/// Native Apple Vision provides the evidence; ambiguous images must go to review.
public enum RecognitionPolicy {
    public static func category(faceCount: Int, largeCenteredFace: Bool,
                                isScreenshot: Bool, labels: [String]) -> PhotoCategory {
        if isScreenshot { return .screenshots }
        if faceCount >= 2 { return .groups }
        if faceCount == 1 { return largeCenteredFace ? .selfies : .portraits }
        let labelSet = labels.map { $0.lowercased() }
        let nature = ["landscape", "mountain", "beach", "sea", "forest", "sunset", "sky", "shore"]
        let food = ["food", "meal", "restaurant", "pizza", "dish"]
        let hasNature = labelSet.contains { label in
            nature.contains { token in label.split(whereSeparator: { !$0.isLetter })
                .contains { $0 == Substring(token) } }
        }
        let hasFood = labelSet.contains { label in
            food.contains { token in label.split(whereSeparator: { !$0.isLetter })
                .contains { $0 == Substring(token) } }
        }
        return hasNature && !hasFood ? .landscapes : .needsReview
    }
}
