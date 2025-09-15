package com.example.hoopradar.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.net.Uri
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.Timestamp
import java.util.Locale
import kotlin.math.round

private data class Court(
    val id: String,
    val name: String,
    val address: String,
    val photoUrl: String,
    val geo: GeoPoint?,
    val hasLights: Boolean?,
    val surface: String?,
    val author: String?,
    val avgRating: Double?,
    val ratingCount: Long?,
    val createdAt: Timestamp?,
    val lastInteractionAt: Timestamp?
)

private fun DocumentSnapshot.toCourt(): Court {
    val avg = getDouble("avgRating")
        ?: getLong("rating")?.toDouble()
        ?: run {
            val s = getLong("ratingSum")?.toDouble() ?: 0.0
            val c = getLong("ratingCount")?.toDouble() ?: 0.0
            if (c > 0) s / c else null
        }
    return Court(
        id = id,
        name = getString("name") ?: "",
        address = getString("address") ?: "",
        photoUrl = getString("photoUrl") ?: "",
        geo = getGeoPoint("geo"),
        hasLights = getBoolean("hasLights"),
        surface = getString("surface"),
        author = getString("author") ?: getString("createdBy"),
        avgRating = avg,
        ratingCount = getLong("ratingCount"),
        createdAt = getTimestamp("createdAt"),
        lastInteractionAt = getTimestamp("lastInteractionAt") ?: getTimestamp("updatedAt")
    )
}

private enum class LightsFilter { ANY, YES, NO }
private enum class SortKey {
    CREATED_DESC, CREATED_ASC,
    LAST_INTERACTION_DESC, LAST_INTERACTION_ASC,
    RATING_DESC, RATING_ASC
}
private enum class TimeBasis { CREATED, LAST_INTERACTION }
private data class TimeWindow(val label: String, val days: Int)
private val TIME_WINDOWS = listOf(
    TimeWindow("Any time", 0),
    TimeWindow("24h", 1),
    TimeWindow("7d", 7),
    TimeWindow("30d", 30),
)

/* ---------------- Screen ---------------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourtsScreen(tabNav: NavController) {

    val rawDocs by produceState<List<DocumentSnapshot>>(emptyList()) {
        FirebaseFirestore.getInstance()
            .collection("courts")
            .addSnapshotListener { snap, _ -> value = snap?.documents ?: emptyList() }
    }
    val courts = remember(rawDocs) { rawDocs.map { it.toCourt() } }

    val authors  = remember(courts) { courts.mapNotNull { it.author?.takeIf { a -> a.isNotBlank() } }.distinct().sorted() }

    var query            by rememberSaveable { mutableStateOf("") }
    var showFilters      by rememberSaveable { mutableStateOf(false) }
    var authorFilter     by rememberSaveable { mutableStateOf<String?>(null) }
    var minRating        by rememberSaveable { mutableStateOf(0f) }
    var lightsFilter     by rememberSaveable { mutableStateOf(LightsFilter.ANY) }
    var selectedSurfaces by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sortKey          by rememberSaveable { mutableStateOf(SortKey.LAST_INTERACTION_DESC) }
    var timeBasis        by rememberSaveable { mutableStateOf(TimeBasis.LAST_INTERACTION) }
    var timeWindowIdx    by rememberSaveable { mutableStateOf(0) }

    val nowMs = System.currentTimeMillis()
    fun withinWindow(ts: Timestamp?): Boolean {
        val days = TIME_WINDOWS[timeWindowIdx].days
        if (days == 0 || ts == null) return true
        val cutoff = nowMs - days * 24L * 60L * 60L * 1000L
        return ts.toDate().time >= cutoff
    }

    val filteredSorted = remember(courts, query, authorFilter, minRating, lightsFilter, selectedSurfaces, sortKey, timeBasis, timeWindowIdx) {
        courts
            .asSequence()
            .filter { c ->
                val q = query.trim().lowercase(Locale.getDefault())
                val matchesQ = q.isEmpty()
                        || c.name.lowercase(Locale.getDefault()).contains(q)
                        || c.address.lowercase(Locale.getDefault()).contains(q)
                val matchesAuthor = authorFilter == null || c.author == authorFilter
                val matchesRating = (c.avgRating ?: 0.0) >= minRating.toDouble()
                val matchesLights = when (lightsFilter) {
                    LightsFilter.ANY -> true
                    LightsFilter.YES -> c.hasLights == true
                    LightsFilter.NO  -> c.hasLights == false
                }
                val matchesSurface = selectedSurfaces.isEmpty() || (
                        c.surface?.let { s -> selectedSurfaces.any { it.equals(s, ignoreCase = true) } } ?: false
                        )
                val ts = if (timeBasis == TimeBasis.CREATED) c.createdAt else c.lastInteractionAt
                val matchesTime = withinWindow(ts)
                matchesQ && matchesAuthor && matchesRating && matchesLights && matchesSurface && matchesTime
            }
            .sortedWith(
                when (sortKey) {
                    SortKey.CREATED_DESC          -> compareByDescending<Court> { it.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
                    SortKey.CREATED_ASC           -> compareBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    SortKey.LAST_INTERACTION_DESC -> compareByDescending<Court> { it.lastInteractionAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
                    SortKey.LAST_INTERACTION_ASC  -> compareBy { it.lastInteractionAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    SortKey.RATING_DESC           -> compareByDescending<Court> { it.avgRating ?: -1.0 }
                    SortKey.RATING_ASC            -> compareBy { it.avgRating ?: 99.0 }
                }
            )
            .toList()
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding()
    ) {
        Text(
            text = "COURTS",
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            textAlign = TextAlign.Center,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF01579B)
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = slideInVertically(tween(150), { it }),
                        exit  = slideOutVertically(tween(150), { it })
                    ) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            },
            placeholder = { Text("Search by name or address") },
            singleLine = true
        )

        AnimatedVisibility(visible = showFilters) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (authors.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            value = authorFilter ?: "All authors",
                            onValueChange = {},
                            label = { Text("Author") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("All authors") }, onClick = { authorFilter = null; expanded = false })
                            authors.forEach { a -> DropdownMenuItem(text = { Text(a) }, onClick = { authorFilter = a; expanded = false }) }
                        }
                    }
                }

                Column {
                    Text("Min rating: ${"%.1f".format(minRating)}")
                    Slider(
                        value = minRating,
                        onValueChange = { minRating = round(it * 2f) / 2f },
                        valueRange = 0f..5f,
                        steps = 9
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { lightsFilter = LightsFilter.ANY },
                        label = { Text("Lights: Any") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (lightsFilter == LightsFilter.ANY) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    AssistChip(
                        onClick = { lightsFilter = LightsFilter.YES },
                        label = { Text("Yes") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (lightsFilter == LightsFilter.YES) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    AssistChip(
                        onClick = { lightsFilter = LightsFilter.NO },
                        label = { Text("No") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (lightsFilter == LightsFilter.NO) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Text("Surface")
                val ALL_SURFACES = listOf("wood", "asphalt", "rubber", "concrete")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ALL_SURFACES.forEach { s ->
                        val selected = selectedSurfaces.contains(s)
                        FilterChip(
                            selected = selected,
                            onClick = { selectedSurfaces = if (selected) selectedSurfaces - s else selectedSurfaces + s },
                            label = { Text(s) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var tbExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = tbExpanded, onExpandedChange = { tbExpanded = !tbExpanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            value = if (timeBasis == TimeBasis.CREATED) "Created" else "Last interaction",
                            onValueChange = {},
                            label = { Text("Time basis") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tbExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = tbExpanded, onDismissRequest = { tbExpanded = false }) {
                            DropdownMenuItem(text = { Text("Created") }, onClick = { timeBasis = TimeBasis.CREATED; tbExpanded = false })
                            DropdownMenuItem(text = { Text("Last interaction") }, onClick = { timeBasis = TimeBasis.LAST_INTERACTION; tbExpanded = false })
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    var twExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = twExpanded, onExpandedChange = { twExpanded = !twExpanded }) {
                        OutlinedTextField(
                            readOnly = true,
                            value = TIME_WINDOWS[timeWindowIdx].label,
                            onValueChange = {},
                            label = { Text("Time window") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(twExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = twExpanded, onDismissRequest = { twExpanded = false }) {
                            TIME_WINDOWS.forEachIndexed { idx, tw ->
                                DropdownMenuItem(text = { Text(tw.label) }, onClick = { timeWindowIdx = idx; twExpanded = false })
                            }
                        }
                    }
                }

                var sortExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = sortExpanded, onExpandedChange = { sortExpanded = !sortExpanded }) {
                    OutlinedTextField(
                        readOnly = true,
                        value = when (sortKey) {
                            SortKey.CREATED_DESC -> "Sort: Newest"
                            SortKey.CREATED_ASC -> "Sort: Oldest"
                            SortKey.LAST_INTERACTION_DESC -> "Sort: Most active"
                            SortKey.LAST_INTERACTION_ASC -> "Sort: Least active"
                            SortKey.RATING_DESC -> "Sort: Highest rating"
                            SortKey.RATING_ASC -> "Sort: Lowest rating"
                        },
                        onValueChange = {},
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sortExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        DropdownMenuItem(text = { Text("Newest (created)") }, onClick = { sortKey = SortKey.CREATED_DESC; sortExpanded = false })
                        DropdownMenuItem(text = { Text("Oldest (created)") }, onClick = { sortKey = SortKey.CREATED_ASC; sortExpanded = false })
                        DropdownMenuItem(text = { Text("Most active (last interaction)") }, onClick = { sortKey = SortKey.LAST_INTERACTION_DESC; sortExpanded = false })
                        DropdownMenuItem(text = { Text("Least active (last interaction)") }, onClick = { sortKey = SortKey.LAST_INTERACTION_ASC; sortExpanded = false })
                        DropdownMenuItem(text = { Text("Highest rating") }, onClick = { sortKey = SortKey.RATING_DESC; sortExpanded = false })
                        DropdownMenuItem(text = { Text("Lowest rating") }, onClick = { sortKey = SortKey.RATING_ASC; sortExpanded = false })
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredSorted, key = { it.id }) { court ->
                CourtCard(court = court) {
                    val gp = court.geo ?: return@CourtCard
                    val route = "map" +
                            "?lat=${gp.latitude}" +
                            "&lng=${gp.longitude}" +
                            "&name=${Uri.encode(court.name)}" +
                            "&url=${Uri.encode(court.photoUrl)}" +
                            "&lights=${court.hasLights == true}"
                    tabNav.navigate(route)
                }
            }
        }
    }
}

/* ---------------- Card ---------------- */
@Composable
private fun CourtCard(
    court: Court,
    onClick: () -> Unit
) {
    val nameUpper = remember(court.name) { court.name.uppercase(Locale.getDefault()) }
    val ratingStr = court.avgRating?.let { "%.1f".format(Locale.US, it) } ?: "–"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF01579B)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(court.photoUrl),
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    nameUpper,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.6.sp
                )
                Text(
                    court.address,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(
                    ratingStr,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
