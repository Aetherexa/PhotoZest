import SwiftUI

@main
struct PhotoZestApp: App {
    @StateObject private var library = PhotoLibraryService()
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(library)
                .tint(Color(red: 0.43, green: 0.31, blue: 0.82))
        }
    }
}
