package com.aetherexa.photoflow

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.ClipData
import android.content.Context
import android.widget.Toast
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.app.Activity
import com.aetherexa.photoflow.data.FolderLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aetherexa.photoflow.core.Category
import com.aetherexa.photoflow.core.PhotoZestFeatureFlags
import com.aetherexa.photoflow.core.ResultsUiPolicy
import com.aetherexa.photoflow.core.SmartTag
import com.aetherexa.photoflow.core.PaymentProvider
import com.aetherexa.photoflow.core.PaymentDirection
import com.aetherexa.photoflow.core.PaymentStatus
import com.aetherexa.photoflow.core.PaymentInsightParser
import com.aetherexa.photoflow.data.PhotoItem
import com.aetherexa.photoflow.data.Thumbnails
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF172137)
private val Dark = Color(0xFF11152B)
private val Background = Color(0xFFF5F6FA)
private val CardWhite = Color.White
private val Lavender = Color(0xFF785CF0)
private val LightLavender = Color(0xFFECE8FF)
private val Mint = Color(0xFF8CF0D0)
private val Muted = Color(0xFF6A7284)
private val Stroke = Color(0xFFE7EAF0)
private val Warm = Color(0xFFFFE6C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(245, 246, 250)
        window.navigationBarColor = android.graphics.Color.rgb(245, 246, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Lavender, onPrimary = Color.White, background = Background,
                surface = CardWhite, onSurface = Ink, onBackground = Ink
            )) { PhotoFlowScreen() }
        }
    }
}

@Composable
private fun HomeShortcut(icon: String, title: String, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(91.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.fillMaxSize().padding(13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(icon, color = Lavender, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoFlowScreen(vm: PhotoFlowViewModel = viewModel()) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("All") }
    var placeFilter by rememberSaveable { mutableStateOf("All places") }
    var momentFilter by rememberSaveable { mutableStateOf("All times") }
    var monthFilter by rememberSaveable { mutableStateOf("All months") }
    var searchText by rememberSaveable { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var destinationUri by remember { mutableStateOf<Uri?>(null) }
    var destinationName by remember { mutableStateOf("") }
    var pendingSelection by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    var showGpsPermissionExplanation by remember { mutableStateOf(false) }
    var paymentProviderFilter by remember { mutableStateOf<PaymentProvider?>(null) }
    var paymentDirectionFilter by remember { mutableStateOf<PaymentDirection?>(null) }
    var confirmFinancialShare by remember { mutableStateOf(false) }
    var photoSelection by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var organizeLayout by remember { mutableStateOf(FolderLayout.CATEGORY_FOLDERS) }
    var collectionName by remember { mutableStateOf("") }
    var pendingOrganizeKeys by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    val beginScan: (Uri, String) -> Unit = { uri, name ->
        photoSelection = emptySet()
        selectionMode = false
        showFilters = false
        filter = "All"
        placeFilter = "All places"
        momentFilter = "All times"
        monthFilter = "All months"
        searchText = ""
        selectedTags = emptySet()
        paymentProviderFilter = null
        paymentDirectionFilter = null
        vm.scan(uri, name)
    }
    val beginGallery: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty() && !vm.isBusy) {
            photoSelection = emptySet()
            selectionMode = false
            showFilters = false
            filter = "All"
            searchText = ""
            selectedTags = emptySet()
            placeFilter = "All places"
            momentFilter = "All times"
            monthFilter = "All months"
            paymentProviderFilter = null
            paymentDirectionFilter = null
            vm.scanFromGallery(uris)
        }
    }
    val selectFromGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris -> beginGallery(uris) }
    val selectFromInstalledGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clips ->
                for (index in 0 until clips.itemCount) uris.add(clips.getItemAt(index).uri)
            }
            beginGallery(uris)
        }
    }
    val askGpsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // If denied, still scan. Android might hide GPS from photo streams; do not guess places.
        pendingSelection?.let { (uri, name) -> beginScan(uri, name) }
        pendingSelection = null
    }

    val chooseInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* A read-only provider can still be scanned. */ }
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Selected folder"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                pendingSelection = uri to name
                showGpsPermissionExplanation = true
            } else beginScan(uri, name)
        }
    }
    val grantOriginalFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* Verification will fail safely if provider is read-only. */ }
            vm.linkGalleryOriginals(uri,
                DocumentFile.fromTreeUri(context, uri)?.name ?: "Gallery originals")
        }
    }
    val chooseDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* The move will validate permissions. */ }
            destinationName = DocumentFile.fromTreeUri(context, uri)?.name ?: "Selected destination"
            destinationUri = uri
        }
    }

    val all = vm.photos.toList()
    val waiting = all.filterNot { it.moved }
    val places = remember(all) { all.groupingBy { it.resolvedCity }.eachCount().toSortedMap() }
    val moments = remember(all) { all.mapNotNull { it.metadata.timeOfDay }.toSet() }
    val months = remember(all) { all.mapNotNull { it.metadata.month }.distinct().sortedDescending() }
    val suggestedTags = remember(all) {
        val excluded = Category.entries.map { it.display }.toSet() +
            SmartTag.entries.map { it.display }.toSet() + places.keys + moments + months
        all.flatMap { it.searchableTags }
            .filter { it !in excluded && it != "GPS recorded" && it != "Date unknown" &&
                !Regex("\\d{4}(-\\d{2}(-\\d{2})?)?").matches(it) }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(14)
    }
    val receiptPhotos = all.filter { it.payment != null }
    val expenses = receiptPhotos.filter { SmartTag.MAYBE_DUPLICATE !in it.tags }
    val estimatedExpense = expenses.sumOf { it.payment?.estimatedExpensePaise ?: 0L }
    val paymentApps = receiptPhotos.mapNotNull { it.payment?.provider }.distinct()
    val shown = remember(all, filter, placeFilter, momentFilter, monthFilter,
        searchText, selectedTags, paymentProviderFilter, paymentDirectionFilter) {
        all.filter { photo ->
            val typeMatches = when {
                filter == "All" -> true
                Category.entries.any { it.display == filter } -> photo.category.display == filter
                else -> SmartTag.entries.any { it.display == filter && it in photo.tags }
            }
            val placeMatches = placeFilter == "All places" || photo.resolvedCity == placeFilter
            val momentMatches = momentFilter == "All times" || photo.metadata.timeOfDay == momentFilter
            val monthMatches = monthFilter == "All months" || photo.metadata.month == monthFilter
            val tagMatches = selectedTags.all { it in photo.searchableTags }
            val term = searchText.trim()
            val textMatches = term.isEmpty() ||
                photo.source.name.contains(term, ignoreCase = true) ||
                photo.labels.contains(term, ignoreCase = true) ||
                photo.searchableTags.any { it.contains(term, ignoreCase = true) }
            val appMatches = paymentProviderFilter == null || photo.payment?.provider == paymentProviderFilter
            val directionMatches = paymentDirectionFilter == null || photo.payment?.direction == paymentDirectionFilter
            typeMatches && placeMatches && momentMatches && monthMatches && tagMatches &&
                textMatches && appMatches && directionMatches
        }
    }
    // Action targets must always be explicitly selected; filtering alone never selects files.
    val actionPhotos = if (!ResultsUiPolicy.canActOnSelection(selectionMode, photoSelection.isNotEmpty())) emptyList()
        else shown.filter { it.originUri in photoSelection }
    val actionable = actionPhotos.filterNot { it.moved }
    val verifiedForMove = actionable.filter { it.source.parentUri != Uri.EMPTY }
    val unverifiedForMove = actionable.size - verifiedForMove.size
    val flagged = all.count { SmartTag.MAYBE_BLURRED in it.tags ||
        SmartTag.MAYBE_DUPLICATE in it.tags || it.payment?.needsReview == true }
    val total = if (vm.phase == WorkflowPhase.MOVING) waiting.size + vm.movedCount else vm.selectedCount
    val progress = if (vm.phase == WorkflowPhase.MOVING) {
        (vm.processedCount.toFloat() / (vm.processedCount + waiting.size).coerceAtLeast(1))
    } else (vm.processedCount.toFloat() / total.coerceAtLeast(1))
    val animatedProgress by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (all.isNotEmpty() && !vm.isBusy && selectionMode) {
                Column(Modifier.fillMaxWidth().background(CardWhite)
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(onClick = {
                            if (actionPhotos.any { it.payment != null }) confirmFinancialShare = true
                            else sharePhotos(context, actionPhotos)
                        }, enabled = actionPhotos.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LightLavender,
                                contentColor = Ink)) {
                            Text("Share", fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = {
                            pendingOrganizeKeys = actionable.map { it.originUri }.toSet()
                            if (vm.inputKind == InputKind.GALLERY && vm.sourceUri == null)
                                grantOriginalFolder.launch(null)
                            else chooseDestination.launch(null)
                        }, enabled = actionable.isNotEmpty() && PhotoZestFeatureFlags.originalFolderMoves,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Lavender)) {
                            Text(if (PhotoZestFeatureFlags.originalFolderMoves) "Create folders" else "Moves unavailable", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    if (actionPhotos.isEmpty()) {
                        Text("Tap photos to select them", fontSize = 11.sp, color = Muted,
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (all.isNotEmpty()) 3 else 2),
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            contentPadding = PaddingValues(
                start = if (all.isNotEmpty()) 5.dp else 19.dp,
                end = if (all.isNotEmpty()) 5.dp else 19.dp,
                top = if (all.isNotEmpty()) 9.dp else 21.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (all.isNotEmpty()) 3.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (all.isNotEmpty()) 3.dp else 13.dp)
        ) {
            if (all.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                            .background(Ink), contentAlignment = Alignment.Center) {
                            Text("✦", color = Mint, fontSize = 26.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("PhotoZest", fontSize = 25.sp, fontWeight = FontWeight.Black,
                                color = Ink, letterSpacing = (-0.9).sp)
                            Text("YOUR MEMORIES, REIMAGINED", fontSize = 8.sp,
                                color = Muted, letterSpacing = 1.5.sp)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
                        .background(CardWhite).border(1.dp, Stroke, RoundedCornerShape(23.dp))
                        .clickable(enabled = !vm.isBusy) {
                            selectFromGallery.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }.padding(horizontal = 21.dp, vertical = 18.dp)) {
                        Text("⌕   Search photos, places, payments or tags", color = Muted, fontSize = 13.sp)
                    }
                }
                item {
                    HomeShortcut("▧", "Gallery", !vm.isBusy) {
                        selectFromGallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                item {
                    HomeShortcut("▣", "Folder", !vm.isBusy) { chooseInput.launch(null) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFEEE8FF), Color(0xFFF7F4FF))))
                        .padding(19.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(0.95f)) {
                                Text("Start with\nyour photos", color = Ink,
                                    fontSize = 26.sp, fontWeight = FontWeight.Black,
                                    lineHeight = 29.sp, letterSpacing = (-0.8).sp)
                                Spacer(Modifier.height(12.dp))
                                Text("Choose photos or a folder. PhotoZest organizes them for you.",
                                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                            Image(painter = androidx.compose.ui.res.painterResource(
                                id = R.drawable.photoflow_collage), contentDescription = null,
                                modifier = Modifier.weight(1.1f).height(180.dp),
                                contentScale = ContentScale.Fit)
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { selectFromGallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !vm.isBusy, modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Lavender)) {
                            Text("▧  Choose from Gallery", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { chooseInput.launch(null) }, enabled = !vm.isBusy,
                            modifier = Modifier.fillMaxWidth().height(49.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CardWhite,
                                contentColor = Ink)) {
                            Text("▣  Choose Folder", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Smart collections", color = Ink, fontWeight = FontWeight.Bold,
                            fontSize = 21.sp, modifier = Modifier.weight(1f))
                        Text("Ready when you are", color = Lavender, fontSize = 11.sp)
                    }
                }
                item {
                    HomeShortcut("✦", "AI sorting", !vm.isBusy) {
                        selectFromGallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                item {
                    HomeShortcut("▤", "Receipts", !vm.isBusy) {
                        selectFromGallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Private image recognition", modifier = Modifier.weight(1f),
                                    color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Switch(vm.useOnDeviceAI && PhotoZestFeatureFlags.onDeviceRecognition, { vm.useOnDeviceAI = it && PhotoZestFeatureFlags.onDeviceRecognition }, enabled = !vm.isBusy && PhotoZestFeatureFlags.onDeviceRecognition,
                                    colors = SwitchDefaults.colors(checkedTrackColor = Lavender))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Screenshot & payment OCR", modifier = Modifier.weight(1f),
                                    color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Switch(vm.useScreenshotOCR && PhotoZestFeatureFlags.screenshotOcr, { vm.useScreenshotOCR = it && PhotoZestFeatureFlags.screenshotOcr }, enabled = !vm.isBusy && PhotoZestFeatureFlags.screenshotOcr,
                                    colors = SwitchDefaults.colors(checkedTrackColor = Lavender))
                            }
                            Text("Originals stay on your device. Moving photos always requires confirmation.",
                                fontSize = 10.sp, lineHeight = 14.sp, color = Muted)
                        }
                    }
                }
                if (vm.phase == WorkflowPhase.ERROR || vm.isBusy) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(vm.message, fontSize = 12.sp, color = Muted)
                            if (vm.isBusy) LinearProgressIndicator(progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth(), color = Lavender)
                        }
                    }
                }
            }
            if (all.isNotEmpty()) {
                // One compact top bar replaces the onboarding dashboard after a scan.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("PhotoZest", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold,
                            color = Ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { chooseInput.launch(null) }, enabled = !vm.isBusy) {
                            Text("Folder", color = Muted, fontSize = 12.sp)
                        }
                        Button(onClick = { selectFromGallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        ) }, enabled = !vm.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = LightLavender,
                                contentColor = Lavender), shape = CircleShape) {
                            Text("+  Photos", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                if (vm.isBusy || vm.moveFailures.isNotEmpty() || vm.movedCount > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text(vm.message, fontSize = 12.sp, color = Muted)
                            if (vm.isBusy) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                                    color = Lavender, trackColor = LightLavender)
                            }
                            if (vm.moveFailures.isNotEmpty())
                                Text("${vm.moveFailures.size} photos could not be moved; originals are unchanged.",
                                    fontSize = 12.sp, color = Color(0xFFB04B16))
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = searchText, onValueChange = { searchText = it.take(80) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp),
                        singleLine = true,
                        placeholder = { Text("Search your photos", color = Muted, fontSize = 14.sp) },
                        leadingIcon = { Text("⌕", fontSize = 26.sp, color = Lavender) },
                        trailingIcon = {
                            if (searchText.isNotEmpty()) TextButton(onClick = { searchText = "" }) {
                                Text("×", color = Muted, fontSize = 21.sp)
                            }
                        },
                        shape = RoundedCornerShape(26.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lavender, unfocusedBorderColor = Stroke,
                            focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite)
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val quickFilters = ResultsUiPolicy.categoryShortcuts(all.map { it.category })
                    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        items(quickFilters.size) { index ->
                            val name = quickFilters[index]
                            FilterChip(selected = filter == name,
                                onClick = { filter = name; photoSelection = emptySet() },
                                label = { Text(name, fontSize = 12.sp) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LightLavender,
                                    selectedLabelColor = Lavender,
                                    containerColor = Color.White, labelColor = Ink),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true, selected = filter == name,
                                    borderColor = Stroke, selectedBorderColor = Lavender))
                        }
                        item {
                            val active = (if (placeFilter != "All places") 1 else 0) +
                                (if (momentFilter != "All times") 1 else 0) +
                                (if (monthFilter != "All months") 1 else 0) +
                                (if (paymentProviderFilter != null) 1 else 0) +
                                (if (paymentDirectionFilter != null) 1 else 0) + selectedTags.size
                            FilterChip(selected = active > 0, onClick = { showFilters = true },
                                label = { Text("☷  Filters${if (active > 0) " · $active" else ""}",
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LightLavender, selectedLabelColor = Lavender))
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 7.dp, top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (selectionMode) "${actionPhotos.size} selected" else "${shown.size} photos",
                                fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
                            if (selectionMode) Text("Select the originals you want to act on",
                                fontSize = 11.sp, color = Muted)
                        }
                        if (selectionMode && shown.isNotEmpty()) {
                            TextButton(onClick = {
                                photoSelection = if (actionPhotos.size == shown.size) emptySet()
                                    else shown.map { it.originUri }.toSet()
                            }) { Text(if (actionPhotos.size == shown.size) "Clear" else "Select all",
                                color = Lavender, fontSize = 12.sp) }
                        }
                        TextButton(onClick = {
                            selectionMode = !selectionMode
                            photoSelection = emptySet()
                        }) { Text(if (selectionMode) "Done" else "Select",
                            color = Lavender, fontWeight = FontWeight.Bold) }
                    }
                }
                // Month dividers retain chronology without putting EXIF text under every photo.
                val groupedResults = shown.sortedWith(compareByDescending<PhotoItem> { it.metadata.date ?: "" }
                    .thenBy { it.source.name }).groupBy { it.metadata.month ?: "Unknown date" }
                groupedResults.forEach { (month, group) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(prettyMonth(month), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = Muted, modifier = Modifier.padding(start = 13.dp, top = 11.dp, bottom = 5.dp))
                    }
                    items(group, key = { it.source.uri.toString() }) { photo ->
                        PhotoTile(photo = photo,
                            isSelecting = selectionMode,
                            isSelected = photo.originUri in photoSelection,
                            onSelect = {
                                if (!selectionMode) selectionMode = true
                                photoSelection = if (photo.originUri in photoSelection)
                                    photoSelection - photo.originUri else photoSelection + photo.originUri
                            },
                            onClick = { selectedUri = photo.source.uri })
                    }
                }
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 45.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No matching photos", fontSize = 18.sp,
                                fontWeight = FontWeight.Bold, color = Ink)
                            Text("Try a different search or remove filters", fontSize = 12.sp, color = Muted)
                            TextButton(onClick = {
                                filter = "All"; placeFilter = "All places"
                                momentFilter = "All times"; monthFilter = "All months"
                                searchText = ""; selectedTags = emptySet()
                                paymentProviderFilter = null; paymentDirectionFilter = null
                                photoSelection = emptySet()
                            }) { Text("Clear search and filters", color = Lavender) }
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(24.dp)).background(LightLavender),
                            contentAlignment = Alignment.Center) {
                            Text("▧", fontSize = 34.sp, color = Lavender)
                        }
                        Spacer(Modifier.height(15.dp))
                        Text("Your next adventure starts here", fontSize = 17.sp,
                            fontWeight = FontWeight.Bold, color = Ink)
                        Spacer(Modifier.height(7.dp))
                        Text("Choose photos from your Gallery, or pick a whole album folder.",
                            textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("BUILT WITH CARE BY AETHEREXA · PHOTOS STAY ON YOUR DEVICE",
                    color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false },
            containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Refine photos", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        filter = "All"; placeFilter = "All places"; momentFilter = "All times"
                        monthFilter = "All months"; selectedTags = emptySet()
                        paymentProviderFilter = null; paymentDirectionFilter = null
                        photoSelection = emptySet()
                    }) { Text("Reset", color = Lavender) }
                }
                Spacer(Modifier.height(14.dp))
                Text("Places", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                Text("From photo GPS or a location you set", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(5.dp))
                val placeNames = listOf("All places") + places.keys.toList()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(placeNames.size) { idx ->
                        val name = placeNames[idx]
                        FilterChip(selected = placeFilter == name, onClick = {
                            placeFilter = name; photoSelection = emptySet()
                        }, label = { Text(name, fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Time of day", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                val timeNames = listOf("All times") + listOf(
                    "Early morning", "Morning", "Afternoon", "Evening", "Night"
                ).filter { it in moments }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(timeNames.size) { idx ->
                        val name = timeNames[idx]
                        FilterChip(selected = momentFilter == name, onClick = {
                            momentFilter = name; photoSelection = emptySet()
                        }, label = { Text(name, fontSize = 12.sp) })
                    }
                }
                if (months.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Month", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                    val monthNames = listOf("All months") + months
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(monthNames.size) { idx ->
                            val name = monthNames[idx]
                            FilterChip(selected = monthFilter == name, onClick = {
                                monthFilter = name; photoSelection = emptySet()
                            }, label = { Text(if (name == "All months") name else prettyMonth(name),
                                fontSize = 12.sp) })
                        }
                    }
                }
                if (suggestedTags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Smart tags", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                    Text("Select more than one to combine", color = Muted, fontSize = 11.sp)
                    suggestedTags.take(12).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            row.forEach { (name, count) ->
                                FilterChip(selected = name in selectedTags, onClick = {
                                    selectedTags = if (name in selectedTags) selectedTags - name
                                        else selectedTags + name
                                    photoSelection = emptySet()
                                }, label = { Text("$name · $count", fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis) })
                            }
                        }
                    }
                }
                if (receiptPhotos.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Payments", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val apps = listOf<PaymentProvider?>(null) + paymentApps
                        items(apps.size) { idx ->
                            val app = apps[idx]
                            FilterChip(selected = paymentProviderFilter == app,
                                onClick = { paymentProviderFilter = app; photoSelection = emptySet() },
                                label = { Text(app?.label ?: "All apps", fontSize = 12.sp) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val directions = listOf<PaymentDirection?>(null) + PaymentDirection.entries
                        items(directions.size) { idx ->
                            val direction = directions[idx]
                            FilterChip(selected = paymentDirectionFilter == direction,
                                onClick = { paymentDirectionFilter = direction; photoSelection = emptySet() },
                                label = { Text(direction?.label ?: "All directions", fontSize = 12.sp) })
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = { showFilters = false }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender)) {
                    Text("Show ${shown.size} photos", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(25.dp))
            }
        }
    }

    if (confirmFinancialShare) {
        AlertDialog(
            onDismissRequest = { confirmFinancialShare = false },
            title = { Text("Share sensitive screenshots?", color = Ink,
                fontWeight = FontWeight.Bold) },
            text = { Text("Your selection contains payment screenshots. Recipients may see names, transaction references, UPI IDs and financial amounts in the original images. PhotoZest cannot redact those when sharing originals.",
                fontSize = 13.sp, color = Ink) },
            confirmButton = { Button(onClick = {
                confirmFinancialShare = false
                sharePhotos(context, actionPhotos)
            }) { Text("Share originals") } },
            dismissButton = { TextButton(onClick = { confirmFinancialShare = false }) {
                Text("Cancel") } }
        )
    }
    if (showGpsPermissionExplanation) {
        AlertDialog(
            onDismissRequest = {
                showGpsPermissionExplanation = false
                pendingSelection?.let { (uri, name) -> beginScan(uri, name) }
                pendingSelection = null
            },
            title = { Text("Enable location tags?", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("PhotoZest needs optional access to the GPS data already saved in your photos. " +
                "It does not access your current location or send coordinates online. " +
                "You can continue without this permission, but some cities may show as unknown.",
                color = Muted, fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = {
                    showGpsPermissionExplanation = false
                    askGpsPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }, colors = ButtonDefaults.buttonColors(containerColor = Lavender)) {
                    Text("Allow photo GPS")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGpsPermissionExplanation = false
                    pendingSelection?.let { (uri, name) -> beginScan(uri, name) }
                    pendingSelection = null
                }) { Text("Skip", color = Muted) }
            },
            containerColor = CardWhite
        )
    }

    selectedUri?.let { uri ->
        all.firstOrNull { it.source.uri == uri }?.let { photo ->
            ReviewDialog(photo, onDismiss = { selectedUri = null },
                onCategory = { vm.changeCategory(uri, it) }, onTag = { vm.toggleTag(uri, it) },
                onAddTag = { vm.addManualTag(uri, it) },
                onRemoveTag = { vm.removeManualTag(uri, it) },
                onCity = { vm.setManualCity(uri, it) })
        }
    }
    destinationUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { destinationUri = null },
            title = { Text("Ready to move originals?", fontWeight = FontWeight.Black, color = Ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Destination: $destinationName", fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = collectionName,
                        onValueChange = { collectionName = it.take(58) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New collection name (optional)") },
                        placeholder = { Text("Weekend Trip") },
                        singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Text("How should these originals be organized?", fontSize = 12.sp, color = Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = organizeLayout == FolderLayout.CATEGORY_FOLDERS,
                            onClick = { organizeLayout = FolderLayout.CATEGORY_FOLDERS },
                            label = { Text("Category folders", fontSize = 11.sp) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = organizeLayout == FolderLayout.ONE_COLLECTION,
                            onClick = { organizeLayout = FolderLayout.ONE_COLLECTION },
                            label = { Text("One collection", fontSize = 11.sp) })
                    }
                    Text(if (organizeLayout == FolderLayout.CATEGORY_FOLDERS)
                            "A new trip folder containing the following category folders:"
                        else "A single new folder holding these selected originals:",
                        fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(10.dp))
                    Category.entries.forEach { category ->
                        val count = verifiedForMove.count { it.category == category }
                        if (count > 0 && organizeLayout == FolderLayout.CATEGORY_FOLDERS) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("▣  ${category.folder}", color = Ink, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Text("$count", color = Lavender, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    HorizontalDivider(color = Stroke, modifier = Modifier.padding(vertical = 11.dp))
                    if (unverifiedForMove > 0) Text("$unverifiedForMove photos can't be moved yet: their source folder wasn't verified. Those photos will not be touched.",
                        color = Color(0xFFB04B16), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("This MOVES ${verifiedForMove.size} verified originals out of their current locations. " +
                            "No copies or ZIP files will be created. This action cannot be automatically undone.",
                        color = Ink, fontSize = 12.sp, lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                    Text("Choose a writable destination on the same device storage volume. " +
                            "Providers that cannot move files natively will leave them untouched.",
                        color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    destinationUri = null
                    vm.moveToFolder(uri, pendingOrganizeKeys, organizeLayout, collectionName)
                }, enabled = verifiedForMove.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender)) {
                    Text("Move ${verifiedForMove.size} originals")
                }
            },
            dismissButton = { TextButton(onClick = { destinationUri = null }) { Text("Not yet", color = Muted) } },
            containerColor = Color.White
        )
    }
}

@Composable
private fun MetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = CardWhite),
        shape = RoundedCornerShape(17.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(value, color = Ink, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun PhotoThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        value = Thumbnails.get(resolver, uri)
    }
    if (bitmap == null) {
        Box(modifier.background(LightLavender), contentAlignment = Alignment.Center) {
            Text("✦", color = Lavender, fontSize = 24.sp)
        }
    } else {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null,
            modifier = modifier.background(LightLavender), contentScale = ContentScale.Crop)
    }
}

/** Photos-first tile: metadata is revealed on tap, selection appears only on request. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(photo: PhotoItem, isSelecting: Boolean, isSelected: Boolean,
                      onSelect: () -> Unit, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(5.dp))
        .combinedClickable(onClick = {
            if (isSelecting) onSelect() else onClick()
        }, onLongClick = { onSelect() })) {
        PhotoThumbnail(photo.source.uri, Modifier.fillMaxSize())
        if (isSelecting) {
            Box(Modifier.align(Alignment.TopStart).padding(7.dp).size(26.dp)
                .clip(CircleShape).background(if (isSelected) Lavender
                    else Color.Black.copy(alpha = 0.28f))
                .border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                if (isSelected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp)
            }
        }
        if (photo.moved) {
            Text("✓", modifier = Modifier.align(Alignment.BottomEnd)
                .padding(5.dp).clip(CircleShape).background(Mint)
                .padding(horizontal = 5.dp, vertical = 2.dp),
                color = Ink, fontSize = 10.sp)
        } else if (photo.payment?.needsReview == true ||
            SmartTag.MAYBE_BLURRED in photo.tags || SmartTag.MAYBE_DUPLICATE in photo.tags) {
            Text("!", modifier = Modifier.align(Alignment.BottomEnd)
                .padding(5.dp).clip(CircleShape).background(Warm)
                .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun prettyMonth(month: String): String = try {
    YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
} catch (_: Exception) { month }

@Composable
private fun PhotoCard(photo: PhotoItem, modifier: Modifier = Modifier,
                      isSelected: Boolean, onSelect: () -> Unit, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardWhite), shape = RoundedCornerShape(18.dp)) {
        Column {
            Box {
                PhotoThumbnail(photo.source.uri, Modifier.fillMaxWidth().height(158.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)))
                Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(32.dp)
                    .clip(CircleShape).background(if (isSelected) Lavender else Color.White.copy(alpha = 0.9f))
                    .clickable(onClick = onSelect), contentAlignment = Alignment.Center) {
                    Text(if (isSelected) "✓" else "○", color = if (isSelected) Color.White else Ink,
                        fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                if (photo.moved) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape)
                        .background(Mint).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("✓ MOVED", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (photo.tags.any { it == SmartTag.MAYBE_BLURRED || it == SmartTag.MAYBE_DUPLICATE }) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape)
                        .background(Warm).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("REVIEW", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(photo.source.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("●  ${photo.category.display}", color = Lavender,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (photo.payment != null) {
                    val payment = photo.payment
                    Text("${payment.provider.label} · ${payment.amountPaise?.let(PaymentInsightParser::formatAmount) ?: "Amount unknown"}",
                        fontSize = 11.sp, color = Ink, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${payment.direction.label} · ${payment.payee ?: "Payee unknown"} · ${payment.status.label}",
                        fontSize = 10.sp, color = Muted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                } else {
                    Text("${photo.resolvedCity} · ${photo.metadata.timeOfDay ?: "Time unknown"}",
                        fontSize = 10.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${photo.metadata.date ?: "Date unknown"}  ·  ${photo.width} × ${photo.height}",
                        fontSize = 10.sp, color = Muted)
                }
                Text(photo.searchableTags.filterNot { it == photo.category.display || it == photo.resolvedCity }
                    .take(3).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 9.sp, color = Lavender)
            }
        }
    }
}

@Composable
private fun ReviewDialog(photo: PhotoItem, onDismiss: () -> Unit,
                         onCategory: (Category) -> Unit, onTag: (SmartTag) -> Unit,
                         onAddTag: (String) -> Unit, onRemoveTag: (String) -> Unit,
                         onCity: (String) -> Unit) {
    var newTag by remember(photo.source.uri) { mutableStateOf("") }
    var editedCity by remember(photo.source.uri) { mutableStateOf(photo.manualCity ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fine-tune this photo", color = Ink, fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                PhotoThumbnail(photo.source.uri,
                    Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(13.dp)))
                Spacer(Modifier.height(13.dp))
                Text(photo.source.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(3.dp))
                Text(photo.reason, fontSize = 12.sp, color = Muted)
                if (photo.moved) {
                    Spacer(Modifier.height(9.dp))
                    Text("This original has already been moved. Category edits do not move it again.",
                        color = Muted, fontSize = 11.sp)
                }
                if (photo.payment != null) {
                    val info = photo.payment
                    Spacer(Modifier.height(10.dp))
                    Text("PAYMENT OCR · NOT BANK-VERIFIED", fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, color = Lavender)
                    Text("App: ${info.provider.label}", fontSize = 12.sp, color = Ink)
                    Text("Amount: ${info.amountPaise?.let(PaymentInsightParser::formatAmount) ?: "Unknown"}",
                        fontSize = 12.sp, color = Ink)
                    Text("Payee: ${info.payee ?: "Not found"}", fontSize = 12.sp, color = Ink)
                    Text("Direction: ${info.direction.label} · Status: ${info.status.label}",
                        fontSize = 12.sp, color = Ink)
                    info.maskedUpiId?.let { Text("UPI (masked): $it", fontSize = 11.sp, color = Muted) }
                    info.referenceLast4?.let { Text("Reference ends: $it", fontSize = 11.sp, color = Muted) }
                    if (info.needsReview) Text("⚠ Review before relying on this payment.",
                        fontSize = 12.sp, color = Color(0xFF926012))
                    Text("Raw OCR text is not saved. The original screenshot contains its own sensitive data.",
                        fontSize = 10.sp, color = Muted)
                }
                if (photo.labels.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("AI detected: ${photo.labels}", fontSize = 11.sp, color = Muted)
                }
                Spacer(Modifier.height(18.dp))
                Text("DESTINATION CATEGORY", color = Lavender, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Category.entries.forEach { category ->
                    Row(Modifier.fillMaxWidth()
                        .clickable(enabled = !photo.moved) { onCategory(category) }
                        .padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).border(1.5.dp, Lavender, CircleShape)
                            .padding(4.dp).clip(CircleShape)
                            .background(if (photo.category == category) Lavender else Color.Transparent))
                        Spacer(Modifier.width(11.dp))
                        Text(category.display, color = Ink, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = Stroke, modifier = Modifier.padding(vertical = 12.dp))
                Text("PHOTO ATTRIBUTES · EXIF", color = Lavender, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(7.dp))
                Text("Captured: ${photo.metadata.capturedAt ?: "Unknown"} " +
                    "${photo.metadata.timeSource?.let { "($it)" } ?: ""}",
                    fontSize = 12.sp, color = Ink)
                Text("Time of day: ${photo.metadata.timeOfDay ?: "Unknown"}", fontSize = 12.sp, color = Ink)
                Text("Place: ${photo.resolvedCity}" + if (photo.metadata.hasGps) " (GPS approximate)" else " (GPS missing, hidden, or off-list)",
                    fontSize = 12.sp, color = Ink)
                if (photo.metadata.hasGps) Text("GPS: %.4f, %.4f".format(
                    photo.metadata.latitude, photo.metadata.longitude), fontSize = 11.sp, color = Muted)
                if (photo.metadata.cameraModel != null) Text("Camera: ${photo.metadata.cameraModel}", fontSize = 12.sp, color = Ink)
                val settings = listOfNotNull(
                    photo.metadata.iso?.let { "ISO $it" },
                    photo.metadata.aperture?.let { "f/$it" },
                    photo.metadata.exposureSeconds?.let { "${it}s" },
                    photo.metadata.focalLength?.let { "$it mm" }
                )
                if (settings.isNotEmpty()) Text(settings.joinToString(" · "), fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(value = editedCity, onValueChange = { editedCity = it.take(60) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Correct city / add missing location") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onCity(editedCity) }) { Text("Save place", color = Lavender) }
                    TextButton(onClick = { editedCity = ""; onCity("") }) { Text("Use GPS / Unknown", color = Muted) }
                }
                Spacer(Modifier.height(9.dp))
                Text("AUTO + MANUAL TAGS", color = Lavender, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(photo.searchableTags.filterNot { it == photo.category.display }.sorted()
                    .joinToString(" · ").ifBlank { "No additional tags" },
                    color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newTag, onValueChange = { newTag = it.take(48) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        label = { Text("Add a custom tag") })
                    TextButton(onClick = { onAddTag(newTag); newTag = "" }, enabled = newTag.isNotBlank()) {
                        Text("Add", color = Lavender)
                    }
                }
                photo.manualTags.sorted().forEach { tag ->
                    TextButton(onClick = { onRemoveTag(tag) }) { Text("×  $tag", color = Ink) }
                }
                HorizontalDivider(color = Stroke, modifier = Modifier.padding(vertical = 12.dp))
                Text("PHOTO QUALITY TAGS", color = Lavender, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                SmartTag.entries.forEach { tag ->
                    Row(Modifier.fillMaxWidth().clickable { onTag(tag) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tag in photo.tags, onCheckedChange = { onTag(tag) })
                        Text(tag.display, color = Ink, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Lavender, fontWeight = FontWeight.Bold) } },
        containerColor = CardWhite
    )
}

/** Shares existing content URIs; no image byte streams are opened or copied. */
private fun sharePhotos(context: Context, photos: List<PhotoItem>) {
    val uris = ArrayList(photos.take(50).map { it.source.uri })
    if (uris.isEmpty()) return
    try {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "PhotoZest selected photos", uris[0]).also { clips ->
                uris.drop(1).forEach { clips.addItem(ClipData.Item(it)) }
            }
        }
        context.startActivity(Intent.createChooser(intent, "Share filtered original photos"))
    } catch (_: Exception) {
        Toast.makeText(context, "No compatible sharing app was available for these photos.", Toast.LENGTH_LONG).show()
    }
}
