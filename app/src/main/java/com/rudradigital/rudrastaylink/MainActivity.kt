package com.rudradigital.rudrastaylink

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rudradigital.rudrastaylink.data.*
import com.rudradigital.rudrastaylink.notifications.OwnerNotifications
import com.rudradigital.rudrastaylink.security.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF071521)
private val InkSoft = Color(0xFF0C2230)
private val CardGlass = Color(0xCC122D3A)
private val CardGlass2 = Color(0xFF163644)
private val Teal = Color(0xFF62D9C4)
private val TealDeep = Color(0xFF1B8E87)
private val Mint = Color(0xFFB3F4E6)
private val TextMain = Color(0xFFF1F7F8)
private val TextMuted = Color(0xFF9EB5BD)
private val Amber = Color(0xFFFFC477)
private val RedSoft = Color(0xFFFF8E91)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OwnerNotifications.ensureChannel(this)
        setContent { RudraTheme { RudraApp() } }
    }
}

@Composable
private fun RudraTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Teal,
        onPrimary = Ink,
        secondary = Mint,
        background = Ink,
        surface = InkSoft,
        onBackground = TextMain,
        onSurface = TextMain,
        error = RedSoft
    )
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

private enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Dashboard),
    ROOMS("Rooms", Icons.Default.Bed),
    ENQUIRIES("Enquiries", Icons.Default.MailOutline),
    MORE("More", Icons.Default.GridView)
}

@Composable
private fun RudraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenStore = remember { TokenStore(context) }
    val preferences = remember { context.getSharedPreferences("rudra_app_settings", Context.MODE_PRIVATE) }

    var baseUrl by remember { mutableStateOf(preferences.getString("api_base_url", "https://YOURDOMAIN.pythonanywhere.com/") ?: "https://YOURDOMAIN.pythonanywhere.com/") }
    val api = remember(baseUrl) { ApiProvider.create(context, baseUrl) }
    var isSignedIn by remember { mutableStateOf(!tokenStore.accessToken().isNullOrBlank()) }
    var ownerName by remember { mutableStateOf("") }
    var dashboard by remember { mutableStateOf(DashboardDto()) }
    var properties by remember { mutableStateOf(emptyList<PropertyDto>()) }
    var rooms by remember { mutableStateOf(emptyList<RoomDto>()) }
    var enquiries by remember { mutableStateOf(emptyList<EnquiryDto>()) }
    var notifications by remember { mutableStateOf(emptyList<NotificationDto>()) }
    var seenNotificationIds by remember { mutableStateOf(setOf<Int>()) }
    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var showNotifications by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPropertyDialog by remember { mutableStateOf(false) }
    var editingProperty by remember { mutableStateOf<PropertyDto?>(null) }
    var showRoomDialog by remember { mutableStateOf(false) }
    var editingRoom by remember { mutableStateOf<RoomDto?>(null) }
    var statusRoom by remember { mutableStateOf<RoomDto?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    suspend fun reloadAll(notifyNew: Boolean = false, includeArchived: Boolean = false) {
        val oldIds = seenNotificationIds
        val dash = api.dashboard()
        dashboard = dash
        ownerName = dash.ownerName
        properties = api.properties().results
        rooms = api.rooms(includeArchived = if (includeArchived) "true" else null).results
        enquiries = api.enquiries().results
        notifications = api.notifications().results
        val unread = notifications.filter { !it.isRead }
        if (notifyNew) {
            unread.filter { it.id !in oldIds }.forEach {
                OwnerNotifications.show(context, it.id, it.title, it.message)
            }
        }
        seenNotificationIds = notifications.map { it.id }.toSet()
    }

    fun launchJob(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            message = null
            try {
                block()
            } catch (error: Exception) {
                message = friendlyError(error)
                if (error is HttpException && error.code() == 401) {
                    tokenStore.clear()
                    isSignedIn = false
                }
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(isSignedIn, api) {
        if (isSignedIn) {
            loading = true
            try {
                reloadAll(notifyNew = false)
            } catch (error: Exception) {
                message = friendlyError(error)
                if (error is HttpException && error.code() == 401) {
                    tokenStore.clear()
                    isSignedIn = false
                }
            } finally {
                loading = false
            }
        }
    }

    // Lightweight foreground polling. Background push needs the owner's Firebase project credentials.
    LaunchedEffect(isSignedIn, api) {
        if (isSignedIn) {
            while (true) {
                delay(30_000)
                try {
                    val previousIds = seenNotificationIds
                    val latest = api.notifications().results
                    val newUnread = latest.filter { !it.isRead && it.id !in previousIds }
                    newUnread.forEach { OwnerNotifications.show(context, it.id, it.title, it.message) }
                    notifications = latest
                    seenNotificationIds = latest.map { it.id }.toSet()
                    dashboard = api.dashboard()
                    if (newUnread.isNotEmpty()) enquiries = api.enquiries().results
                } catch (error: Exception) {
                    if (error is HttpException && error.code() == 401) {
                        tokenStore.clear()
                        isSignedIn = false
                        message = "Your session expired. Please sign in again."
                    }
                    // Non-auth network errors do not interrupt the current screen.
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B2734), Ink, Color(0xFF06111C))))) {
        if (!isSignedIn) {
            LoginScreen(
                baseUrl = baseUrl,
                onBaseUrlChange = { baseUrl = it },
                message = message,
                loading = loading,
                onLogin = { username, password, server ->
                    launchJob {
                        val normalized = ApiProvider.normalizeBaseUrl(server)
                        val loginApi = ApiProvider.create(context, normalized)
                        val pair = loginApi.login(mapOf("username" to username.trim(), "password" to password))
                        tokenStore.save(pair.access, pair.refresh)
                        preferences.edit().putString("api_base_url", normalized).apply()
                        baseUrl = normalized
                        ownerName = pair.user?.firstName?.takeIf { it.isNotBlank() } ?: pair.user?.username.orEmpty()
                        isSignedIn = true
                        activeTab = MainTab.HOME
                    }
                }
            )
        } else {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopBar(
                        title = if (showNotifications) "Notifications" else "Rudra StayLink",
                        subtitle = if (showNotifications) "Owner updates and guest requests" else "Owner workspace",
                        unread = notifications.count { !it.isRead },
                        onNotificationClick = { showNotifications = !showNotifications },
                        showBack = showNotifications,
                        onBack = { showNotifications = false }
                    )
                },
                bottomBar = {
                    if (!showNotifications) BottomNavigationBar(activeTab) { activeTab = it }
                },
                snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (showNotifications) {
                        NotificationsScreen(
                            notifications = notifications,
                            onRead = { id -> launchJob { api.markNotificationRead(id); reloadAll() } },
                            onReadAll = { launchJob { api.markAllNotificationsRead(); reloadAll() } }
                        )
                    } else when (activeTab) {
                        MainTab.HOME -> DashboardScreen(
                            ownerName = ownerName.ifBlank { dashboard.ownerName }, dashboard = dashboard,
                            onRefresh = { launchJob { reloadAll() } },
                            onRooms = { activeTab = MainTab.ROOMS },
                            onEnquiries = { activeTab = MainTab.ENQUIRIES },
                            onAddRoom = { editingRoom = null; showRoomDialog = true },
                            onManageProperties = { activeTab = MainTab.MORE }
                        )
                        MainTab.ROOMS -> RoomsScreen(
                            rooms = rooms,
                            onAdd = { editingRoom = null; showRoomDialog = true },
                            onEdit = { editingRoom = it; showRoomDialog = true },
                            onStatus = { statusRoom = it },
                            onArchive = { room -> launchJob { api.archiveRoom(room.id); reloadAll() } },
                            onRestore = { room -> launchJob { api.restoreRoom(room.id); reloadAll(includeArchived = true) } },
                            onRefresh = { includeArchived -> launchJob { rooms = api.rooms(includeArchived = if (includeArchived) "true" else null).results } }
                        )
                        MainTab.ENQUIRIES -> EnquiriesScreen(
                            enquiries = enquiries,
                            onStatus = { enquiry, status -> launchJob { api.updateEnquiryStatus(enquiry.id, EnquiryStatusRequest(status)); reloadAll() } }
                        )
                        MainTab.MORE -> MoreScreen(
                            properties = properties,
                            onAddProperty = { editingProperty = null; showPropertyDialog = true },
                            onEditProperty = { editingProperty = it; showPropertyDialog = true },
                            onChangePassword = { showPasswordDialog = true },
                            onLogout = {
                                launchJob {
                                    try { tokenStore.refreshToken()?.let { api.logout(mapOf("refresh" to it)) } } catch (_: Exception) { }
                                    tokenStore.clear()
                                    isSignedIn = false
                                    showNotifications = false
                                }
                            },
                            baseUrl = baseUrl,
                            onServerChange = { newUrl ->
                                try {
                                    baseUrl = ApiProvider.normalizeBaseUrl(newUrl)
                                    preferences.edit().putString("api_base_url", baseUrl).apply()
                                    message = "Server address saved. Reconnect to use the new server."
                                } catch (e: Exception) { message = e.message }
                            }
                        )
                    }
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Teal, trackColor = Color.Transparent)
                    }
                    message?.let { text ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp), color = Color(0xFF263C47), border = BorderStroke(1.dp, Teal.copy(alpha = .3f))
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Teal)
                                Spacer(Modifier.width(10.dp))
                                Text(text, color = TextMain, modifier = Modifier.weight(1f), fontSize = 13.sp)
                                IconButton(onClick = { message = null }) { Icon(Icons.Default.Close, null, tint = TextMuted) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPropertyDialog) {
        PropertyDialog(
            property = editingProperty,
            onDismiss = { showPropertyDialog = false },
            onSave = { form ->
                launchJob {
                    val request = PropertyRequest(form.name, form.area, form.address, form.contactPerson, form.phone, form.whatsappNumber, form.description)
                    if (editingProperty == null) api.createProperty(request) else api.updateProperty(editingProperty!!.id, request)
                    showPropertyDialog = false
                    reloadAll()
                }
            }
        )
    }
    if (showRoomDialog) {
        RoomDialog(
            properties = properties,
            room = editingRoom,
            onDismiss = { showRoomDialog = false },
            onSave = { form ->
                launchJob {
                    val propertyId = form.propertyId ?: throw IllegalArgumentException("Select a property first.")
                    val photoPart = form.photoUri?.let { makePhotoPart(context, it) }
                    val requestParts = form.toMultipartParts()
                    if (editingRoom == null) {
                        api.createRoom(propertyId.toPart(), requestParts.name, requestParts.type, requestParts.capacity, requestParts.rate, requestParts.amenities, requestParts.notes, requestParts.status, photoPart)
                    } else {
                        api.updateRoom(editingRoom!!.id, propertyId.toPart(), requestParts.name, requestParts.type, requestParts.capacity, requestParts.rate, requestParts.amenities, requestParts.notes, requestParts.status, photoPart)
                    }
                    showRoomDialog = false
                    reloadAll()
                }
            }
        )
    }
    statusRoom?.let { room ->
        AvailabilityDialog(room, onDismiss = { statusRoom = null }, onSave = { status, guest, checkout ->
            launchJob {
                api.setAvailability(room.id, AvailabilityRequest(status, guest, checkout.takeIf { status == "OCCUPIED" }))
                statusRoom = null
                reloadAll()
            }
        })
    }
    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSave = { current, new -> launchJob {
                val pair = api.changePassword(ChangePasswordRequest(current, new))
                tokenStore.save(pair.access, pair.refresh)
                showPasswordDialog = false
                message = "Password updated successfully."
            } }
        )
    }
}

@Composable
private fun LoginScreen(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    message: String?,
    loading: Boolean,
    onLogin: (String, String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(46.dp))
        Box(Modifier.size(86.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF215F67), Color(0xFF0F2D3A)))).borderGlow(Teal), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Hotel, null, tint = Teal, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("RUDRA", color = TextMain, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
        Text("STAYLINK", color = Teal, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
        Spacer(Modifier.height(8.dp))
        Text("Your property. Your rooms. One simple app.", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(34.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Owner sign in", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Use the username and password provided by your StayLink operator.", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            RudraTextField(username, { username = it }, "Username", Icons.Default.Person, keyboard = KeyboardType.Text)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Teal) },
                trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextMuted) } },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                colors = textFieldColors()
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onLogin(username, password, baseUrl) }, modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = !loading && username.isNotBlank() && password.isNotBlank() && !baseUrl.contains("YOURDOMAIN"),
                colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(16.dp)
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
                else { Text("Sign in securely", color = Ink, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ArrowForward, null, tint = Ink) }
            }
            TextButton(onClick = { showConnection = !showConnection }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.Cloud, null, tint = Teal, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Connection settings", color = Teal)
            }
            AnimatedVisibility(showConnection) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("API server address", color = TextMuted, fontSize = 12.sp)
                    OutlinedTextField(value = baseUrl, onValueChange = onBaseUrlChange, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("https://your-domain.pythonanywhere.com/") }, colors = textFieldColors())
                    Text("For a phone on the same Wi-Fi, use your PC's LAN address, for example http://192.168.1.5:8000/ . For production, use your HTTPS PythonAnywhere address.", color = TextMuted, fontSize = 11.sp)
                }
            }
            if (message != null) Text(message, color = RedSoft, fontSize = 12.sp)
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, null, tint = Teal, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("Private owner access  ·  Secure connection", color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun TopBar(title: String, subtitle: String, unread: Int, onNotificationClick: () -> Unit, showBack: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextMain) }
        else Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF153C49)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Hotel, null, tint = Teal) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 11.sp)
        }
        if (!showBack) BadgedBox(badge = { if (unread > 0) Badge(containerColor = Teal, contentColor = Ink) { Text(if (unread > 9) "9+" else unread.toString()) } }) {
            IconButton(onClick = onNotificationClick) { Icon(Icons.Default.NotificationsNone, null, tint = TextMain) }
        }
    }
}

@Composable
private fun BottomNavigationBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF0B202C), contentColor = TextMuted, tonalElevation = 0.dp) {
        MainTab.values().forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, null) },
                label = { Text(tab.title, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = Ink, selectedTextColor = Teal, indicatorColor = Teal, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted)
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    ownerName: String, dashboard: DashboardDto, onRefresh: () -> Unit, onRooms: () -> Unit,
    onEnquiries: () -> Unit, onAddRoom: () -> Unit, onManageProperties: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(start = 17.dp, end = 17.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Namaskar, ${ownerName.ifBlank { "Owner" }}", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Here is your stay overview today.", color = TextMuted, fontSize = 13.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null, tint = Teal) }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth(), gradient = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ROOMS AT A GLANCE", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text(dashboard.rooms.total.toString(), color = TextMain, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Total rooms in your properties", color = TextMuted, fontSize = 12.sp)
                    }
                    Box(Modifier.size(65.dp).clip(CircleShape).background(Teal.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Apartment, null, tint = Teal, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMetric("Available", dashboard.rooms.available, Teal, Modifier.weight(1f))
                    MiniMetric("Occupied", dashboard.rooms.occupied, Amber, Modifier.weight(1f))
                    MiniMetric("Repair", dashboard.rooms.maintenance, RedSoft, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Add room", Icons.Default.AddCircle, onAddRoom, Modifier.weight(1f))
                ActionTile("My rooms", Icons.Default.Bed, onRooms, Modifier.weight(1f))
                ActionTile("Enquiries", Icons.Default.MailOutline, onEnquiries, Modifier.weight(1f))
            }
        }
        item { SectionHeader("MY PROPERTIES", "${dashboard.propertiesCount} listed", onManageProperties) }
        if (dashboard.properties.isEmpty()) {
            item { EmptyState("No property added yet", "Ask your StayLink operator to add your first property, or use More → Properties.", Icons.Default.HomeWork) }
        } else {
            items(dashboard.properties) { property ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C4B55)), contentAlignment = Alignment.Center) { Icon(Icons.Default.HomeWork, null, tint = Teal) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(property.name, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(property.area, color = TextMuted, fontSize = 12.sp)
                        }
                        Text("${property.roomsCount} rooms", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { SectionHeader("RECENT ENQUIRIES", "${dashboard.enquiries.new} new", onEnquiries) }
        if (dashboard.recentEnquiries.isEmpty()) item { EmptyState("No enquiries yet", "New guest enquiries from the StayLink operator will appear here.", Icons.Default.MailOutline) }
        else items(dashboard.recentEnquiries) { EnquiryCard(it, compact = true) }
    }
}

@Composable
private fun RoomsScreen(
    rooms: List<RoomDto>, onAdd: () -> Unit, onEdit: (RoomDto) -> Unit,
    onStatus: (RoomDto) -> Unit, onArchive: (RoomDto) -> Unit,
    onRestore: (RoomDto) -> Unit, onRefresh: (Boolean) -> Unit
) {
    var filter by remember { mutableStateOf("ALL") }
    var search by remember { mutableStateOf("") }
    var includeArchived by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf<RoomDto?>(null) }
    val filtered = rooms.filter {
        if (includeArchived) !it.isActive
        else it.isActive && (filter == "ALL" || it.status == filter) &&
            (search.isBlank() || it.name.contains(search, true) || it.propertyName.contains(search, true) || it.roomType.contains(search, true))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 15.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("My rooms", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("Update rates and availability", color = TextMuted, fontSize = 12.sp) }
            IconButton(onClick = { onRefresh(includeArchived) }) { Icon(Icons.Default.Refresh, null, tint = Teal) }
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp), enabled = !includeArchived) { Icon(Icons.Default.Add, null, tint = Ink); Spacer(Modifier.width(4.dp)); Text("Add", color = Ink, fontWeight = FontWeight.Bold) }
        }
        RudraTextField(search, { search = it }, "Search your rooms", Icons.Default.Search)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            FilterChip(selected = !includeArchived && filter == "ALL", onClick = { includeArchived = false; filter = "ALL"; onRefresh(false) }, label = { Text("All", fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal.copy(alpha = .2f), selectedLabelColor = Mint, labelColor = TextMuted))
            listOf("AVAILABLE" to "Available", "OCCUPIED" to "Occupied", "MAINTENANCE" to "Repair").forEach { (key, label) ->
                FilterChip(selected = !includeArchived && filter == key, onClick = { includeArchived = false; filter = key; onRefresh(false) }, label = { Text(label, fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal.copy(alpha = .2f), selectedLabelColor = Mint, labelColor = TextMuted))
            }
            FilterChip(selected = includeArchived, onClick = { includeArchived = !includeArchived; onRefresh(!includeArchived) }, label = { Text("Archived", fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Teal.copy(alpha = .2f), selectedLabelColor = Mint, labelColor = TextMuted))
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) EmptyState(if (includeArchived) "No archived rooms" else "No matching rooms", if (includeArchived) "Rooms you archive will be kept here and can be restored." else "Add a room or change your search/filter.", Icons.Default.Bed)
        else LazyColumn(contentPadding = PaddingValues(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered, key = { it.id }) { room ->
                RoomCard(room, onEdit = { onEdit(room) }, onStatus = { onStatus(room) }, onArchive = { confirmArchive = room }, onRestore = { onRestore(room) })
            }
        }
    }
    confirmArchive?.let { room ->
        AlertDialog(
            onDismissRequest = { confirmArchive = null },
            containerColor = Color(0xFF102A37),
            title = { Text("Archive ${room.name}?", color = TextMain, fontWeight = FontWeight.Bold) },
            text = { Text("This hides the room from active inventory but preserves its previous enquiry history. You can restore it later.", color = TextMuted) },
            confirmButton = { Button(onClick = { onArchive(room); confirmArchive = null }, colors = ButtonDefaults.buttonColors(containerColor = RedSoft)) { Text("Archive room", color = Ink) } },
            dismissButton = { TextButton(onClick = { confirmArchive = null }) { Text("Cancel", color = TextMuted) } }
        )
    }
}

@Composable
private fun RoomCard(room: RoomDto, onEdit: () -> Unit, onStatus: () -> Unit, onArchive: () -> Unit, onRestore: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            if (!room.photoUrl.isNullOrBlank()) {
                AsyncImage(model = room.photoUrl, contentDescription = "${room.name} photo", modifier = Modifier.size(82.dp).clip(RoundedCornerShape(15.dp)))
            } else {
                Box(Modifier.size(82.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFF20515A), Color(0xFF102D3A)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Bed, null, tint = Teal, modifier = Modifier.size(34.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${room.propertyName} · ${room.roomType}", color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(if (room.isActive) room.status else "ARCHIVED")
                    Spacer(Modifier.width(8.dp))
                    Text("${room.capacity} guests", color = TextMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("₹${formatAmount(room.ratePerNight)} / night", color = Mint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        if (room.occupiedGuest.isNotBlank() && room.status == "OCCUPIED" && room.isActive) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = Amber, modifier = Modifier.size(15.dp))
                Text(" ${room.occupiedGuest}", color = TextMuted, fontSize = 11.sp)
                room.expectedCheckout?.let { Text("  ·  Checkout $it", color = TextMuted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (room.isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Teal.copy(alpha = .5f)), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 9.dp)) {
                    Icon(Icons.Default.Edit, null, tint = Teal, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Edit room & rate", color = Teal, fontSize = 11.sp)
                }
                Button(onClick = onStatus, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = CardGlass2), contentPadding = PaddingValues(horizontal = 7.dp, vertical = 9.dp)) {
                    Icon(Icons.Default.SyncAlt, null, tint = Mint, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Availability", color = Mint, fontSize = 11.sp)
                }
                IconButton(onClick = onArchive, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF482F39))) { Icon(Icons.Default.Archive, null, tint = RedSoft, modifier = Modifier.size(17.dp)) }
            }
        } else {
            OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Teal.copy(alpha = .5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)) {
                Icon(Icons.Default.Restore, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("Restore room")
            }
        }
    }
}

@Composable
private fun EnquiriesScreen(enquiries: List<EnquiryDto>, onStatus: (EnquiryDto, String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Guest enquiries", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text("Requests shared by the StayLink operator", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        if (enquiries.isEmpty()) EmptyState("No guest enquiries", "When the operator sends a guest request to your property, it will appear here.", Icons.Default.MailOutline)
        else LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(enquiries, key = { it.id }) { enquiry ->
                GlassCard(Modifier.fillMaxWidth()) {
                    EnquiryCard(enquiry)
                    Spacer(Modifier.height(10.dp))
                    Text("Update request", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EnquiryAction("Seen", "ACKNOWLEDGED", enquiry.status, { onStatus(enquiry, it) }, Modifier.weight(1f))
                        EnquiryAction("Confirm", "CONFIRMED", enquiry.status, { onStatus(enquiry, it) }, Modifier.weight(1f))
                        EnquiryAction("Close", "CLOSED", enquiry.status, { onStatus(enquiry, it) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnquiryCard(enquiry: EnquiryDto, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(enquiry.customerName, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${enquiry.propertyName} · ${enquiry.roomName}", color = TextMuted, fontSize = 11.sp)
            }
            StatusPill(enquiry.status)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconText(Icons.Default.Phone, enquiry.customerPhone)
            IconText(Icons.Default.Groups, "${enquiry.guestCount} guests")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconText(Icons.Default.CalendarMonth, "${enquiry.checkIn} → ${enquiry.checkOut}")
            Text("₹${formatAmount(enquiry.nightlyRate)}/night", color = Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        if (!compact && enquiry.message.isNotBlank()) Text(enquiry.message, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun EnquiryAction(label: String, status: String, current: String, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val selected = current == status
    OutlinedButton(onClick = { onClick(status) }, modifier = modifier.height(40.dp), enabled = !selected, contentPadding = PaddingValues(horizontal = 2.dp), shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, if (selected) Teal else Color(0xFF345460))) {
        Text(label, color = if (selected) Teal else TextMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun NotificationsScreen(notifications: List<NotificationDto>, onRead: (Int) -> Unit, onReadAll: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your updates", color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onReadAll) { Text("Mark all read", color = Teal, fontSize = 12.sp) }
        }
        if (notifications.isEmpty()) EmptyState("All caught up", "New guest enquiries and updates will show up here.", Icons.Default.NotificationsOff)
        else LazyColumn(contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(notifications, key = { it.id }) { item ->
                GlassCard(Modifier.fillMaxWidth().clickable { if (!item.isRead) onRead(item.id) }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(if (item.isRead) CardGlass2 else Teal.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, null, tint = if (item.isRead) TextMuted else Teal) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(item.message, color = TextMuted, fontSize = 12.sp)
                            item.enquiry?.let { Text("${it.customerName} · ${it.roomName}", color = Mint, fontSize = 11.sp) }
                        }
                        if (!item.isRead) Box(Modifier.size(8.dp).clip(CircleShape).background(Teal))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreScreen(
    properties: List<PropertyDto>, onAddProperty: () -> Unit, onEditProperty: (PropertyDto) -> Unit,
    onChangePassword: () -> Unit, onLogout: () -> Unit, baseUrl: String, onServerChange: (String) -> Unit
) {
    var showServerEditor by remember { mutableStateOf(false) }
    var editedServer by remember(baseUrl) { mutableStateOf(baseUrl) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("More", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("Property and account settings", color = TextMuted, fontSize = 12.sp) }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("My properties", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("${properties.size} registered properties", color = TextMuted, fontSize = 12.sp) }
                    Button(onClick = onAddProperty, colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null, tint = Ink); Text(" Add", color = Ink) }
                }
                Spacer(Modifier.height(8.dp))
                if (properties.isEmpty()) Text("No properties have been added to your account yet.", color = TextMuted, fontSize = 12.sp)
                properties.forEach { property ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x331C4B55)).clickable { onEditProperty(property) }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HomeWork, null, tint = Teal)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) { Text(property.name, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text(property.area, color = TextMuted, fontSize = 11.sp) }
                        Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SettingsRow(Icons.Default.Lock, "Change password", "Keep your owner account secure", onChangePassword)
                Divider(color = Color(0xFF31515D), modifier = Modifier.padding(vertical = 8.dp))
                SettingsRow(Icons.Default.Cloud, "API server", "Configure the PythonAnywhere or local server", { showServerEditor = !showServerEditor })
                AnimatedVisibility(showServerEditor) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(editedServer, { editedServer = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Server URL") }, singleLine = true, colors = textFieldColors())
                        Button(onClick = { onServerChange(editedServer); showServerEditor = false }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Save server address", color = Ink) }
                        Text("Use HTTPS for the live server. For local phone testing, use your PC's Wi-Fi IP and run Django on 0.0.0.0:8000.", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, RedSoft.copy(alpha = .5f))) {
                Icon(Icons.Default.Logout, null, tint = RedSoft); Spacer(Modifier.width(8.dp)); Text("Sign out", color = RedSoft, fontWeight = FontWeight.Bold)
            }
        }
        item { Text("Rudra StayLink · Owner app v1.0.3", color = TextMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}

@Composable
private fun PropertyDialog(property: PropertyDto?, onDismiss: () -> Unit, onSave: (PropertyForm) -> Unit) {
    var name by remember { mutableStateOf(property?.name.orEmpty()) }
    var area by remember { mutableStateOf(property?.area ?: "Aare Ware") }
    var address by remember { mutableStateOf(property?.address.orEmpty()) }
    var contact by remember { mutableStateOf(property?.contactPerson.orEmpty()) }
    var phone by remember { mutableStateOf(property?.phone.orEmpty()) }
    var whatsapp by remember { mutableStateOf(property?.whatsappNumber.orEmpty()) }
    var description by remember { mutableStateOf(property?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF102A37), titleContentColor = TextMain, textContentColor = TextMuted,
        title = { Text(if (property == null) "Add property" else "Edit property", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                RudraTextField(name, { name = it }, "Property / lodging name")
                RudraTextField(area, { area = it }, "Area / location")
                RudraTextField(address, { address = it }, "Full address")
                RudraTextField(contact, { contact = it }, "Contact person")
                RudraTextField(phone, { phone = it }, "Phone number", keyboard = KeyboardType.Phone)
                RudraTextField(whatsapp, { whatsapp = it }, "WhatsApp number with country code", keyboard = KeyboardType.Phone)
                RudraTextField(description, { description = it }, "Short description", singleLine = false)
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank() && area.isNotBlank()) onSave(PropertyForm(name.trim(), area.trim(), address, contact, phone, whatsapp, description)) }, enabled = name.isNotBlank() && area.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Save property", color = Ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun RoomDialog(properties: List<PropertyDto>, room: RoomDto?, onDismiss: () -> Unit, onSave: (RoomForm) -> Unit) {
    var selectedProperty by remember { mutableStateOf(room?.property ?: properties.firstOrNull()?.id) }
    var propertyMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(room?.name.orEmpty()) }
    var roomType by remember { mutableStateOf(room?.roomType ?: "Deluxe") }
    var capacity by remember { mutableStateOf(room?.capacity?.toString() ?: "2") }
    var rate by remember { mutableStateOf(room?.ratePerNight ?: "") }
    var amenities by remember { mutableStateOf(room?.amenities.orEmpty()) }
    var notes by remember { mutableStateOf(room?.notes.orEmpty()) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photoUri = it }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF102A37), titleContentColor = TextMain, textContentColor = TextMuted,
        title = { Text(if (room == null) "Add a room" else "Edit room details", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box {
                    OutlinedButton(onClick = { propertyMenu = true }, modifier = Modifier.fillMaxWidth(), enabled = properties.isNotEmpty(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal), border = BorderStroke(1.dp, Color(0xFF3E6975))) {
                        Text(properties.firstOrNull { it.id == selectedProperty }?.name ?: "Select property", modifier = Modifier.weight(1f), maxLines = 1); Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(expanded = propertyMenu, onDismissRequest = { propertyMenu = false }) {
                        properties.forEach { prop -> DropdownMenuItem(text = { Text(prop.name) }, onClick = { selectedProperty = prop.id; propertyMenu = false }) }
                    }
                }
                RudraTextField(name, { name = it }, "Room name or number")
                RudraTextField(roomType, { roomType = it }, "Room type (Standard / Deluxe / Sea View)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RudraTextField(capacity, { capacity = it.filter(Char::isDigit) }, "Max guests", modifier = Modifier.weight(1f), keyboard = KeyboardType.Number)
                    RudraTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, "Nightly price (₹)", modifier = Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                }
                RudraTextField(amenities, { amenities = it }, "Facilities (AC, Wi-Fi, sea view)")
                RudraTextField(notes, { notes = it }, "Notes for your team", singleLine = false)
                OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Teal.copy(alpha = .5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)) {
                    Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(7.dp)); Text(if (photoUri != null) "Photo selected" else "Choose room photograph")
                }
                if (room != null) Text("To change availability or guest checkout, use the Availability button on the room card.", color = TextMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(RoomForm(selectedProperty, name.trim(), roomType.trim(), capacity.toIntOrNull() ?: 2, rate, amenities, notes, room?.status ?: "AVAILABLE", photoUri)) }, enabled = selectedProperty != null && name.isNotBlank() && rate.toBigDecimalOrNull() != null, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Save room", color = Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun AvailabilityDialog(room: RoomDto, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var status by remember { mutableStateOf(room.status) }
    var guest by remember { mutableStateOf(room.occupiedGuest) }
    var checkout by remember { mutableStateOf(room.expectedCheckout ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF102A37), titleContentColor = TextMain, textContentColor = TextMuted,
        title = { Text("Update ${room.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose the room's current status.", color = TextMuted, fontSize = 12.sp)
                listOf("AVAILABLE" to "Available", "OCCUPIED" to "Occupied", "MAINTENANCE" to "Maintenance").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (status == value) Teal.copy(alpha = .12f) else CardGlass).clickable { status = value }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = status == value, onClick = { status = value }, colors = RadioButtonDefaults.colors(selectedColor = Teal, unselectedColor = TextMuted))
                        Text(label, color = TextMain, fontWeight = FontWeight.Medium)
                    }
                }
                if (status == "OCCUPIED") {
                    RudraTextField(guest, { guest = it }, "Current guest name")
                    RudraTextField(checkout, { checkout = it }, "Expected checkout (YYYY-MM-DD)")
                    Text("Use a date such as 2026-11-10.", color = TextMuted, fontSize = 10.sp)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(status, guest, checkout) }, enabled = status != "OCCUPIED" || (guest.isNotBlank() && checkout.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))), colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Update status", color = Ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF102A37), titleContentColor = TextMain,
        title = { Text("Change password", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                PasswordField(current, { current = it }, "Current password")
                PasswordField(newPassword, { newPassword = it }, "New password")
                PasswordField(confirm, { confirm = it }, "Confirm new password")
                Text("Use at least 10 characters and choose a password you do not use elsewhere.", color = TextMuted, fontSize = 11.sp)
                if (confirm.isNotEmpty() && newPassword != confirm) Text("Passwords do not match.", color = RedSoft, fontSize = 11.sp)
            }
        },
        confirmButton = { Button(onClick = { onSave(current, newPassword) }, enabled = current.isNotBlank() && newPassword.length >= 10 && newPassword == confirm, colors = ButtonDefaults.buttonColors(containerColor = Teal)) { Text("Update password", color = Ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = textFieldColors())
}

@Composable
private fun MiniMetric(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(Color(0x33213D49)).padding(10.dp)) {
        Text(value.toString(), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ActionTile(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(CardGlass).borderGlow(Color(0xFF31515D)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Teal, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(6.dp))
        Text(title, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextMuted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(trailing, color = Teal, fontSize = 11.sp)
        Icon(Icons.Default.ChevronRight, null, tint = Teal, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "AVAILABLE" -> Teal
        "OCCUPIED", "NEW" -> Amber
        "MAINTENANCE", "CLOSED", "ARCHIVED" -> RedSoft
        "CONFIRMED" -> Mint
        else -> TextMuted
    }
    val label = status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = .13f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IconText(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Teal, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A4650)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text(title, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, color = TextMuted, fontSize = 10.sp) }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
    }
}

@Composable
private fun EmptyState(title: String, detail: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 35.dp, horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(65.dp).clip(CircleShape).background(Color(0xFF173945)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(5.dp))
        Text(detail, color = TextMuted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, gradient: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(21.dp))
            .clip(RoundedCornerShape(21.dp))
            .background(if (gradient) Brush.linearGradient(listOf(Color(0xFF1A4B55), Color(0xFF102B38), Color(0xCC153440))) else Brush.linearGradient(listOf(CardGlass, Color(0xCC0E2632))))
            .borderGlow(Color(0xFF2A4B58))
            .padding(15.dp),
        content = content
    )
}

@Composable
private fun RudraTextField(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) }, singleLine = singleLine, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        leadingIcon = if (icon != null) ({ Icon(icon, null, tint = Teal) }) else null,
        colors = textFieldColors()
    )
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Teal,
    unfocusedBorderColor = Color(0xFF3B5D68),
    focusedLabelColor = Teal,
    unfocusedLabelColor = TextMuted,
    cursorColor = Teal,
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    focusedContainerColor = Color(0x221B4653),
    unfocusedContainerColor = Color(0x221B4653)
)

private data class PropertyForm(val name: String, val area: String, val address: String, val contactPerson: String, val phone: String, val whatsappNumber: String, val description: String)
private data class RoomForm(val propertyId: Int?, val name: String, val type: String, val capacity: Int, val rate: String, val amenities: String, val notes: String, val status: String, val photoUri: Uri?)
private data class RoomParts(val name: okhttp3.RequestBody, val type: okhttp3.RequestBody, val capacity: okhttp3.RequestBody, val rate: okhttp3.RequestBody, val amenities: okhttp3.RequestBody, val notes: okhttp3.RequestBody, val status: okhttp3.RequestBody)

private fun RoomForm.toMultipartParts(): RoomParts {
    fun part(value: String) = value.toRequestBody("text/plain".toMediaTypeOrNull())
    return RoomParts(part(name), part(type), part(capacity.toString()), part(rate), part(amenities), part(notes), part(status))
}

private fun Int.toPart() = toString().toRequestBody("text/plain".toMediaTypeOrNull())

private fun makePhotoPart(context: Context, uri: Uri): MultipartBody.Part? {
    // Resize/compress photos before upload so ordinary phone camera images fit the backend's upload limit.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maxDimension / sample > 1600) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val output = java.io.ByteArrayOutputStream()
    var quality = 86
    do {
        output.reset()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        quality -= 8
    } while (output.size() > 4_500_000 && quality >= 54)
    bitmap.recycle()
    if (output.size() > 4_900_000) throw IllegalArgumentException("This photograph is too large after compression. Choose a smaller image.")
    val body = output.toByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull())
    return MultipartBody.Part.createFormData("photo", "rudra_room_${System.currentTimeMillis()}.jpg", body)
}

private fun formatAmount(raw: String): String = try {
    NumberFormat.getNumberInstance(Locale("en", "IN")).format(raw.toDouble())
} catch (_: Exception) { raw }

private fun friendlyError(error: Exception): String {
    if (error is HttpException) {
        val detail = try { error.response()?.errorBody()?.string()?.take(700) } catch (_: Exception) { null }
        return when (error.code()) {
            400 -> "Please check the details. ${detail.orEmpty()}"
            401 -> "Your session has expired. Please sign in again."
            403 -> "This action is not permitted for your owner account."
            404 -> "The requested record was not found. Refresh and try again."
            413 -> "The selected photograph is too large. Choose a smaller image."
            429 -> "Too many requests. Please wait a moment and try again."
            in 500..599 -> "The StayLink server had a problem. Please try again shortly."
            else -> "Server response ${error.code()}. ${detail.orEmpty()}"
        }
    }
    val raw = error.message.orEmpty()
    return when {
        raw.contains("Unable to resolve host", true) || raw.contains("Failed to connect", true) -> "Cannot reach the StayLink server. Check your internet connection and API server address."
        raw.contains("CLEARTEXT", true) -> "This local server uses HTTP. Use the correct local address and confirm the Django server is running."
        else -> raw.ifBlank { "Something went wrong. Please try again." }
    }
}

private fun Modifier.borderGlow(color: Color): Modifier = this.border(1.dp, color.copy(alpha = .55f), RoundedCornerShape(21.dp))

