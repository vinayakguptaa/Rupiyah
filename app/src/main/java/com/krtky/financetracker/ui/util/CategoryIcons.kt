package com.krtky.financetracker.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIcons {
    data class Entry(val id: String, val label: String, val icon: ImageVector)

    val all: List<Entry> = listOf(
        Entry("category", "General", Icons.Default.Category),
        Entry("restaurant", "Food", Icons.Default.Restaurant),
        Entry("coffee", "Cafe", Icons.Default.Coffee),
        Entry("shopping_bag", "Shopping", Icons.Default.ShoppingBag),
        Entry("grocery", "Grocery", Icons.Default.LocalGroceryStore),
        Entry("directions_bus", "Transit", Icons.Default.DirectionsBus),
        Entry("directions_car", "Car", Icons.Default.DirectionsCar),
        Entry("flight", "Flight", Icons.Default.Flight),
        Entry("local_gas_station", "Fuel", Icons.Default.LocalGasStation),
        Entry("home", "Home", Icons.Default.Home),
        Entry("school", "Education", Icons.Default.School),
        Entry("movie", "Movies", Icons.Default.Movie),
        Entry("sports_esports", "Games", Icons.Default.SportsEsports),
        Entry("fitness_center", "Fitness", Icons.Default.FitnessCenter),
        Entry("local_hospital", "Health", Icons.Default.LocalHospital),
        Entry("local_pharmacy", "Pharmacy", Icons.Default.LocalPharmacy),
        Entry("phone_android", "Mobile", Icons.Default.PhoneAndroid),
        Entry("subscriptions", "Subs", Icons.Default.Subscriptions),
        Entry("payments", "Income", Icons.Default.Payments),
        Entry("salary", "Salary", Icons.Default.Payments),
        Entry("income", "Income", Icons.Default.ArrowDownward),
        Entry("swap_horiz", "Transfer", Icons.Default.SwapHoriz),
        Entry("account_balance", "Bank", Icons.Default.AccountBalance),
        Entry("account_balance_wallet", "Wallet", Icons.Default.AccountBalanceWallet),
        Entry("work", "Work", Icons.Default.Work),
        Entry("trending_up", "Invest", Icons.AutoMirrored.Filled.TrendingUp),
        Entry("checkroom", "Clothes", Icons.Default.Checkroom),
        Entry("pets", "Pets", Icons.Default.Pets),
        Entry("build", "Repair", Icons.Default.Build),
        Entry("more_horiz", "Other", Icons.Default.MoreHoriz),
        Entry("help_outline", "Misc", Icons.AutoMirrored.Filled.HelpOutline),
    )

    fun iconFor(id: String?, name: String? = null): ImageVector {
        val key = id?.trim()?.lowercase()?.replace(' ', '_')?.replace('/', '_').orEmpty()
        val label = name?.trim()?.lowercase().orEmpty()
        val blob = "$key $label"
        // A generic stored icon should not hide a more specific category name.
        if (key != "category") {
            all.firstOrNull { it.id.equals(key, true) }?.icon?.let { return it }
        }
        return when {
            "salary" in blob || "income" in blob || "payroll" in blob || "credit salary" in blob -> Icons.Default.Payments
            "pay" in key || key == "payments" -> Icons.Default.Payments
            "food" in blob || "restaurant" in blob || "cafe" in blob -> Icons.Default.Restaurant
            "shop" in blob || "bag" in blob -> Icons.Default.ShoppingBag
            "travel" in blob || "bus" in blob || "transit" in blob -> Icons.Default.DirectionsBus
            "school" in blob || "educat" in blob || "coach" in blob -> Icons.Default.School
            "movie" in blob || "entertain" in blob -> Icons.Default.Movie
            "transfer" in blob || "swap" in blob -> Icons.Default.SwapHoriz
            "wallet" in blob -> Icons.Default.AccountBalanceWallet
            "bank" in blob -> Icons.Default.AccountBalance
            "work" in blob || "job" in blob -> Icons.Default.Work
            "other" in blob || "more" in blob -> Icons.Default.MoreHoriz
            else -> Icons.Default.Category
        }
    }
}
