package com.aayaan.agclient.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.modules.ModuleCategory
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.ui.components.ModuleCard
import com.aayaan.agclient.ui.theme.*

@Composable
fun ModulesScreen(
    moduleManager: ModuleManager,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ModuleCategory?>(null) }

    val allModules = moduleManager.getAllModules()
    val filteredModules = allModules.filter { module ->
        (selectedCategory == null || module.category == selectedCategory) &&
                (searchQuery.isEmpty() ||
                        module.name.contains(searchQuery, ignoreCase = true) ||
                        module.commandName.contains(searchQuery, ignoreCase = true) ||
                        module.description.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Modules or Commands...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All (${allModules.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonCyan,
                    selectedLabelColor = DarkBackground
                )
            )

            for (category in ModuleCategory.values()) {
                val count = moduleManager.getModulesByCategory(category).size
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text("${category.displayName} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricPurple,
                        selectedLabelColor = TextPrimary
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredModules) { module ->
                ModuleCard(
                    module = module,
                    onToggle = { isEnabled ->
                        if (isEnabled) module.enable() else module.disable()
                        moduleManager.saveToConfig()
                    }
                )
            }
        }
    }
}
