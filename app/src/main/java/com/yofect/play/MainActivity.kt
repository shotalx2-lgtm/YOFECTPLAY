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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

private data class UiPalette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val textMuted: Color,
    val text: Color
)
private val DarkPalette = UiPalette(
    Color(0xFF08090D), Color(0xFF15171D), Color(0xFF20232B),
    Color(0xFF9DA2AD), Color.White
)
private val LightPalette = UiPalette(
    Color(0xFFF5F6F8), Color.White, Color(0xFFE9EBEF),
    Color(0xFF656B76), Color(0xFF17191E)
)
private val LocalUiPalette = staticCompositionLocalOf { DarkPalette }
private val Accent = Color(0xFFE50914)
@Composable private fun ink() = LocalUiPalette.current.background
@Composable private fun surfaceDark() = LocalUiPalette.current.surface
@Composable private fun surfaceMid() = LocalUiPalette.current.surfaceRaised
@Composable private fun muted() = LocalUiPalette.current.textMuted
@Composable private fun primaryText() = LocalUiPalette.current.text
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
    val rating: Double,
    val source: String = "TMDB",
    val externalUrl: String? = null,
    val sourceLicense: String = ""
)
private data class Catalog(
    val trending: List<Work> = emptyList(),
    val movies: List<Work> = emptyList(),
    val shows: List<Work> = emptyList(),
    val arabic: List<Work> = emptyList(),
    val turkish: List<Work> = emptyList(),
    val indian: List<Work> = emptyList(),
    val international: List<Work> = emptyList(),
    val openShows: List<Work> = emptyList(),
    val publicDomain: List<Work> = emptyList(),
    val openVideos: List<Work> = emptyList()
)
private data class Season(val number: Int, val name: String, val episodes: Int, val sourceId: Int = 0)
private data class Episode(val number: Int, val name: String, val overview: String, val still: String?)
private data class WatchProvider(val name: String, val logo: String?, val link: String)
private data class WorkDetails(val overview: String, val genres: List<String>, val runtime: String, val seasons: List<Season>, val providers: List<WatchProvider>, val providerLink: String)

private fun favoriteKey(work: Work) = work.mediaType + ":" + work.source + ":" + work.id

private fun normalizedTitle(value: String): String {
    val plain = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    return plain.replace("[^\\p{L}\\p{N}]".toRegex(), "")
}

private fun identityKey(work: Work): String {
    val title = normalizedTitle(work.title)
    return if (title.isBlank()) favoriteKey(work)
    else work.mediaType + ":" + title + ":" + work.year.take(4)
}

private fun sourcePriority(work: Work): Int = when (work.source) {
    "TMDB" -> 4
    "TVmaze" -> 3
    "Internet Archive" -> 2
    "Wikimedia Commons" -> 1
    else -> 0
}

private fun dedupeWorks(items: List<Work>): List<Work> {
    val map = LinkedHashMap<String, Work>()
    items.filter { it.title.isNotBlank() && it.id != 0 }.forEach { work ->
        val key = identityKey(work)
        val old = map[key]
        if (old == null || sourcePriority(work) > sourcePriority(old) ||
            (old.poster.isNullOrBlank() && !work.poster.isNullOrBlank()) ||
            (old.overview.isBlank() && work.overview.isNotBlank())
        ) map[key] = work
    }
    return map.values.toList()
}
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

private suspend fun apiWorksPages(context: Context, path: String, fallback: String = ""): List<Work> {
    val pages = mutableListOf<Work>()
    for (page in 1..2) {
        val separator = if (path.contains("?")) "&" else "?"
        pages.addAll(apiWorks(context, path + separator + "page=" + page, fallback))
    }
    return pages.distinctBy { favoriteKey(it) }
}

private suspend fun loadCatalog(context: Context): Catalog = coroutineScope {
    val openShows = async { loadTvMazeShows() }
    val publicDomain = async { loadInternetArchive("", 20) }
    val openVideos = async { loadCommonsVideos("", 16) }
    val trending = async { apiWorksPages(context, "trending/all/week") }
    val movies = async { apiWorksPages(context, "movie/popular", "movie") }
    val shows = async { apiWorksPages(context, "tv/popular", "tv") }
    val arMovies = async { apiWorksPages(context, "discover/movie?with_original_language=ar&sort_by=popularity.desc", "movie") }
    val arShows = async { apiWorksPages(context, "discover/tv?with_original_language=ar&sort_by=popularity.desc", "tv") }
    val trMovies = async { apiWorksPages(context, "discover/movie?with_original_language=tr&sort_by=popularity.desc", "movie") }
    val trShows = async { apiWorksPages(context, "discover/tv?with_original_language=tr&sort_by=popularity.desc", "tv") }
    val inMovies = async { apiWorksPages(context, "discover/movie?with_original_language=hi%7Cta%7Cte%7Cml%7Ckn%7Cbn%7Cmr&sort_by=popularity.desc", "movie") }
    val inShows = async { apiWorks(context, "discover/tv?with_original_language=hi&sort_by=popularity.desc", "tv") }
    val enMovies = async { apiWorksPages(context, "discover/movie?without_original_language=ar,tr,hi,ta,te,ml,kn,bn,mr&sort_by=popularity.desc", "movie") }
    val enShows = async { apiWorks(context, "discover/tv?with_original_language=en&sort_by=popularity.desc", "tv") }
    val noKeyShows = openShows.await()
    val archiveItems = publicDomain.await()
    val commonsItems = openVideos.await()
    Catalog(
        trending = dedupeWorks(trending.await() + noKeyShows.take(8) + archiveItems.take(6)),
        movies = dedupeWorks(movies.await()),
        shows = dedupeWorks(shows.await() + noKeyShows),
        arabic = dedupeWorks(arMovies.await() + arShows.await() + noKeyShows.filter { it.language == "AR" }),
        turkish = dedupeWorks(trMovies.await() + trShows.await() + noKeyShows.filter { it.language == "TR" }),
        indian = dedupeWorks(inMovies.await() + inShows.await() + noKeyShows.filter { it.language == "HI" }),
        international = dedupeWorks(enMovies.await() + enShows.await() + noKeyShows.filter { it.language !in listOf("AR", "TR", "HI") }),
        openShows = dedupeWorks(noKeyShows),
        publicDomain = archiveItems,
        openVideos = commonsItems
    )
}


private suspend fun publicJsonObject(url: String): JSONObject? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "YOFECT-PLAY/0.3 (Android; public metadata clients)")
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode !in 200..299) return@withContext null
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private suspend fun publicJsonArray(url: String): JSONArray? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "YOFECT-PLAY/0.3 (Android; public metadata clients)")
        connection.setRequestProperty("Accept", "application/json")
        if (connection.responseCode !in 200..299) return@withContext null
        JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private fun cleanHtml(value: String): String =
    value.replace("<[^>]*>".toRegex(), " ").replace("&nbsp;", " ")
        .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\\"")
        .replace("\\\\s+".toRegex(), " ").trim()

private fun parseTvMazeShow(show: JSONObject): Work? {
    val id = show.optInt("id")
    if (id <= 0) return null
    val image = show.optJSONObject("image")
    val imageUrl = image?.optString("original").orEmpty().ifBlank { image?.optString("medium").orEmpty() }
    val languageName = show.optString("language")
    val languageCode = when (languageName.lowercase(Locale.ROOT)) {
        "arabic" -> "AR"
        "turkish" -> "TR"
        "hindi" -> "HI"
        else -> languageName.take(2).uppercase(Locale.ROOT)
    }
    val summary = cleanHtml(show.optString("summary"))
    val year = show.optString("premiered").take(4).ifBlank { show.optString("ended").take(4) }
    val url = show.optString("url").ifBlank { "https://www.tvmaze.com/shows/" + id }
    return Work(
        id = -abs(id),
        title = show.optString("name").ifBlank { "مسلسل بدون عنوان" },
        overview = summary,
        poster = imageUrl.ifBlank { null },
        backdrop = imageUrl.ifBlank { null },
        year = year,
        language = languageCode,
        mediaType = "tv",
        rating = show.optJSONObject("rating")?.optDouble("average", 0.0) ?: 0.0,
        source = "TVmaze",
        externalUrl = url,
        sourceLicense = "TVmaze (CC BY-SA)"
    )
}

private suspend fun loadTvMazeShows(): List<Work> = coroutineScope {
    val pages = listOf(0, 10, 20, 30).map { page ->
        async { publicJsonArray("https://api.tvmaze.com/shows?page=" + page) ?: JSONArray() }
    }
    val result = mutableListOf<Work>()
    pages.forEach { deferred ->
        val rows = deferred.await()
        for (index in 0 until rows.length()) {
            val work = runCatching { parseTvMazeShow(rows.getJSONObject(index)) }.getOrNull()
            if (work != null) result.add(work)
        }
    }
    dedupeWorks(result).sortedByDescending { it.rating }.take(100)
}

private suspend fun searchTvMaze(query: String): List<Work> {
    val url = "https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(query, "UTF-8")
    val rows = publicJsonArray(url) ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index ->
        runCatching { parseTvMazeShow(rows.optJSONObject(index)?.optJSONObject("show") ?: JSONObject()) }.getOrNull()
    }
}

private suspend fun loadInternetArchive(query: String = "", limit: Int = 18): List<Work> {
    val rights = "(licenseurl:http*publicdomain* OR licenseurl:http*zero/1.0* OR licenseurl:http*by/4.0* OR licenseurl:http*by-sa/4.0*)"
    val q = if (query.isBlank()) "mediatype:movies AND " + rights else
        "mediatype:movies AND title:(" + query.replace("\\\\", " ").replace("\\"", " ") + ") AND " + rights
    val url = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
        "&fl[]=identifier&fl[]=title&fl[]=year&fl[]=description&fl[]=licenseurl" +
        "&sort[]=downloads%20desc&rows=" + limit + "&page=1&output=json"
    val docs = publicJsonObject(url)?.optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
    val results = mutableListOf<Work>()
    for (index in 0 until docs.length()) {
        val item = docs.optJSONObject(index) ?: continue
        val identifier = item.optString("identifier")
        val title = item.optString("title")
        val license = item.optString("licenseurl")
        val permitted = license.contains("publicdomain", true) || license.contains("zero/1.0", true) ||
            license.contains("by/4.0", true) || license.contains("by-sa/4.0", true)
        if (identifier.isBlank() || title.isBlank() || !permitted) continue
        val id = -((identifier.hashCode() and 0x7fffffff).coerceAtLeast(1))
        results.add(
            Work(
                id = id, title = title, overview = cleanHtml(item.optString("description")),
                poster = "https://archive.org/services/img/" + identifier,
                backdrop = "https://archive.org/services/img/" + identifier,
                year = item.optString("year").take(4), language = "—", mediaType = "movie",
                rating = 0.0, source = "Internet Archive",
                externalUrl = "https://archive.org/details/" + identifier,
                sourceLicense = license
            )
        )
    }
    return dedupeWorks(results)
}

private fun allowedCommonsLicense(label: String): Boolean {
    val value = label.trim().lowercase(Locale.ROOT)
    return value.contains("public domain") || value.contains("cc0") ||
        value.startsWith("cc by ") || value.startsWith("cc by-sa") ||
        value.startsWith("cc attribution") || value.startsWith("cc attribution-sharealike")
}

private suspend fun loadCommonsVideos(query: String = "", limit: Int = 16): List<Work> {
    val search = (if (query.isBlank()) "" else query + " ") + "filetype:video"
    val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search" +
        "&gsrsearch=" + URLEncoder.encode(search, "UTF-8") +
        "&gsrnamespace=6&gsrlimit=" + limit + "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=500"
    val pages = publicJsonObject(url)?.optJSONObject("query")?.optJSONObject("pages") ?: JSONObject()
    val keys = pages.keys()
    val results = mutableListOf<Work>()
    while (keys.hasNext()) {
        val page = pages.optJSONObject(keys.next()) ?: continue
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
        val metadata = info.optJSONObject("extmetadata") ?: JSONObject()
        val license = cleanHtml(metadata.optJSONObject("LicenseShortName")?.optString("value").orEmpty())
        if (!allowedCommonsLicense(license)) continue
        val fileName = page.optString("title").removePrefix("File:")
        val title = fileName.substringBeforeLast(".").replace("_", " ").trim()
        val thumb = info.optString("thumburl").ifBlank { info.optString("url") }
        val description = cleanHtml(metadata.optJSONObject("ImageDescription")?.optString("value").orEmpty())
        val pageUrl = "https://commons.wikimedia.org/wiki/" +
            URLEncoder.encode("File:" + fileName, "UTF-8").replace("+", "_")
        val id = -((fileName.hashCode() and 0x7fffffff).coerceAtLeast(1))
        if (title.isNotBlank() && thumb.isNotBlank()) {
            results.add(
                Work(
                    id = id, title = title, overview = description,
                    poster = thumb, backdrop = thumb, year = "", language = "—",
                    mediaType = "movie", rating = 0.0, source = "Wikimedia Commons",
                    externalUrl = pageUrl, sourceLicense = license
                )
            )
        }
    }
    return dedupeWorks(results)
}

private suspend fun searchPublicSources(query: String): List<Work> = coroutineScope {
    val tv = async { searchTvMaze(query) }
    val archive = async { loadInternetArchive(query, 8) }
    val commons = async { loadCommonsVideos(query, 8) }
    dedupeWorks(tv.await() + archive.await() + commons.await())
}

private suspend fun loadTvMazeDetails(work: Work): WorkDetails {
    val showId = abs(work.id)
    val show = publicJsonObject("https://api.tvmaze.com/shows/" + showId) ?: JSONObject()
    val genres = show.optJSONArray("genres") ?: JSONArray()
    val genreNames = (0 until genres.length()).map { genres.optString(it) }.filter { it.isNotBlank() }
    val seasonsJson = publicJsonArray("https://api.tvmaze.com/shows/" + showId + "/seasons") ?: JSONArray()
    val seasons = (0 until seasonsJson.length()).mapNotNull { index ->
        val season = seasonsJson.optJSONObject(index) ?: return@mapNotNull null
        val number = season.optInt("number")
        val sourceId = season.optInt("id")
        if (number <= 0 || sourceId <= 0) null
        else Season(number, season.optString("name").ifBlank { "الموسم " + number },
            season.optInt("episodeOrder"), sourceId)
    }
    val runtimeValue = show.optInt("averageRuntime")
    val runtime = if (runtimeValue > 0) "$runtimeValue دقيقة للحلقة" else "غير محدد"
    return WorkDetails(
        cleanHtml(show.optString("summary")).ifBlank { work.overview },
        genreNames, runtime, seasons, emptyList(), work.externalUrl.orEmpty()
    )
}

private suspend fun loadDetails(context: Context, work: Work): WorkDetails? {
    if (work.source == "TVmaze") return loadTvMazeDetails(work)
    if (work.source != "TMDB") {
        return WorkDetails(work.overview, emptyList(), "غير متاح من هذا المصدر", emptyList(), emptyList(), work.externalUrl.orEmpty())
    }
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
    val providerPath = if (work.mediaType == "tv") "tv/" + work.id + "/watch/providers" else "movie/" + work.id + "/watch/providers"
    val providersJson = apiJson(context, providerPath)?.optJSONObject("results")?.optJSONObject("EG")
    val providerLink = providersJson?.optString("link").orEmpty()
    val providerRows = mutableListOf<WatchProvider>()
    listOf("flatrate", "free", "ads", "rent", "buy").forEach { group ->
        val array = providersJson?.optJSONArray(group) ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("provider_name")
            val logoPath = item.optString("logo_path")
            if (name.isNotBlank()) {
                val logo = if (logoPath.isBlank() || logoPath == "null") null else IMAGE_BASE + "w92" + logoPath
                providerRows.add(WatchProvider(name, logo, providerLink))
            }
        }
    }
    return WorkDetails(json.optString("overview").ifBlank { work.overview }, genres, runtime, seasons, providerRows.distinctBy { it.name }, providerLink)
}

private suspend fun loadEpisodes(context: Context, work: Work, season: Season): List<Episode> {
    if (work.source == "TVmaze") {
        if (season.sourceId <= 0) return emptyList()
        val publicRows = publicJsonArray("https://api.tvmaze.com/seasons/" + season.sourceId + "/episodes") ?: return emptyList()
        return (0 until publicRows.length()).mapNotNull { index ->
            publicRows.optJSONObject(index)?.let { item ->
                val image = item.optJSONObject("image")
                val still = image?.optString("medium").orEmpty().ifBlank { image?.optString("original").orEmpty() }
                val number = item.optInt("number")
                Episode(number, item.optString("name").ifBlank { "الحلقة " + number },
                    cleanHtml(item.optString("summary")), still.ifBlank { null })
            }
        }
    }
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
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var launchStage by remember { mutableStateOf("splash") }
    var isLightMode by remember { mutableStateOf(prefs.getBoolean("light_mode", false)) }
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
        prefs.edit().putStringSet("favorites", next).apply()
    }

    suspend fun refreshCatalog() {
        loading = true
        error = ""
        val loaded = runCatching { loadCatalog(context) }
        catalog = loaded.getOrDefault(Catalog())
        loading = false
        if (loaded.isFailure || (catalog.trending + catalog.openShows + catalog.publicDomain + catalog.openVideos).isEmpty()) {
            error = "تعذر الاتصال بالمصادر الآن. تحقق من الإنترنت ثم حاول التحديث."
        } else if (getApiKey(context).isBlank()) {
            error = "المصادر المجانية تعمل بدون مفتاح. أضف TMDB اختياريًا لتوسيع بيانات الأفلام والصور."
        }
    }

    LaunchedEffect(Unit) {
        delay(1050)
        launchStage = if (prefs.getBoolean("intro_complete", false)) "app" else "intro"
    }
    LaunchedEffect(launchStage) {
        if (launchStage == "app") refreshCatalog()
    }
    LaunchedEffect(query, launchStage) {
        if (launchStage != "app") return@LaunchedEffect
        if (query.trim().length < 2) {
            searchResults = emptyList()
            searchStarted = false
            searchBusy = false
        } else {
            delay(450)
            searchBusy = true
            searchStarted = true
            val rows = coroutineScope {
                val tmdb = async { apiWorks(context, "search/multi?query=" + URLEncoder.encode(query.trim(), "UTF-8")) }
                val open = async { searchPublicSources(query.trim()) }
                dedupeWorks(tmdb.await().filter { it.mediaType == "movie" || it.mediaType == "tv" } + open.await())
            }
            searchResults = rows
            searchBusy = false
        }
    }

    val palette = if (isLightMode) LightPalette else DarkPalette
    CompositionLocalProvider(
        LocalUiPalette provides palette,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = if (isLightMode) {
                lightColorScheme(
                    primary = Accent, background = palette.background, surface = palette.surface,
                    onBackground = palette.text, onSurface = palette.text
                )
            } else {
                darkColorScheme(
                    primary = Accent, background = palette.background, surface = palette.surface,
                    onBackground = palette.text, onSurface = palette.text
                )
            }
        ) {
            Surface(Modifier.fillMaxSize(), color = ink()) {
                when (launchStage) {
                    "splash" -> SplashScreen()
                    "intro" -> IntroScreen(
                        onFinish = {
                            prefs.edit().putBoolean("intro_complete", true).apply()
                            launchStage = "app"
                        }
                    )
                    else -> {
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
                                        0 -> HomeScreen(
                                            catalog, loading, error,
                                            { selected = it },
                                            { scope.launch { refreshCatalog() } }
                                        )
                                        1 -> SearchScreen(
                                            query, { query = it }, searchResults, searchBusy, searchStarted
                                        ) { selected = it }
                                        2 -> LibraryScreen(catalog, favorites) { selected = it }
                                        else -> SettingsScreen(
                                            hasKey = getApiKey(context).isNotBlank(),
                                            input = keyInput,
                                            onInput = { keyInput = it },
                                            onSave = {
                                                prefs.edit().putString("tmdb_key", keyInput.trim()).apply()
                                                keyInput = ""
                                                scope.launch { refreshCatalog() }
                                            },
                                            onRefresh = { scope.launch { refreshCatalog() } },
                                            isLightMode = isLightMode,
                                            onThemeChange = { light ->
                                                isLightMode = light
                                                prefs.edit().putBoolean("light_mode", light).apply()
                                            }
                                        )
                                    }
                                }
                                NavigationBar(
                                    containerColor = surfaceDark(),
                                    contentColor = primaryText()
                                ) {
                                    val labels = listOf("الرئيسية", "بحث", "قائمتي", "الإعدادات")
                                    val icons = listOf(
                                        Icons.Default.Home, Icons.Default.Search,
                                        Icons.Default.Favorite, Icons.Default.Settings
                                    )
                                    labels.forEachIndexed { index, label ->
                                        NavigationBarItem(
                                            selected = tab == index,
                                            onClick = { tab = index },
                                            icon = { Icon(icons[index], null) },
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Accent,
                                                selectedTextColor = primaryText(),
                                                indicatorColor = Color(0x33E50914),
                                                unselectedIconColor = muted(),
                                                unselectedTextColor = muted()
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandMark(size: Int = 62) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 3).dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF3545), Accent, Color(0xFF8D0010)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PlayArrow, contentDescription = "YOFECT PLAY",
            tint = Color.White, modifier = Modifier.size((size * 0.66f).dp)
        )
    }
}

@Composable
private fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF351019), Color(0xFF130B10), ink()),
                radius = 900f
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(88)
            Spacer(Modifier.height(22.dp))
            Text(
                "YOFECT", color = primaryText(), fontSize = 34.sp,
                fontWeight = FontWeight.Black, letterSpacing = 4.sp
            )
            Text(
                "PLAY", color = Accent, fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 9.sp
            )
            Spacer(Modifier.height(34.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(23.dp), color = Accent, strokeWidth = 2.dp
            )
            Spacer(Modifier.height(12.dp))
            Text("عالمك من الأفلام والمسلسلات", color = muted(), fontSize = 12.sp)
        }
        Text(
            "اكتشف حكايتك القادمة",
            color = muted(), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun IntroScreen(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("كل عالم الترفيه في مكان واحد", "اكتشف ما تحب بسهولة", "مصادر أوضح وتجربة أذكى")
    val descriptions = listOf(
        "استكشف الأفلام والمسلسلات العربية والتركية والهندية والعالمية من مكتبة واحدة.",
        "ابحث بالاسم، تصفح التصنيفات، وافتح صفحة التفاصيل والمواسم والحلقات المتاحة.",
        "نعرض بيانات العمل ومصادر المشاهدة المتاحة بوضوح، من غير روابط تشغيل وهمية أو تكرار للنتائج."
    )
    val symbols = listOf(Icons.Default.Movie, Icons.Default.Explore, Icons.Default.Info)
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF260D14), ink(), ink()))
        ).padding(horizontal = 26.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinish) { Text("تخطي", color = muted()) }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(174.dp).clip(RoundedCornerShape(42.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF581522), Color(0xFF1C1117)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(symbols[page], null, tint = Accent, modifier = Modifier.size(78.dp))
            BrandMark(44)
        }
        Spacer(Modifier.height(38.dp))
        Text(
            titles[page], color = primaryText(), fontSize = 24.sp,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            descriptions[page], color = muted(), fontSize = 14.sp,
            lineHeight = 23.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier.width(if (index == page) 27.dp else 7.dp).height(7.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (index == page) Accent else surfaceMid())
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (page < 2) page++ else onFinish() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text(if (page == 2) "ابدأ استخدام YOFECT PLAY" else "التالي", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowBack, null)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "مكتبة اكتشاف ومشاهدة حسب المصادر المتاحة",
            color = muted(), fontSize = 10.sp
        )
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
        else -> catalog.trending + catalog.movies + catalog.shows + catalog.openShows + catalog.publicDomain + catalog.openVideos
    }.let { dedupeWorks(it) }
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
            if (catalog.trending.isNotEmpty()) item { PosterSection("مختارات لك", dedupeWorks(catalog.trending), onOpen) }
            if (catalog.movies.isNotEmpty()) item { PosterSection("أفلام شائعة", dedupeWorks(catalog.movies), onOpen) }
            if (catalog.shows.isNotEmpty()) item { PosterSection("مسلسلات شائعة", dedupeWorks(catalog.shows), onOpen) }
            if (catalog.openShows.isNotEmpty()) item { PosterSection("مسلسلات من TVmaze • بدون مفتاح", catalog.openShows, onOpen) }
            if (catalog.publicDomain.isNotEmpty()) item { PosterSection("أفلام بترخيص ملكية عامة", catalog.publicDomain, onOpen) }
            if (catalog.openVideos.isNotEmpty()) item { PosterSection("فيديوهات بتراخيص مفتوحة", catalog.openVideos, onOpen) }
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
            item { NoticeCard("لا توجد نتائج في هذا التصنيف حاليًا. جرّب تحديث المصادر أو البحث باسم العمل.") }
        }
        item {
            Text("TVmaze وInternet Archive وWikimedia Commons تعمل دون مفتاح. لا نعرض إلا روابط المصدر والترخيص المتاح؛ تشغيل المحتوى محكوم بحقوق كل عمل.", color = muted(), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }
    }
}

@Composable
private fun WelcomeBanner() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(190.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF5D1322), Color(0xFF21131B), surfaceDark()))), contentAlignment = Alignment.BottomStart) {
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
        if (subtitle != null) Text(subtitle, color = muted(), fontSize = 11.sp)
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
            AsyncImage(work.poster, work.title, Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(12.dp)).background(surfaceMid()), contentScale = ContentScale.Crop)
            if (work.rating > 0) {
                Row(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xD9000000)).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.size(11.dp))
                    Text(" " + String.format("%.1f", work.rating), color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Text(work.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text((if (work.mediaType == "tv") "مسلسل" else "فيلم") + " • " + work.year, color = muted(), fontSize = 10.sp)
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
            !started -> Text("ابحث في مكتبة الأفلام والمسلسلات.", color = muted(), modifier = Modifier.padding(20.dp))
            results.isEmpty() -> Text("لا توجد نتائج مطابقة.", color = muted(), modifier = Modifier.padding(20.dp))
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 10.dp)) {
                items(results.distinctBy { favoriteKey(it) }) { WorkListRow(it, onOpen) }
            }
        }
    }
}

@Composable
private fun WorkListRow(work: Work, onOpen: (Work) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(work) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(work.poster, work.title, Modifier.size(88.dp, 118.dp).clip(RoundedCornerShape(10.dp)).background(surfaceMid()), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(work.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text((if (work.mediaType == "tv") "مسلسل" else "فيلم") + " • " + work.year + " • " + work.language, color = muted(), fontSize = 10.sp)
            Text(work.overview.ifBlank { "لا يوجد ملخص متاح." }, color = Color.LightGray, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("عرض التفاصيل", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryScreen(catalog: Catalog, favorites: Set<String>, onOpen: (Work) -> Unit) {
    val all = dedupeWorks(catalog.trending + catalog.movies + catalog.shows + catalog.arabic + catalog.turkish + catalog.indian + catalog.international + catalog.openShows + catalog.publicDomain + catalog.openVideos)
    val saved = all.filter { favorites.contains(favoriteKey(it)) }
    Column(Modifier.fillMaxSize()) {
        Text("قائمتي", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        if (saved.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.FavoriteBorder, null, tint = Accent, modifier = Modifier.size(52.dp))
                Text("قائمتك فاضية", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text("افتح أي عمل واضغط القلب لإضافته لقائمتك.", color = muted(), fontSize = 12.sp)
            }
        } else {
            LazyColumn { items(saved) { WorkListRow(it, onOpen) } }
        }
    }
}

@Composable
private fun DetailScreen(work: Work, favorite: Boolean, onBack: () -> Unit, onFavorite: () -> Unit) {
    val context = LocalContext.current
    var details by remember(work.id, work.mediaType, work.source) { mutableStateOf<WorkDetails?>(null) }
    var loading by remember(work.id, work.mediaType, work.source) { mutableStateOf(true) }
    var selectedSeason by remember(work.id, work.mediaType) { mutableStateOf<Season?>(null) }
    var episodes by remember(work.id, work.mediaType) { mutableStateOf<List<Episode>>(emptyList()) }
    var episodesLoading by remember { mutableStateOf(false) }

    LaunchedEffect(work.id, work.mediaType, work.source) {
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
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), ink()))))
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
                Text("مصدر البيانات: ${work.source}", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (details != null) {
                    Text("النوع: " + details!!.genres.joinToString(" • ").ifBlank { "غير محدد" }, color = muted(), fontSize = 11.sp)
                    Text("المدة: " + details!!.runtime, color = muted(), fontSize = 11.sp)
                }
                if (details?.providers?.isNotEmpty() == true) {
                    Text("منصات متاحة في مصر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    details!!.providers.forEach { provider ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(surfaceDark())
                                .clickable { openProvider(context, provider.link, work.title) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(provider.logo, provider.name, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Text(provider.name, color = Color.White, modifier = Modifier.weight(1f).padding(start = 10.dp), fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.OpenInNew, null, tint = muted())
                        }
                    }
                    Button(onClick = { openProvider(context, details!!.providerLink, work.title) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) {
                        Text("فتح خيارات المشاهدة")
                    }
                } else {
                    if (work.source != "TMDB" && !work.externalUrl.isNullOrBlank()) {
                        Button(onClick = { openProvider(context, work.externalUrl.orEmpty(), work.title) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.OpenInNew, null)
                            Spacer(Modifier.width(8.dp))
                            Text("فتح صفحة العمل في المصدر")
                        }
                        Text("المصدر: ${work.source} • ${work.sourceLicense}", color = muted(), fontSize = 10.sp)
                    } else {
                        Button(onClick = { openWhereToWatch(context, work.title) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("ابحث عن منصة مشاهدة رسمية")
                        }
                        Text("لم تتوفر بيانات منصات المشاهدة لهذا العمل في مصر. البحث الخارجي قد يعرض خيارات أخرى.", color = muted(), fontSize = 10.sp)
                    }
                }
                Text("YOFECT PLAY لا يضيف بثًا غير مرخص. التشغيل والجودة داخل التطبيق يتطلبان تكاملًا مع مزود يملك حقوق العرض.", color = muted(), fontSize = 10.sp)
            }
        }
        if (work.mediaType == "tv") {
            item { SectionHeader("المواسم والحلقات") }
            if (loading) item { LoadingBlock() }
            if (!loading && details?.seasons.isNullOrEmpty()) item { NoticeCard("لم تتوفر معلومات المواسم لهذا المسلسل في قاعدة البيانات.") }
            items(details?.seasons.orEmpty()) { season ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(surfaceDark()).clickable { selectedSeason = season }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(season.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(season.episodes.toString() + " حلقة", color = muted(), fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronLeft, null, tint = Accent)
                }
            }
            if (selectedSeason != null) {
                item { SectionHeader(selectedSeason!!.name) }
                if (episodesLoading) item { LoadingBlock() }
                if (!episodesLoading && episodes.isEmpty()) item { NoticeCard("لا توجد بيانات حلقات متاحة لهذا الموسم.") }
                items(episodes) { episode ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).background(surfaceDark(), RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(episode.still ?: work.poster, episode.name, Modifier.size(104.dp, 66.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("الحلقة " + episode.number + ": " + episode.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(episode.overview.ifBlank { "لا يتوفر وصف للحلقة." }, color = muted(), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun openProvider(context: Context, url: String, title: String) {
    val target = url.ifBlank { "https://www.justwatch.com/eg/search?q=" + URLEncoder.encode(title, "UTF-8") }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))) }
}
private fun openWhereToWatch(context: Context, title: String) {
    openProvider(context, "", title)
}

@Composable
private fun SettingsScreen(
    hasKey: Boolean,
    input: String,
    onInput: (String) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    isLightMode: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    var revealKey by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("الإعدادات", color = primaryText(), fontSize = 27.sp, fontWeight = FontWeight.Black)
            Text("خصص تجربتك وتابع حالة مصادر المكتبة", color = muted(), fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(surfaceDark()).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(54)
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text("YOFECT PLAY", color = primaryText(), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("ترفيهك… بطريقتك", color = muted(), fontSize = 11.sp)
            }
            Surface(color = if (hasKey) Color(0xFF123C2D) else Color(0xFF493318), shape = RoundedCornerShape(20.dp)) {
                Text(
                    if (hasKey) "كل المصادر جاهزة" else "المجاني يعمل",
                    color = Color(0xFF8AE0B2),
                    fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                )
            }
        }

        Text("المظهر", color = primaryText(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(surfaceDark()).clickable { onThemeChange(!isLightMode) }.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (isLightMode) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = Accent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("الوضع الفاتح", color = primaryText(), fontWeight = FontWeight.SemiBold)
                Text(if (isLightMode) "الخلفية الفاتحة مفعّلة" else "المظهر الداكن مفعّل", color = muted(), fontSize = 10.sp)
            }
            Switch(checked = isLightMode, onCheckedChange = onThemeChange)
        }

        Text("مصادر المكتبة", color = primaryText(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(surfaceDark()).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF55D69A))
                    Text("TVmaze • المسلسلات والمواسم والحلقات • بدون مفتاح", color = primaryText(), fontSize = 11.sp, modifier = Modifier.padding(start = 9.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF55D69A))
                    Text("Internet Archive • أعمال بترخيص ملكية عامة • بدون مفتاح", color = primaryText(), fontSize = 11.sp, modifier = Modifier.padding(start = 9.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF55D69A))
                    Text("Wikimedia Commons • فيديوهات بتراخيص مفتوحة • بدون مفتاح", color = primaryText(), fontSize = 11.sp, modifier = Modifier.padding(start = 9.dp))
                }
                Divider(color = surfaceMid())
                Text("TMDB اختياري: يضيف بيانات وصورًا أوسع للأفلام والمسلسلات، لكنه ليس شرطًا لتشغيل المصادر المجانية.", color = muted(), fontSize = 11.sp, lineHeight = 18.sp)
            }
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("TMDB API Key") },
                placeholder = { Text("الصق مفتاح API هنا") },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "إظهار المفتاح")
                    }
                }
            )
            Button(
                onClick = onSave,
                enabled = input.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Icon(Icons.Default.VpnKey, null)
                Spacer(Modifier.width(8.dp))
                Text("حفظ المفتاح وتحديث المكتبة")
            }
            Text(
                if (hasKey) "TMDB مفعّل بالإضافة إلى المصادر المجانية." else "TMDB غير مفعّل، لكن المصادر العامة تعمل بدون مفتاح.",
                color = Color(0xFF58C98C),
                fontSize = 10.sp
            )
        }

        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().height(49.dp),
            shape = RoundedCornerShape(13.dp)
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("تحديث بيانات المكتبة")
        }

        Text("عن التطبيق", color = primaryText(), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(surfaceDark()).clickable { showAbout = true }.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, null, tint = Accent)
            Text("عن YOFECT PLAY وسياسة مصادر المشاهدة", color = primaryText(), modifier = Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 12.sp)
            Icon(Icons.Default.ChevronLeft, null, tint = muted())
        }
        Text(
            "TMDB يوفّر البيانات والصور، وليس ملفات الأفلام أو حقوق بث الحلقات. روابط المشاهدة تُعرض وفق ما توفره المصادر الرسمية. This product uses the TMDB API but is not endorsed or certified by TMDB.",
            color = muted(), fontSize = 10.sp, lineHeight = 16.sp
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("حسنًا") } },
            title = { Text("YOFECT PLAY") },
            text = {
                Text(
                    "منصة لاكتشاف الأفلام والمسلسلات وتجميع بياناتها من مصادر متعددة. يظهر كل عمل مرة واحدة، مع توضيح مصدر البيانات وطريقة الوصول للمشاهدة. لا يتم تشغيل محتوى محمي داخل التطبيق إلا عند توفر تكامل مرخص يسمح بذلك."
                )
            },
            containerColor = surfaceDark(),
            titleContentColor = primaryText(),
            textContentColor = muted()
        )
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
