import XCTest
@testable import PhotoZestCore

final class PhotoPolicyTests: XCTestCase {
    func testFreePlanLimitsAndNoOCR() {
        let p = PhotoEntitlements.capabilities(for: .free)
        XCTAssertTrue(p.accepts(count: 100))
        XCTAssertFalse(p.accepts(count: 101))
        XCTAssertFalse(p.permits(.basicOCR))
        XCTAssertFalse(p.permits(.categorySearch))
    }
    func testPlusAndProEntitlements() {
        let plus = PhotoEntitlements.capabilities(for: .plus)
        let pro = PhotoEntitlements.capabilities(for: .pro)
        XCTAssertTrue(plus.accepts(count: 300))
        XCTAssertFalse(plus.accepts(count: 301))
        XCTAssertTrue(plus.permits(.categorySearch))
        XCTAssertFalse(plus.permits(.basicOCR))
        XCTAssertTrue(pro.permits(.basicOCR))
        XCTAssertTrue(pro.permits(.metadataEditing))
        XCTAssertFalse(pro.permits(.advancedOCR))
        XCTAssertTrue(pro.accepts(count: 500))
    }
    func testMaxCommercialQuotas() {
        let p = PhotoEntitlements.capabilities(for: .max)
        XCTAssertNil(p.maximumPhotosPerScan)
        XCTAssertTrue(p.accepts(count: 20_000))
        XCTAssertTrue(p.permits(.advancedOCR))
        XCTAssertTrue(p.permits(.semanticSearch))
        XCTAssertTrue(p.permits(.multiCategoryShare))
        XCTAssertFalse(p.accepts(count: -1))
    }
    func testGroupWinsOverBackground() {
        XCTAssertEqual(RecognitionPolicy.category(faceCount: 3, largeCenteredFace: false,
            isScreenshot: false, labels: ["food", "portrait"]), .groups)
    }
    func testSelfieRequiresLargeCenteredFace() {
        XCTAssertEqual(RecognitionPolicy.category(faceCount: 1, largeCenteredFace: true,
            isScreenshot: false, labels: []), .selfies)
        XCTAssertEqual(RecognitionPolicy.category(faceCount: 1, largeCenteredFace: false,
            isScreenshot: false, labels: []), .portraits)
    }
    func testAmbiguityAndTokenBoundaries() {
        XCTAssertEqual(RecognitionPolicy.category(faceCount: 0, largeCenteredFace: false,
            isScreenshot: false, labels: ["mountain", "pizza"]), .needsReview)
        XCTAssertEqual(RecognitionPolicy.category(faceCount: 0, largeCenteredFace: false,
            isScreenshot: false, labels: ["research"]), .needsReview)
        XCTAssertEqual(RecognitionPolicy.category(faceCount: 0, largeCenteredFace: false,
            isScreenshot: true, labels: ["beach"]), .screenshots)
        XCTAssertEqual(RecognitionPolicy.category(faceCount: 0, largeCenteredFace: false,
            isScreenshot: false, labels: ["beach"]), .landscapes)
    }
}
