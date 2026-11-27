import Foundation
import UIKit
import Vision
import PhotoZestCore

struct VisionEvidence {
    let faceCount: Int
    let centeredLargeFace: Bool
    let labels: [String]
    let recognizedText: String?
}

/// Native, on-device Vision requests. No image data sent to any server.
enum VisionPhotoAnalyzer {
    static func analyze(_ data: Data, enableOCR: Bool) -> VisionEvidence {
        guard let image = UIImage(data: data), let cgImage = image.cgImage else {
            return .init(faceCount: 0, centeredLargeFace: false, labels: [], recognizedText: nil)
        }
        let faceRequest = VNDetectFaceRectanglesRequest()
        let classifyRequest = VNClassifyImageRequest()
        var requests: [VNRequest] = [faceRequest, classifyRequest]
        let textRequest = VNRecognizeTextRequest()
        if enableOCR {
            textRequest.recognitionLevel = .accurate
            textRequest.usesLanguageCorrection = true
            requests.append(textRequest)
        }
        do { try VNImageRequestHandler(cgImage: cgImage, options: [:]).perform(requests) }
        catch { return .init(faceCount: 0, centeredLargeFace: false, labels: [], recognizedText: nil) }
        let faces = faceRequest.results ?? []
        let centered = faces.count == 1 &&
            faces[0].boundingBox.width >= 0.30 &&
            abs(faces[0].boundingBox.midX - 0.5) <= 0.20 &&
            abs(faces[0].boundingBox.midY - 0.5) <= 0.20
        let labels = (classifyRequest.results ?? [])
            .filter { $0.confidence >= 0.35 }
            .prefix(6)
            .map(\.identifier)
        let ocr = enableOCR ? (textRequest.results ?? [])
            .compactMap { $0.topCandidates(1).first?.string }
            .joined(separator: "\n") : nil
        return .init(faceCount: faces.count, centeredLargeFace: centered,
                     labels: labels, recognizedText: ocr)
    }
}
