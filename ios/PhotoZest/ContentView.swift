import SwiftUI
import PhotosUI
import PhotoZestCore

struct ContentView: View {
    @EnvironmentObject private var library: PhotoLibraryService
    @State private var picked: [PhotosPickerItem] = []
    @State private var activeCategory: PhotoCategory? = nil
    @State private var albumName = ""
    @State private var showAlbum = false
    @State private var showError = false
    @State private var errorText = ""
    private let lavender = Color(red: 0.46, green: 0.33, blue: 0.87)
    private let grid = [GridItem(.adaptive(minimum: 110), spacing: 7)]
    private var filtered: [IndexedPhoto] {
        guard let activeCategory else { return library.items }
        return library.items.filter { $0.category == activeCategory }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    hero
                    actions
                    if library.isAnalyzing {
                        ProgressView(value: Double(library.processed),
                                     total: Double(max(library.total, 1)))
                            .tint(lavender)
                        Text("Analyzing \(library.processed) of \(library.total) photos")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Text(library.notice).font(.footnote).foregroundStyle(.secondary)
                    if !library.items.isEmpty {
                        collections
                        gallery
                    } else if !library.isAnalyzing {
                        emptyState
                    }
                    Text("Your photos stay on your device. PhotoZest never moves or deletes originals.")
                        .font(.caption2).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
                .padding(18)
            }
            .background(Color(red: 0.97, green: 0.96, blue: 1.00).ignoresSafeArea())
            .navigationBarHidden(true)
            .safeAreaInset(edge: .bottom) {
                if !library.selectedIDs.isEmpty && PhotoZestFeatureFlags.albumOrganization {
                    HStack {
                        Text("\(library.selectedIDs.count) selected").font(.subheadline).bold()
                        Spacer()
                        Button("Add to album") { showAlbum = true }
                            .buttonStyle(.borderedProminent)
                            .tint(lavender)
                    }
                    .padding(12)
                    .background(.regularMaterial)
                }
            }
        }
        .onChange(of: picked) { _, values in
            Task { await library.scanPickerSelection(values) }
        }
        .alert("Add selected photos to album", isPresented: $showAlbum) {
            TextField("Album name", text: $albumName)
            Button("Cancel", role: .cancel) {}
            Button("Add") {
                Task {
                    do { try await library.addSelectedToAlbum(named: albumName) }
                    catch {
                        errorText = error.localizedDescription
                        showError = true
                    }
                }
            }
        } message: {
            Text("This adds references to an Apple Photos album. Originals aren't moved.")
        }
        .alert("Album update failed", isPresented: $showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorText)
        }
    }

    private var header: some View {
        HStack {
            ZStack {
                RoundedRectangle(cornerRadius: 14).fill(lavender)
                    .frame(width: 48, height: 48)
                Image(systemName: "photo.stack.fill").font(.title2).foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("PhotoZest").font(.title2.weight(.heavy))
                Text("by AETHEREXA").font(.caption2).tracking(1.6).foregroundStyle(.secondary)
            }
            Spacer()
            Label("On-device", systemImage: "lock.shield.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(lavender)
        }
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Your photos.\nBeautifully organized.")
                .font(.largeTitle.weight(.bold)).tracking(-1.2)
            Text("Turn camera roll clutter into memories you can find, select, and cherish.")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(22)
        .background(
            LinearGradient(colors: [Color(red: 0.86, green: 0.81, blue: 1),
                                    Color(red: 0.97, green: 0.95, blue: 1)],
                           startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 23)
        )
    }

    private var actions: some View {
        HStack(spacing: 12) {
            PhotosPicker(selection: $picked,
                         maxSelectionCount: library.capabilities.maximumPhotosPerScan,
                         matching: .images) {
                Label("Choose photos", systemImage: "photo.on.rectangle.angled")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(lavender)
            if PhotoZestFeatureFlags.libraryScan {
            Button {
                Task { await library.scanRecentLibrary() }
            } label: {
                Label("Scan library", systemImage: "square.stack.3d.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(lavender)
            .disabled(library.isAnalyzing)
            }
        }
        .controlSize(.large)
    }

    private var collections: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Smart collections").font(.title3.weight(.bold))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    categoryPill(nil, title: "All", count: library.items.count)
                    ForEach(PhotoCategory.allCases, id: \.self) { category in
                        let count = library.items.filter { $0.category == category }.count
                        if count > 0 {
                            categoryPill(category, title: category.rawValue, count: count)
                        }
                    }
                }
            }
        }
    }

    private func categoryPill(_ category: PhotoCategory?, title: String, count: Int) -> some View {
        Button {
            activeCategory = category
        } label: {
            Text("\(title) · \(count)")
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 15)
                .padding(.vertical, 10)
                .background(activeCategory == category ? lavender : .white,
                            in: Capsule())
                .foregroundStyle(activeCategory == category ? .white : .primary)
        }
    }

    private var gallery: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Photos").font(.title3.weight(.bold))
                Spacer()
                Text("\(filtered.count) found")
                    .font(.caption).foregroundStyle(.secondary)
            }
            LazyVGrid(columns: grid, spacing: 7) {
                ForEach(filtered) { item in
                    Button {
                        library.toggle(item.id)
                    } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(uiImage: item.thumbnail)
                                .resizable()
                                .scaledToFill()
                                .frame(height: 120)
                                .frame(maxWidth: .infinity)
                                .clipped()
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            Image(systemName: library.selectedIDs.contains(item.id)
                                  ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(
                                    library.selectedIDs.contains(item.id) ? lavender : .white)
                                .padding(8)
                            Text(item.category.rawValue)
                                .font(.system(size: 9, weight: .semibold))
                                .lineLimit(1)
                                .padding(5)
                                .background(.regularMaterial, in: Capsule())
                                .frame(maxWidth: .infinity, maxHeight: .infinity,
                                       alignment: .bottomLeading)
                                .padding(5)
                        }
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(item.category.rawValue), tap to select")
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.largeTitle).foregroundStyle(lavender)
            Text("Your memories belong together").font(.headline)
            Text("Choose photos to start private on-device organizing.")
                .font(.footnote).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(32)
        .background(.white, in: RoundedRectangle(cornerRadius: 22))
    }
}
