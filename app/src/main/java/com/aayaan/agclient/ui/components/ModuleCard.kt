package com.aayaan.agclient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.modules.Module
import com.aayaan.agclient.modules.ModuleProperty
import com.aayaan.agclient.ui.theme.*

@Composable
fun ModuleCard(
    module: Module,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(module.isEnabled) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (enabled) NeonCyan.copy(alpha = 0.5f) else DarkCardBorder,
                RoundedCornerShape(10.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) DarkSurface else DarkBackground
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) NeonCyan else TextPrimary
                        )
                        Text(
                            text = ".${module.commandName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricPurple
                        )
                    }
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            onToggle(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = DarkCard
                        )
                    )
                    if (module.properties.isNotEmpty()) {
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand properties",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded && module.properties.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Divider(color = DarkCardBorder)
                    for (prop in module.properties) {
                        PropertyRow(prop)
                    }
                }
            }
        }
    }
}

@Composable
fun PropertyRow(prop: ModuleProperty<*>) {
    when (prop) {
        is ModuleProperty.BooleanProperty -> {
            var checked by remember { mutableStateOf(prop.value) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = prop.name, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        prop.value = it
                    }
                )
            }
        }
        is ModuleProperty.FloatProperty -> {
            var sliderVal by remember { mutableStateOf(prop.value) }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = prop.name, style = MaterialTheme.typography.bodyMedium)
                    Text(text = "%.1f".format(sliderVal), style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                }
                Slider(
                    value = sliderVal,
                    onValueChange = {
                        sliderVal = it
                        prop.value = it
                    },
                    valueRange = prop.min..prop.max,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )
            }
        }
        is ModuleProperty.IntProperty -> {
            var sliderVal by remember { mutableStateOf(prop.value.toFloat()) }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = prop.name, style = MaterialTheme.typography.bodyMedium)
                    Text(text = sliderVal.toInt().toString(), style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                }
                Slider(
                    value = sliderVal,
                    onValueChange = {
                        sliderVal = it
                        prop.value = it.toInt()
                    },
                    valueRange = prop.min.toFloat()..prop.max.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )
            }
        }
        is ModuleProperty.ModeProperty -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = prop.name, style = MaterialTheme.typography.bodyMedium)
                Text(text = prop.value, style = MaterialTheme.typography.titleMedium, color = ElectricPurple)
            }
        }
        is ModuleProperty.StringProperty -> {
            var text by remember { mutableStateOf(prop.value) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    prop.value = it
                },
                label = { Text(prop.name) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
