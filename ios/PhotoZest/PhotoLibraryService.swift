import Foundation
import SwiftUI
import Photos
import PhotosUI
import UIKit
import PhotoZestCore

struct IndexedPhoto: Identifiable {
    let id: String
    let thumbnail: UIImage
    let category: PhotoCategory
    let labels: [String]
    let photosAssetIdentifier: String?
    let capturedAt: Date?
}

enum PhotoLibraryIssue: LocalizedError {
    case permission, noSelection, outsideLibrary, invalidAlbum, failedToWrite, featureDisabled
    var errorDescription: String? {
        switch self {
        case .featureDisabled: return "This feature is disabled in this PhotoZest release."
        case .permission: return "Grant Photos access in Settings to organize existing library images."
        case .noSelection: return "Select some images before creating an album."
        case .outsideLibrary: return "Selected photos aren't available for album changes under the current Photos permission."
        case .invalidAlbum: return "Enter an album name between 1 and 60 characters."
        case .failedToWrite: return "Photos couldn't create or update the album. Originals are unchanged."
        }
    }
}

@MainActor
final class PhotoLibraryService: ObservableObject {
    @Published private(set) var items: [IndexedPhoto] = []
    @Published var selectedIDs: Set<String> = []
    @Published private(set) var isAnalyzing = false
    @Published private(set) var processed = 0
    @Published private(set) var total = 0
    @Published var notice = ""
    // No StoreKit 2 verified purchases yet; users cannot unlock paid plans locally.
    let capabilities = PhotoEntitlements.capabilities(for: .free)

    var selected: [IndexedPhoto] { items.filter { selectedIDs.contains($0.id) } }

    func toggle(_ id: String) {
        if selectedIDs.contains(id) { selectedIDs.remove(id) }
        else { selectedIDs.insert(id) }
    }

    private func begin(_ count: Int) {
        items.removeAll()
        selectedIDs.removeAll()
        total = count
        processed = 0
        isAnalyzing = true
        notice = "Processing on this device. Originals will not change."
    }

    private func process(_ image: UIImage, id: String, sourceID: String?,
                         screenshot: Bool, captured: Date?) async {
        let scale = min(1.0, 640.0 / max(image.size.width, image.size.height, 1.0))
        let size = CGSize(width: max(1, image.size.width * scale),
                          height: max(1, image.size.height * scale))
        let small = UIGraphicsImageRenderer(size: size).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        guard let bytes = small.jpegData(compressionQuality: 0.85) else {
            processed += 1
            return
        }
        let vision: VisionEvidence
        if PhotoZestFeatureFlags.onDeviceRecognition {
            vision = await Task.detached(priority: .userInitiated) {
                VisionPhotoAnalyzer.analyze(bytes, enableOCR: false)
            }.value
        } else {
            vision = .init(faceCount: 0, centeredLargeFace: false, labels: [], recognizedText: nil)
        }
        let category = PhotoZestFeatureFlags.onDeviceRecognition ? RecognitionPolicy.category(
            faceCount: vision.faceCount,
            largeCenteredFace: vision.centeredLargeFace,
            isScreenshot: screenshot,
            labels: vision.labels) : .needsReview
        items.append(.init(id: id, thumbnail: small, category: category,
                           labels: vision.labels, photosAssetIdentifier: sourceID,
                           capturedAt: captured))
        processed += 1
    }

    func scanPickerSelection(_ selections: [PhotosPickerItem]) async {
        guard !isAnalyzing else { return }
        let limit = capabilities.maximumPhotosPerScan ?? Int.max
        let selected = Array(selections.prefix(limit))
        begin(selected.count)
        defer { isAnalyzing = false }
        for (index, pick) in selected.enumerated() {
            guard let data = try? await pick.loadTransferable(type: Data.self),
                  let image = UIImage(data: data) else {
                processed += 1
                continue
            }
            await process(image, id: pick.itemIdentifier ?? "selection-\(index)",
                          sourceID: pick.itemIdentifier, screenshot: false, captured: nil)
        }
        notice = "\(items.count) photos classified privately. Originals unchanged."
    }

    func scanRecentLibrary() async {
        guard PhotoZestFeatureFlags.libraryScan else {
            notice = "Library scanning is disabled for this release."
            return
        }
        guard !isAnalyzing else { return }
        let status = await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .readWrite) {
                continuation.resume(returning: $0)
            }
        }
        guard status == .authorized || status == .limited else {
            notice = PhotoLibraryIssue.permission.localizedDescription
            return
        }
        let options = PHFetchOptions()
        options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
        let results = PHAsset.fetchAssets(with: .image, options: options)
        let count = min(results.count, capabilities.maximumPhotosPerScan ?? results.count)
        begin(count)
        defer { isAnalyzing = false }
        for index in 0..<count {
            let asset = results.object(at: index)
            guard let thumbnail = await loadThumbnail(for: asset) else {
                processed += 1
                continue
            }
            await process(thumbnail, id: asset.localIdentifier,
                          sourceID: asset.localIdentifier,
                          screenshot: asset.mediaSubtypes.contains(.photoScreenshot),
                          captured: asset.creationDate)
        }
        notice = "\(items.count) library photos classified locally. Nothing moved or copied."
    }

    private func loadThumbnail(for asset: PHAsset) async -> UIImage? {
        await withCheckedContinuation { continuation in
            let options = PHImageRequestOptions()
            options.deliveryMode = .highQualityFormat
            options.resizeMode = .fast
            options.isNetworkAccessAllowed = false
            let lock = NSLock()
            var finished = false
            PHImageManager.default().requestImage(
                for: asset, targetSize: CGSize(width: 800, height: 800),
                contentMode: .aspectFit, options: options
            ) { image, info in
                if info?[PHImageResultIsDegradedKey] as? Bool == true { return }
                lock.lock()
                defer { lock.unlock() }
                guard !finished else { return }
                finished = true
                continuation.resume(returning: image)
            }
        }
    }

    /// Only modifies album membership of existing, authorized PHAssets.
    /// No asset creation, copy, move, or deletion is requested.
    func addSelectedToAlbum(named raw: String) async throws {
        guard PhotoZestFeatureFlags.albumOrganization else { throw PhotoLibraryIssue.featureDisabled }
        let name = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty && name.count <= 60 else { throw PhotoLibraryIssue.invalidAlbum }
        guard !selected.isEmpty else { throw PhotoLibraryIssue.noSelection }
        var auth = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if auth == .notDetermined {
            auth = await withCheckedContinuation { continuation in
                PHPhotoLibrary.requestAuthorization(for: .readWrite) {
                    continuation.resume(returning: $0)
                }
            }
        }
        guard auth == .authorized || auth == .limited else { throw PhotoLibraryIssue.permission }
        let identifiers = selected.compactMap(\.photosAssetIdentifier)
        guard identifiers.count == selected.count else { throw PhotoLibraryIssue.outsideLibrary }
        let result = PHAsset.fetchAssets(withLocalIdentifiers: identifiers, options: nil)
        guard result.count == selected.count else { throw PhotoLibraryIssue.outsideLibrary }
        let assets = (0..<result.count).map { result.object(at: $0) }
        let album = try await albumNamed(name)
        try await performChanges {
            PHAssetCollectionChangeRequest(for: album)?.addAssets(assets as NSArray)
        }
        let count = selected.count
        selectedIDs.removeAll()
        notice = "Added \(count) existing photos to \(name). No originals moved."
    }

    private func albumNamed(_ name: String) async throws -> PHAssetCollection {
        func findAlbum() -> PHAssetCollection? {
            let albums = PHAssetCollection.fetchAssetCollections(
                with: .album, subtype: .albumRegular, options: nil)
            for i in 0..<albums.count {
                let a = albums.object(at: i)
                if a.localizedTitle == name { return a }
            }
            return nil
        }
        if let existing = findAlbum() { return existing }
        try await performChanges {
            _ = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: name)
        }
        guard let created = findAlbum() else { throw PhotoLibraryIssue.failedToWrite }
        return created
    }

    private func performChanges(_ change: @escaping () -> Void) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges(change) { success, error in
                if success { continuation.resume(returning: ()) }
                else { continuation.resume(throwing: error ?? PhotoLibraryIssue.failedToWrite) }
            }
        }
    }
}
