package com.yofect.play

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val Ink = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF15171D)
private val SurfaceMid = Color(0xFF20232B)
private val Accent = Color(0xFFE50914)
private val Muted = Color(0xFF9DA2AD)
private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
private const val PREFS = "yofect_play_preferences"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YofectPlayApp() }
    }
}

private data class Work(
    val id: Int,
    val title: String,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val year: String,
    val language: String,
    val mediaType: String,
    val rating: Double
)
private data class Catalog(
    val trending: List<Work> = emptyList(),
    val movies: List<Work> = emptyList(),
    val shows: List<Work> = emptyList(),
    val arabic: List<Work> = emptyList(),
    val turkish: List<Work> = emptyList(),
    val indian: List<Work> = emptyList(),
    val international: List<Work> = emptyList()
)
private data class Season(val number: Int, val name: String, val episodes: Int)
private data class Episode(val number: Int, val name: String, val overview: String, val still: String?)
private data class WorkDetails(val overview: String, val genres: List<String>, val runtime: String, val seasons: List<Season>)

private fun favoriteKey(work: Work) = work.mediaType + ":" + work.id
private fun getApiKey(context: Context): String {
    val local = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tmdb_key", "").orEmpty()
    return local.ifBlank { BuildConfig.TMDB_API_KEY }
}
private fun readFavorites(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

private fun parseWork(o: JSONObject, fallback: String = ""): Work {
    val type = o.optString("media_type").ifBlank { fallback }
    val date = if (type == "tv") o.optString("first_air_date") else o.optString("release_date")
    val posterPath = o.optString("poster_path")
    val backdropPath = o.optString("backdrop_path")
    return Work(
        id = o.optInt("id"),
        title = (if (type == "tv") o.optString("name") else o.optString("title")).ifBlank { "بدون عنوان" },
        overview = o.optString("overview"),
        poster = if (posterPath.isBlank() || posterPath == "null") null else IMAGE_BASE + "w500" + posterPath,
        backdrop = if (backdropPath.isBlank() || backdropPath == "null") null else IMAGE_BASE + "w1280" + backdropPath,
        year = date.take(4),
        language = o.optString("original_language").uppercase(),
        mediaType = if (type == "tv") "tv" else "movie",
        rating = o.optDouble("vote_average", 0.0)
    )
}

private suspend fun apiJson(context: Context, path: String): JSONObject? = withContext(Dispatchers.IO) {
    val key = getApiKey(context)
    if (key.isBlank()) return@withContext null
    var connection: HttpURLConnection? = null
    try {
        val separator = if (path.contains("?")) "&" else "?"
        val endpoint = URL("https://api.themoviedb.org/3/" + path + separator +
            "api_key=" + URLEncoder.encode(key, "UTF-8") + "&language=ar-EG&region=EG")
        connection = endpoint.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 18000
        connection.requestMethod = "GET"
        if (connection.responseCode !in 200..299) return@withContext null
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private suspend fun apiWorks(context: Context, path: String, fallback: String = ""): List<Work> {
    val rows = apiJson(context, path)?.optJSONArray("results") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index ->
        runCatching { parseWork(rows.getJSONObject(index), fallback) }.getOrNull()
    }.filter { it.id > 0 }
}

private suspend fun loadCatalog(context: Context): Catalog = coroutineScope {
    val trending = async { apiWorks(context, "trending/all/week") }
    val movies = async { apiWorks(context, "movie/popular", "movie") }
    val shows = async { apiWorks(context, "tv/popular", "tv") }
    val arMovies = async { apiWorks(context, "discover/movie?with_original_language=ar&sort_by=popularity.desc", "movie") }
    val arShows = async { apiWorks(context, "discover/tv?with_original_language=ar&sort_by=popularity.desc", "tv") }
    val trMovies = async { apiWorks(context, "discover/movie?with_original_language=tr&sort_by=popularity.desc", "movie") }
    val trShows = async { apiWorks(context, "discover/tv?with_original_language=tr&sort_by=popularity.desc", "tv") }
    val inMovies = async { apiWorks(context, "discover/movie?with_original_language=hi&sort_by=popularity.desc", "movie") }
    val inShows = async { apiWorks(context, "discover/tv?with_original_language=hi&sort_by=popularity.desc", "tv") }
    val enMovies = async { apiWorks(context, "discover/movie?with_original_language=en&sort_by=popularity.desc", "movie") }
    val enShows = async { apiWorks(context, "discover/tv?with_original_language=en&sort_by=popularity.desc", "tv") }
    Catalog(
        trending = trending.await(),
        movies = movies.await(),
        shows = shows.await(),
        arabic = (arMovies.await() + arShows.await()).distinctBy { favoriteKey(it) },
        turkish = (trMovies.await() + trShows.await()).distinctBy { favoriteKey(it) },
        indian = (inMovies.await() + inShows.await()).distinctBy { favoriteKey(it) },
        international = (enMovies.await() + enShows.await()).distinctBy { favoriteKey(it) }
    )
}

private suspend fun loadDetails(context: Context, work: Work): WorkDetails? {
    val path = if (work.mediaType == "tv") "tv/" + work.id else "movie/" + work.id
    val json = apiJson(context, path) ?: return null
    val genresArray = json.optJSONArray("genres") ?: JSONArray()
    val genres = (0 until genresArray.length()).mapNotNull { genresArray.optJSONObject(it)?.optString("name") }
    val seasonsArray = json.optJSONArray("seasons") ?: JSONArray()
    val seasons = (0 until seasonsArray.length()).mapNotNull { index ->
        seasonsArray.optJSONObject(index)?.let {
            Season(it.optInt("season_number"), it.optString("name"), it.optInt("episode_count"))
        }
    }.filter { it.number > 0 }
    val runtime = if (work.mediaType == "tv") {
        val times = json.optJSONArray("episode_run_time")
        if (times != null && times.length() > 0) times.optInt(0).toString() + " دقيقة للحلقة" else "غير محدد"
    } else {
        val minutes = json.optInt("runtime")
        if (minutes > 0) minutes.toString() + " دقيقة" else "غير محدد"
    }
    return WorkDetails(json.optString("overview").ifBlank { work.overview }, genres, runtime, seasons)
}

private suspend fun loadEpisodes(context: Context, work: Work, season: Season): List<Episode> {
    val rows = apiJson(context, "tv/" + work.id + "/season/" + season.number)?.optJSONArray("episodes") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index ->
        rows.optJSONObject(index)?.let { item ->
            val stillPath = item.optString("still_path")
            val still = if (stillPath.isBlank() || stillPath == "null") null else IMAGE_BASE + "w500" + stillPath
            val number = item.optInt("episode_number")
            Episode(number, item.optString("name").ifBlank { "الحلقة " + number }, item.optString("overview"), still)
        }
    }
}

@Composable
private fun YofectPlayApp() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf(Catalog()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Work?>(null) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Work>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchStarted by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(readFavorites(context)) }
    var keyInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun toggleFavorite(work: Work) {
        val next = favorites.toMutableSet()
        val id = favoriteKey(work)
        if (!next.add(id)) next.remove(id)
        favorites = next
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("favorites", next).apply()
    }
    suspend fun refreshCatalog() {
        loading = true
        error = ""
        catalog = loadCatalog(context)
        loading = false
        if (getApiKey(context).isBlank()) error = "أضف مفتاح TMDB من الإعدادات لتشغيل المكتبة."
        else if (catalog.trending.isEmpty()) error = "لم تصل بيانات. تحقق من مفتاح TMDB واتصال الإنترنت."
    }

    LaunchedEffect(Unit) { refreshCatalog() }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            searchResults = emptyList()
            searchStarted = false
            searchBusy = false
        } else {
            delay(450)
            searchBusy = true
            searchStarted = true
            val rows = apiWorks(context, "search/multi?query=" + URLEncoder.encode(query.trim(), "UTF-8"))
            searchResults = rows.filter { it.mediaType == "movie" || it.mediaType == "tv" }
            searchBusy = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Ink, surface = SurfaceDark, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (selected != null) {
                DetailScreen(
                    work = selected!!,
                    favorite = favorites.contains(favoriteKey(selected!!)),
                    onBack = { selected = null },
                    onFavorite = { toggleFavorite(selected!!) }
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> HomeScreen(catalog, loading, error, { selected = it }, { scope.launch { refreshCatalog() } })
                            1 -> SearchScreen(query, { query = it }, searchResults, searchBusy, searchStarted) { selected = it }
                            2 -> LibraryScreen(catalog, favorites) { selected = it }
                            else -> SettingsScreen(
                                hasKey = getApiKey(context).isNotBlank(),
                                input = keyInput,
                                onInput = { keyInput = it },
                                onSave = {
                                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tmdb_key", keyInput.trim()).apply()
                                    keyInput = ""
                                    scope.launch { refreshCatalog() }
                                },
                                onRefresh = { scope.launch { refreshCatalog() } }
                            )
                        }
                    }
                    NavigationBar(containerColor = Color(0xFF101116)) {
                        val labels = listOf("الرئيسية", "بحث", "قائمتي", "الإعدادات")
                        val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Favorite, Icons.Default.Settings)
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icons[index], null) },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent, selectedTextColor = Color.White,
                                    indicatorColor = Color(0xFF3A171E), unselectedIconColor = Muted, unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(catalog: Catalog, loading: Boolean, error: String, onOpen: (Work) -> Unit, onRefresh: () -> Unit) {
    var region by remember { mutableStateOf("الكل") }
    var media by remember { mutableStateOf("الكل") }
    val source = when (region) {
        "عربي" -> catalog.arabic
        "تركي" -> catalog.turkish
        "هندي" -> catalog.indian
        "أجنبي" -> catalog.international
        else -> catalog.movies + catalog.shows
    }
    val filtered = when (media) {
        "أفلام" -> source.filter { it.mediaType == "movie" }
        "مسلسلات" -> source.filter { it.mediaType == "tv" }
        else -> source
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("YOFECT", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("PLAY", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "تحديث", tint = Color.White) }
            }
        }
        if (catalog.trending.isNotEmpty()) item { HeroBanner(catalog.trending.first(), onOpen) }
        else item { WelcomeBanner() }
        item { SectionHeader("مكتبة من كل العالم", "اختر بلد الإنتاج ونوع العمل") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("الكل", "عربي", "تركي", "هندي", "أجنبي")) { value ->
                    FilterChip(selected = region == value, onClick = { region = value }, label = { Text(value) })
                }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("الكل", "أفلام", "مسلسلات")) { value ->
                    FilterChip(selected = media == value, onClick = { media = value }, label = { Text(value) })
                }
            }
        }
        if (loading) item { LoadingBlock() }
        if (error.isNotBlank()) item { NoticeCard(error) }
        if (region == "الكل") {
            if (catalog.trending.isNotEmpty()) item { PosterSection("الأكثر رواجًا الآن", catalog.trending, onOpen) }
            if (catalog.movies.isNotEmpty()) item { PosterSection("أفلام شائعة", catalog.movies, onOpen) }
            if (catalog.shows.isNotEmpty()) item { PosterSection("مسلسلات شائعة", catalog.shows, onOpen) }
        }
        if (filtered.isNotEmpty()) {
            val heading = when (region) {
                "عربي" -> "الأعمال العربية"
                "تركي" -> "الأعمال التركية"
                "هندي" -> "الأعمال الهندية"
                "أجنبي" -> "الأعمال الأجنبية"
                else -> "كل الأعمال"
            }
            item { PosterSection(heading, filtered, onOpen) }
        } else if (!loading && region != "الكل") {
            item { NoticeCard("لا توجد نتائج في هذا التصنيف حاليًا. تأكد من إعداد مفتاح TMDB ثم حدّث المكتبة.") }
        }
        item {
            Text("هذه مكتبة بيانات وصور ومواسم. تشغيل الفيديو يتطلب مصدرًا مرخصًا متاحًا لكل عمل.", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}

@Composable
private fun WelcomeBanner() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(190.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF5D1322), Color(0xFF21131B), SurfaceDark))), contentAlignment = Alignment.BottomStart) {
        Column(Modifier.padding(20.dp)) {
            Text("كل حكاية تبدأ هنا", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text("أفلام ومسلسلات عربية وعالمية", color = Color.LightGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HeroBanner(work: Work, onOpen: (Work) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(220.dp).clip(RoundedCornerShape(22.dp)).clickable { onOpen(work) }) {
        AsyncImage(work.backdrop ?: work.poster, work.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE07080C)))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text("الأكثر رواجًا", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(work.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text((if (work.mediaType == "tv") "مسلسل" else "فيلم") + " • " + work.year + " • ★ " + String.format("%.1f", work.rating), color = Color.LightGray, fontSize = 11.sp)
        }
        Icon(Icons.Default.PlayCircleFilled, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(34.dp))
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 2.dp)) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PosterSection(title: String, works: List<Work>, onOpen: (Work) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(works.distinctBy { favoriteKey(it) }) { PosterCard(it, onOpen) }
        }
    }
}

@Composable
private fun PosterCard(work: Work, onOpen: (Work) -> Unit) {
    Column(Modifier.width(124.dp).clickable { onOpen(work) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box {
            AsyncImage(work.poster, work.title, Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceMid), contentScale = ContentScale.Crop)
            if (work.rating > 0) {
                Row(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xD9000000)).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.size(11.dp))
                    Text(" " + String.format("%.1f", work.rating), color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Text(work.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text((if (work.mediaType == "tv") "مسلسل" else "فيلم") + " • " + work.year, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, results: List<Work>, busy: Boolean, started: Boolean, onOpen: (Work) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("اكتشف المحتوى", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("اسم فيلم أو مسلسل أو ممثل") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, null) } },
            singleLine = true, shape = RoundedCornerShape(15.dp)
        )
        when {
            busy -> LoadingBlock()
            !started -> Text("ابحث في مكتبة الأفلام والمسلسلات.", color = Muted, modifier = Modifier.padding(20.dp))
            results.isEmpty() -> Text("لا توجد نتائج مطابقة.", color = Muted, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 10.dp)) {
                items(results.distinctBy { favoriteKey(it) }) { WorkListRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun WorkListRow(work: Work, onOpen: (Work) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(work) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(work.poster, work.title, Modifier.size(88.dp, 118.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceMid), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(work.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text((if (work.mediaType == "tv") "مسلسل" else "فيلم") + " • " + work.year + " • " + work.language, color = Muted, fontSize = 10.sp)
            Text(work.overview.ifBlank { "لا يوجد ملخص متاح." }, color = Color.LightGray, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("عرض التفاصيل", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryScreen(catalog: Catalog, favorites: Set<String>, onOpen: (Work) -> Unit) {
    val all = (catalog.trending + catalog.movies + catalog.shows + catalog.arabic + catalog.turkish + catalog.indian + catalog.international).distinctBy { favoriteKey(it) }
    val saved = all.filter { favorites.contains(favoriteKey(it)) }
    Column(Modifier.fillMaxSize()) {
        Text("قائمتي", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        if (saved.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.FavoriteBorder, null, tint = Accent, modifier = Modifier.size(52.dp))
                Text("قائمتك فاضية", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text("افتح أي عمل واضغط القلب لإضافته لقائمتك.", color = Muted, fontSize = 12.sp)
            }
        } else {
            LazyColumn { items(saved) { WorkListRow(it, onOpen) } }
        }
    }
}

@Composable
private fun DetailScreen(work: Work, favorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit) {
    val context = LocalContext.current
    var details by remember(work.id, work.mediaType) { mutableStateOf<WorkDetails?>(null) }
    var loading by remember(work.id, work.mediaType) { mutableStateOf(true) }
    var selectedSeason by remember(work.id, work.mediaType) { mutableStateOf<Season?>(null) }
    var episodes by remember(work.id, work.mediaType) { mutableStateOf<List<Episode>>(emptyList()) }
    var episodesLoading by remember { mutableStateOf(false) }

    LaunchedEffect(work.id, work.mediaType) {
        details = loadDetails(context, work)
        loading = false
    }
    LaunchedEffect(selectedSeason) {
        val season = selectedSeason ?: return@LaunchedEffect
        episodesLoading = true
        episodes = loadEpisodes(context, work, season)
        episodesLoading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(285.dp)) {
                AsyncImage(work.backdrop ?: work.poster, work.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Ink))))
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Accent else Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(if (work.mediaType == "tv") "مسلسل" else "فيلم", color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(work.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(work.year + " • " + work.language + " • ★ " + String.format("%.1f", work.rating), color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
        item {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(details?.overview ?: work.overview.ifBlank { "لا يتوفر ملخص لهذا العمل." }, color = Color.LightGray, fontSize = 13.sp, lineHeight = 21.sp)
                if (details != null) {
                    Text("النوع: " + details!!.genres.joinToString(" • ").ifBlank { "غير محدد" }, color = Muted, fontSize = 11.sp)
                    Text("المدة: " + details!!.runtime, color = Muted, fontSize = 11.sp)
                }
                Button(onClick = { openWhereToWatch(context, work.title) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ابحث عن منصة مشاهدة رسمية")
                }
                Text("لا يوجد بث فيديو مضاف لهذا العمل حتى الآن. زر المشاهدة يبحث عن المنصات التي تعرضه رسميًا.", color = Muted, fontSize = 10.sp)
            }
        }
        if (work.mediaType == "tv") {
            item { SectionHeader("المواسم والحلقات") }
            if (loading) item { LoadingBlock() }
            if (!loading && details?.seasons.isNullOrEmpty()) item { NoticeCard("لم تتوفر معلومات المواسم لهذا المسلسل في قاعدة البيانات.") }
            items(details?.seasons.orEmpty()) { season ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceDark).clickable { selectedSeason = season }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(season.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(season.episodes.toString() + " حلقة", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronLeft, null, tint = Accent)
                }
            }
            if (selectedSeason != null) {
                item { SectionHeader(selectedSeason!!.name) }
                if (episodesLoading) item { LoadingBlock() }
                if (!episodesLoading && episodes.isEmpty()) item { NoticeCard("لا توجد بيانات حلقات متاحة لهذا الموسم.") }
                items(episodes) { episode ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).background(SurfaceDark, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(episode.still ?: work.poster, episode.name, Modifier.size(104.dp, 66.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("الحلقة " + episode.number + ": " + episode.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(episode.overview.ifBlank { "لا يتوفر وصف للحلقة." }, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun openWhereToWatch(context: Context, title: String) {
    val encoded = URLEncoder.encode(title, "UTF-8")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.justwatch.com/eg/search?q=" + encoded))) }
}

@Composable
private fun SettingsScreen(hasKey: Boolean, input: String, onInput: (String) -> Unit, onSave: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("الإعدادات", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Movie, null, tint = Accent, modifier = Modifier.size(34.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text("YOFECT PLAY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("مكتبتك للأفلام والمسلسلات", color = Muted, fontSize = 11.sp)
            }
        }
        Divider(color = SurfaceMid)
        Text("تفعيل مكتبة الأعمال", color = Color.White, fontWeight = FontWeight.Bold)
        Text("يلزم مفتاح TMDB لعرض بيانات الأفلام والمسلسلات والبوسترات والمواسم. أنشئ مفتاح API من موقع TMDB وأدخله هنا. يُحفظ المفتاح على جهازك.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        OutlinedTextField(value = input, onValueChange = onInput, modifier = Modifier.fillMaxWidth(), label = { Text("TMDB API Key") }, placeholder = { Text("أدخل مفتاح API") }, singleLine = true)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) { Text("حفظ المفتاح") }
        Text(if (hasKey) "تم العثور على مفتاح محفوظ أو مضبوط للبناء." else "المفتاح غير مضبوط بعد.", color = if (hasKey) Color(0xFF74D49A) else Color(0xFFFFC857), fontSize = 11.sp)
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("تحديث المكتبة")
        }
        Text("TMDB يوفر البيانات والصور، ولا يوفر ملفات تشغيل الأفلام أو حقوق بث الحلقات. روابط المشاهدة توجه إلى البحث عن المنصات الرسمية.", color = Muted, fontSize = 10.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
    }
}
@Composable
private fun NoticeCard(message: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2B2020)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Info, null, tint = Color(0xFFFFC857))
        Text(message, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
    }
}
