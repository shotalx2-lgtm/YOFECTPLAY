package com.yofect.play

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val BG = Color(0xFF080808)
private val RED = Color(0xFFE50914)
private val GREY = Color(0xFF999999)
private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { YofectPlay() }
 }
}
private data class Work(val id:Int,val title:String,val overview:String,val poster:String?,val year:String,val language:String,val type:String)

private suspend fun fetchCatalog(path:String):List<Work> = withContext(Dispatchers.IO) {
 val key = BuildConfig.TMDB_API_KEY
 if (key.isBlank()) return@withContext emptyList()
 try {
  val endpoint = URL("https://api.themoviedb.org/3/$path?api_key=$key&language=ar-EG&page=1")
  val conn = endpoint.openConnection() as HttpURLConnection
  conn.connectTimeout=12000; conn.readTimeout=12000
  val json = conn.inputStream.bufferedReader().use{it.readText()}
  conn.disconnect()
  val data = JSONObject(json).optJSONArray("results") ?: return@withContext emptyList()
  (0 until data.length()).map { i ->
   val o=data.getJSONObject(i); val pp=o.optString("poster_path")
   Work(o.optInt("id"),o.optString("title",o.optString("name","بدون عنوان")),o.optString("overview"),
    if(pp.isBlank()||pp=="null") null else IMAGE_BASE+pp,
    o.optString("release_date",o.optString("first_air_date","")).take(4),
    o.optString("original_language").uppercase(),if(path.startsWith("tv/")) "مسلسل" else "فيلم")
  }
 } catch(_:Exception){emptyList()}
}

@Composable
private fun YofectPlay() {
 var tab by remember{mutableIntStateOf(0)}
 var query by remember{mutableStateOf("")}
 var category by remember{mutableStateOf("الكل")}
 var chosen by remember{mutableStateOf<Work?>(null)}
 var movies by remember{mutableStateOf(emptyList<Work>())}
 var shows by remember{mutableStateOf(emptyList<Work>())}
 var busy by remember{mutableStateOf(true)}
 LaunchedEffect(Unit){movies=fetchCatalog("movie/popular");shows=fetchCatalog("tv/popular");busy=false}
 MaterialTheme(colorScheme=darkColorScheme(primary=RED,background=BG,surface=Color(0xFF171717))){
  Surface(Modifier.fillMaxSize(),color=BG){
   if(chosen!=null){
    Column(Modifier.fillMaxSize()){
     Row(verticalAlignment=Alignment.CenterVertically){
      IconButton(onClick={chosen=null}){Icon(Icons.Default.ArrowBack,null,tint=Color.White)}
      Text("تفاصيل العمل",color=Color.White,fontWeight=FontWeight.Bold)
     }
     AsyncImage(chosen!!.poster,chosen!!.title,Modifier.fillMaxWidth().height(300.dp).padding(16.dp).background(Color.DarkGray,RoundedCornerShape(16.dp)))
     Text(chosen!!.title,color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=20.dp))
     Text("${chosen!!.type} • ${chosen!!.year} • ${chosen!!.language}",color=GREY,modifier=Modifier.padding(20.dp))
     Text(chosen!!.overview,color=Color.LightGray,modifier=Modifier.padding(horizontal=20.dp))
     Text("بيانات التعريف متاحة؛ رابط المشاهدة يحتاج مصدرًا مرخصًا منفصلًا.",color=RED,modifier=Modifier.padding(20.dp))
    }
   }else{
    Column(Modifier.fillMaxSize()){
     Box(Modifier.weight(1f)){
      when(tab){
       0->Home(movies,shows,busy,category,{category=it},{chosen=it})
       1->Search(query,{query=it},movies+shows,{chosen=it})
       2->MessagePage(Icons.Default.Bookmarks,"مكتبتي","احفظ أعمالك المفضلة هنا.")
       else->MessagePage(Icons.Default.Person,"حساب YOFECT PLAY","إدارة الحساب والملفات الشخصية قريبًا.")
      }
     }
     NavigationBar(containerColor=Color(0xFF111111)){
      listOf("الرئيسية","بحث","مكتبتي","حسابي").forEachIndexed{index,label->
       NavigationBarItem(selected=tab==index,onClick={tab=index},
        icon={Icon(listOf(Icons.Default.Home,Icons.Default.Search,Icons.Default.Bookmarks,Icons.Default.Person)[index],null)},
        label={Text(label,fontSize=10.sp)})
      }
     }
    }
   }
  }
 }
}

@Composable
private fun Home(movies:List<Work>,shows:List<Work>,busy:Boolean,category:String,onCategory:(String)->Unit,onOpen:(Work)->Unit){
 LazyColumn(contentPadding=PaddingValues(bottom=18.dp)){
  item{
   Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
    Column{Text("YOFECT",fontSize=26.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("PLAY",color=RED,fontWeight=FontWeight.Bold,letterSpacing=5.sp)}
    Icon(Icons.Default.Cast,null,tint=Color.White)
   }
  }
  item{
   Box(Modifier.fillMaxWidth().padding(horizontal=16.dp).height(185.dp).background(Brush.linearGradient(listOf(Color(0xFF56131E),Color(0xFF1B1B1B),BG)),RoundedCornerShape(20.dp)).padding(20.dp),contentAlignment=Alignment.BottomStart){
    Column{Text("عالمك من الترفيه",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.ExtraBold);Text("أفلام ومسلسلات من كل مكان",color=Color.LightGray,fontSize=12.sp);Text("اكتشف المكتبة",color=RED,fontWeight=FontWeight.Bold)}
   }
  }
  item{Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
   listOf("الكل","عربي","تركي","أجنبي","أفلام","مسلسلات").forEach{FilterChip(selected=category==it,onClick={onCategory(it)},label={Text(it)})}
  }}
  if(busy)item{Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=RED)}}
  if(!busy&&movies.isEmpty()&&shows.isEmpty())item{Text("أضف TMDB_API_KEY إلى أسرار GitHub لتفعيل تحميل كتالوج الأعمال.",color=GREY,modifier=Modifier.padding(20.dp))}
  if(category!="مسلسلات"){item{SectionTitle("أفلام شائعة")};items(filterWorks(movies,category)){WorkRow(it){onOpen(it)}}}
  if(category!="أفلام"){item{SectionTitle("مسلسلات شائعة")};items(filterWorks(shows,category)){WorkRow(it){onOpen(it)}}}
  item{Text("المعلومات والبوسترات من TMDB. لا تتضمن هذه النسخة بث فيديو أو حقوق مشاهدة.",color=GREY,fontSize=10.sp,modifier=Modifier.padding(18.dp))}
 }
}
@Composable private fun SectionTitle(s:String){Text(s,color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(18.dp))}
private fun filterWorks(list:List<Work>,category:String)=when(category){
 "عربي"->list.filter{it.language=="AR"}
 "تركي"->list.filter{it.language=="TR"}
 "أجنبي"->list.filter{it.language!="AR"&&it.language!="TR"}
 else->list
}
@Composable
private fun WorkRow(work:Work,onClick:()->Unit){
 Row(Modifier.fillMaxWidth().clickable{onClick()}.padding(horizontal=18.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
  AsyncImage(work.poster,work.title,Modifier.size(94.dp,124.dp).background(Color(0xFF202020),RoundedCornerShape(9.dp)))
  Spacer(Modifier.width(13.dp))
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
   Text(work.title,color=Color.White,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
   Text("${work.type} • ${work.year} • ${work.language}",color=GREY,fontSize=11.sp)
   Text(work.overview.ifBlank{"لا يتوفر وصف حاليًا."},color=Color.LightGray,fontSize=11.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
  }
  Icon(Icons.Default.ChevronLeft,null,tint=GREY)
 }
}
@Composable
private fun Search(query:String,onQuery:(String)->Unit,all:List<Work>,onOpen:(Work)->Unit){
 Column{
  Text("البحث",color=Color.White,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(20.dp))
  OutlinedTextField(query,onQuery,Modifier.fillMaxWidth().padding(horizontal=16.dp),placeholder={Text("ابحث عن فيلم أو مسلسل")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true)
  all.filter{query.isNotBlank()&&it.title.contains(query,true)}.forEach{WorkRow(it){onOpen(it)}}
 }
}
@Composable
private fun MessagePage(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,subtitle:String){
 Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
  Icon(icon,null,tint=RED,modifier=Modifier.size(48.dp))
  Text(title,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp))
  Text(subtitle,color=GREY,modifier=Modifier.padding(top=8.dp))
 }
}
