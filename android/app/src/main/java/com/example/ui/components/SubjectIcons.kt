package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryBuilder
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SolarPower
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Subject icon catalogue.
 *
 * Admins pick an icon in the portal by [id] (the same string stored in
 * `public.subjects.icon_name`). Anything the catalogue does not know about
 * falls back to [autoFor] and finally to [DEFAULT], so a stale or hand-edited
 * row can never crash or blank the UI.
 */
object SubjectIcons {

    val DEFAULT: ImageVector = Icons.AutoMirrored.Filled.MenuBook

    /** id -> icon. Ids match the snake_case names offered by the admin portal. */
    private val catalogue: Map<String, ImageVector> = mapOf(
        // Maths & data
        "calculate" to Icons.Filled.Calculate,
        "functions" to Icons.Filled.Functions,
        "timeline" to Icons.Filled.Timeline,
        "bar_chart" to Icons.Filled.BarChart,
        "percent" to Icons.Filled.Percent,
        "insights" to Icons.Filled.Insights,
        "trending_up" to Icons.AutoMirrored.Filled.TrendingUp,
        "query_builder" to Icons.Filled.QueryBuilder,
        "schema" to Icons.Filled.Schema,
        "table_chart" to Icons.Filled.TableChart,
        "data_object" to Icons.Filled.DataObject,

        // Physics & electrical
        "science" to Icons.Filled.Science,
        "bolt" to Icons.Filled.Bolt,
        "electric_bolt" to Icons.Filled.ElectricBolt,
        "energy_savings_leaf" to Icons.Filled.EnergySavingsLeaf,
        "speed" to Icons.Filled.Speed,
        "waves" to Icons.Filled.Waves,
        "thermostat" to Icons.Filled.Thermostat,

        // Chemistry & biology
        "biotech" to Icons.Filled.Biotech,
        "water_drop" to Icons.Filled.WaterDrop,
        "local_fire_department" to Icons.Filled.LocalFireDepartment,
        "color_lens" to Icons.Filled.ColorLens,

        // Electronics & communication
        "memory" to Icons.Filled.Memory,
        "cell_tower" to Icons.Filled.CellTower,
        "router" to Icons.Filled.Router,
        "wifi" to Icons.Filled.Wifi,
        "battery_charging_full" to Icons.Filled.BatteryChargingFull,
        "electric_meter" to Icons.Filled.ElectricMeter,
        "power" to Icons.Filled.Power,
        "lightbulb" to Icons.Filled.Lightbulb,
        "solar_power" to Icons.Filled.SolarPower,
        "wind_power" to Icons.Filled.WindPower,
        "tv" to Icons.Filled.Tv,
        "electrical_services" to Icons.Filled.ElectricalServices,
        "device_hub" to Icons.Filled.DeviceHub,
        "hub" to Icons.Filled.Hub,
        "lan" to Icons.Filled.Lan,
        "sensors" to Icons.Filled.Sensors,

        // Civil & mechanical
        "construction" to Icons.Filled.Construction,
        "engineering" to Icons.Filled.Engineering,
        "architecture" to Icons.Filled.Architecture,
        "straighten" to Icons.Filled.Straighten,
        "landscape" to Icons.Filled.Landscape,
        "factory" to Icons.Filled.Factory,
        "warehouse" to Icons.Filled.Warehouse,
        "handyman" to Icons.Filled.Handyman,
        "build" to Icons.Filled.Build,
        "scale" to Icons.Filled.Scale,

        // Computing
        "computer" to Icons.Filled.Computer,
        "developer_mode" to Icons.Filled.DeveloperMode,
        "code" to Icons.Filled.Code,
        "storage" to Icons.Filled.Storage,
        "security" to Icons.Filled.Security,
        "cloud" to Icons.Filled.Cloud,
        "dns" to Icons.Filled.Dns,
        "phone_android" to Icons.Filled.PhoneAndroid,
        "bug_report" to Icons.Filled.BugReport,
        "terminal" to Icons.Filled.Terminal,

        // Business & commerce
        "account_balance" to Icons.Filled.AccountBalance,
        "wallet" to Icons.Filled.Wallet,
        "business" to Icons.Filled.Business,
        "store" to Icons.Filled.Store,
        "receipt" to Icons.Filled.Receipt,
        "monetization_on" to Icons.Filled.MonetizationOn,
        "gavel" to Icons.Filled.Gavel,
        "balance" to Icons.Filled.Balance,
        "paid" to Icons.Filled.Paid,

        // Humanities & skills
        "language" to Icons.Filled.Language,
        "translate" to Icons.Filled.Translate,
        "record_voice_over" to Icons.Filled.RecordVoiceOver,
        "menu_book" to Icons.AutoMirrored.Filled.MenuBook,
        "history" to Icons.Filled.History,
        "psychology" to Icons.Filled.Psychology,
        "groups" to Icons.Filled.Groups,
        "forum" to Icons.Filled.Forum,
        "auto_stories" to Icons.Filled.AutoStories,
        "library_books" to Icons.AutoMirrored.Filled.LibraryBooks,

        // Design & media
        "design_services" to Icons.Filled.DesignServices,
        "brush" to Icons.Filled.Brush,
        "palette" to Icons.Filled.Palette,
        "videocam" to Icons.Filled.Videocam,
        "camera" to Icons.Filled.Camera,

        // Health, safety & sports
        "health_and_safety" to Icons.Filled.HealthAndSafety,
        "local_hospital" to Icons.Filled.LocalHospital,
        "fitness_center" to Icons.Filled.FitnessCenter,
        "sports_score" to Icons.Filled.SportsScore,

        // General
        "school" to Icons.Filled.School,
        "home_work" to Icons.Filled.HomeWork,
        "star" to Icons.Filled.Star,
        "verified" to Icons.Filled.Verified,
        "verified_user" to Icons.Filled.VerifiedUser,
        "auto_awesome" to Icons.Filled.AutoAwesome,
        "emoji_events" to Icons.Filled.EmojiEvents,
        "rocket" to Icons.Filled.Rocket,
        "rocket_launch" to Icons.Filled.RocketLaunch,
        "travel_explore" to Icons.Filled.TravelExplore,
        "key" to Icons.Filled.Key,
        "volunteer_activism" to Icons.Filled.VolunteerActivism,
        "self_improvement" to Icons.Filled.SelfImprovement,
        "restaurant" to Icons.Filled.Restaurant,
        "agriculture" to Icons.Filled.Agriculture,
        "forest" to Icons.Filled.Forest,
        "sailing" to Icons.Filled.Sailing,
        "satellite_alt" to Icons.Filled.SatelliteAlt,
        "flight" to Icons.Filled.Flight,
        "military_tech" to Icons.Filled.MilitaryTech,

        // Legacy ids still present in the database
        "graphic_eq" to Icons.Filled.GraphicEq,
        "smart_toy" to Icons.Filled.SmartToy,

        // Shared UI ids
        "person" to Icons.Filled.Person,
        "account_circle" to Icons.Filled.AccountCircle,
        "account_box" to Icons.Filled.AccountBox,
        "lock" to Icons.Filled.Lock,
        "settings" to Icons.Filled.Settings,
        "search" to Icons.Filled.Search,
        "home" to Icons.Filled.Home,
        "menu" to Icons.Filled.Menu,
        "refresh" to Icons.Filled.Refresh,
        "send" to Icons.AutoMirrored.Filled.Send,
        "check" to Icons.Filled.Check,
        "check_circle" to Icons.Filled.CheckCircle,
        "add_circle" to Icons.Filled.AddCircle,
        "date_range" to Icons.Filled.DateRange,
        "list" to Icons.AutoMirrored.Filled.List,
        "place" to Icons.Filled.Place,
        "edit" to Icons.Filled.Edit,
        "delete" to Icons.Filled.Delete,
        "more_vert" to Icons.Filled.MoreVert,
        "notifications" to Icons.Filled.Notifications,
        "info" to Icons.Filled.Info,
        "favorite" to Icons.Filled.Favorite,
        "thumb_up" to Icons.Filled.ThumbUp,
        "face" to Icons.Filled.Face,
        "play_arrow" to Icons.Filled.PlayArrow,
        "forward_to_inbox" to Icons.AutoMirrored.Filled.ForwardToInbox
    )

    /**
     * Keyword -> icon id, checked in order against a lower-cased subject name.
     * First match wins, so more specific patterns come first.
     */
    private val rules: List<Pair<List<String>, String>> = listOf(
        listOf("data structure", "data struct") to "data_object",
        listOf("operating system", "os ") to "computer",
        listOf("computer network", "networking") to "router",
        listOf("electrical", "electric") to "electrical_services",
        listOf(
            "microprocessor", "microcontroller", "embedded", "vlsi", "cmos",
            "semiconductor", "microelectronic", "analog circuit",
            "digital circuit", "electronic"
        ) to "memory",
        listOf("machine learning", "artificial intelligence", "ai ") to "smart_toy",
        listOf(
            "signal processing", "digital signal", "signal & system",
            "signals and system"
        ) to "cell_tower",
        listOf(
            "communication skill", "soft skill", "business communication",
            "professional communication", "technical communication"
        ) to "record_voice_over",
        listOf("civil", "building construction", "structural engineering", "structural design", "construction") to "construction",
        listOf("architect", "town planning") to "architecture",
        listOf("mathemat", "math ", "calculus", "algebra", "geometry", "trigonometr") to "calculate",
        listOf("control system", "control engineer") to "settings",
        listOf("mechanic", "machine") to "settings",
        listOf("statistics", "probability", "statistic") to "bar_chart",
        listOf("physics") to "science",
        listOf("chemistr", "organic", "polymer") to "biotech",
        listOf("biology", "botany", "zoology") to "forest",
        listOf("geology", "survey", "geo") to "landscape",
        listOf("thermal", "heat", "thermodynam") to "thermostat",
        listOf("wave", "optics", "sound") to "waves",
        listOf("kinematic", "motion") to "speed",
        listOf("power", "energy") to "electric_bolt",
        listOf("program", "coding", "software", "algorithm", "dsa") to "developer_mode",
        listOf("web", "html", "javascript") to "code",
        listOf("database", "dbms", "sql") to "storage",
        listOf("network") to "lan",
        listOf("cyber", "security", "crypt") to "security",
        listOf("cloud", "devops") to "cloud",
        listOf("operating") to "dns",
        listOf("linux", "shell", "command") to "terminal",
        listOf("economi", "finance", "accounting") to "account_balance",
        listOf("manage", "business", "entrepreneur") to "business",
        listOf("market", "commerce", "sales") to "store",
        listOf("law", "legal", "constitution") to "gavel",
        listOf("english", "language") to "language",
        listOf("humanities", "history") to "history",
        listOf("psycholog", "behaviour", "behavior") to "psychology",
        listOf("graphic", "drawing", "drafting") to "straighten",
        listOf(
            "ui/ux", "ui design", "product design", "design engineering",
            "graphic design"
        ) to "design_services",
        listOf("mobile", "android", "app development") to "phone_android",
        listOf("environment", "ecology") to "forest",
        listOf("safety") to "health_and_safety",
        listOf("physics lab") to "science",
        listOf("workshop", "practical") to "handyman",
        listOf("project", "seminar") to "rocket_launch"
    )

    /** Every selectable id, sorted, for the admin portal's picker. */
    val ids: List<String> = catalogue.keys.sorted()

    fun isKnown(id: String?): Boolean = id != null && catalogue.containsKey(id)

    /** The stored icon if it is recognised, otherwise a guess from the name. */
    fun resolve(id: String?, subjectName: String): ImageVector =
        catalogue[id] ?: catalogue[autoFor(subjectName)] ?: DEFAULT

    /**
     * Best-guess icon id for a subject name. Returns "menu_book" when nothing
     * matches so callers always get a usable id.
     */
    fun autoFor(subjectName: String): String {
        val name = " ${subjectName.lowercase()} "
        for ((keywords, id) in rules) {
            if (keywords.any { name.contains(it) }) return id
        }
        return "menu_book"
    }
}
