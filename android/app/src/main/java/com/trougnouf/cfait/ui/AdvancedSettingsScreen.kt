// SPDX-License-Identifier: GPL-3.0-or-later
// File: ./android/app/src/main/java/com/trougnouf/cfait/ui/AdvancedSettingsScreen.kt
package com.trougnouf.cfait.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.trougnouf.cfait.core.CfaitMobile
import com.trougnouf.cfait.core.MobileGoalType
import com.trougnouf.cfait.core.MobileIntervalUnit
import com.trougnouf.cfait.core.MobileGoal
import com.trougnouf.cfait.core.MobileInterval
import com.trougnouf.cfait.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    api: CfaitMobile,
    tabPosition: String,
    tabAutoHide: Boolean,
    onTabPositionChange: (String) -> Unit,
    onTabAutoHideChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugStatus by remember { mutableStateOf("") }
    var debugIsError by remember { mutableStateOf(false) }

    var maxDoneRoots by remember { mutableStateOf("20") }
    var maxDoneSubtasks by remember { mutableStateOf("5") }
    var trashRetention by remember { mutableStateOf("14") }
    var deleteEventsOnCompletion by remember { mutableStateOf(false) }
    var showOngoingNotifications by remember { mutableStateOf(true) }
    var showQuickFilter by remember { mutableStateOf(true) }
    var quickFilterTerm by remember { mutableStateOf("is:ready") }
    var quickFilterIcon by remember { mutableStateOf("f0fa9") }
    var defaultDurationGoalMins by remember { mutableStateOf("60") }
    var sessionsCountAsCompletions by remember { mutableStateOf(false) }
    var showGoalsTab by remember { mutableStateOf(true) }
    var showTaskGoalsInSidebar by remember { mutableStateOf(true) }

    var sortStandardByPriority by remember { mutableStateOf(false) }
    var sortMonths by remember { mutableStateOf("2") }
    var urgentDays by remember { mutableStateOf("1") }
    var urgentPrio by remember { mutableStateOf("1") }
    var defaultPriority by remember { mutableStateOf("5") }
    var startGracePeriodDays by remember { mutableStateOf("1") }
    var aliases by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var newAliasKey by remember { mutableStateOf("") }
    var newAliasTags by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    var goals by remember { mutableStateOf<Map<String, MobileGoal>>(emptyMap()) }
    var goalInputKey by remember { mutableStateOf("") }
    var goalInputTarget by remember { mutableStateOf("") }
    var goalInputType by remember { mutableStateOf(MobileGoalType.COUNT) }
    var goalInputAmount by remember { mutableStateOf("1") }
    var goalInputUnit by remember { mutableStateOf(MobileIntervalUnit.WEEKS) }
    var editingGoalKey by remember { mutableStateOf<String?>(null) }

    fun reload() {
        try {
            val cfg = api.getConfig()
            maxDoneRoots = cfg.maxDoneRoots.toString()
            maxDoneSubtasks = cfg.maxDoneSubtasks.toString()
            trashRetention = cfg.trashRetention.toString()
            deleteEventsOnCompletion = cfg.deleteEventsOnCompletion
            showOngoingNotifications = cfg.showOngoingNotifications
            showQuickFilter = cfg.showQuickFilter
            quickFilterTerm = cfg.quickFilterTerm
            quickFilterIcon = cfg.quickFilterIcon
            defaultDurationGoalMins = cfg.defaultDurationGoalMins.toString()
            sessionsCountAsCompletions = cfg.sessionsCountAsCompletions
            showGoalsTab = cfg.showGoalsTab
            showTaskGoalsInSidebar = cfg.showTaskGoalsInSidebar

            sortStandardByPriority = cfg.sortStandardByPriority
            sortMonths = cfg.sortCutoffMonths?.toString() ?: ""
            urgentDays = cfg.urgentDays.toString()
            urgentPrio = cfg.urgentPrio.toString()
            defaultPriority = cfg.defaultPriority.toString()
            startGracePeriodDays = cfg.startGracePeriodDays.toString()
            aliases = cfg.tagAliases
            goals = cfg.goals
        } catch (e: Exception) {
            // Ignore on load
        }
    }

    LaunchedEffect(Unit) { reload() }

    fun saveToDisk() {
        try {
            val cfg = api.getConfig()
            val newCfg = cfg.copy(
                maxDoneRoots = maxDoneRoots.toUIntOrNull() ?: 20u,
                maxDoneSubtasks = maxDoneSubtasks.toUIntOrNull() ?: 5u,
                trashRetention = trashRetention.toUIntOrNull() ?: 14u,
                deleteEventsOnCompletion = deleteEventsOnCompletion,
                showOngoingNotifications = showOngoingNotifications,
                showQuickFilter = showQuickFilter,
                quickFilterTerm = quickFilterTerm,
                quickFilterIcon = quickFilterIcon,
                defaultDurationGoalMins = defaultDurationGoalMins.toUIntOrNull() ?: 60u,
                sessionsCountAsCompletions = sessionsCountAsCompletions,
                showGoalsTab = showGoalsTab,
                showTaskGoalsInSidebar = showTaskGoalsInSidebar,

                sortStandardByPriority = sortStandardByPriority,
                sortCutoffMonths = sortMonths.toUIntOrNull(),
                urgentDays = urgentDays.toUIntOrNull() ?: 1u,
                urgentPrio = urgentPrio.toUByteOrNull() ?: 1u,
                defaultPriority = defaultPriority.toUByteOrNull() ?: 5u,
                startGracePeriodDays = startGracePeriodDays.toUIntOrNull() ?: 1u,
                tagAliases = aliases,
                goals = goals
            )
            api.saveConfig(newCfg)
        } catch (e: Exception) {
            // Ignore save errors
        }
    }

    val handleBack = {
        saveToDisk()
        onBack()
    }

    BackHandler { handleBack() }

    // Pre-resolve strings that will be referenced from non-composable contexts (eg. inside coroutine)
    val exportExporting = stringResource(R.string.export_debug_status_exporting)
    val exportReady = stringResource(R.string.export_debug_status_ready)
    val exportFailedTemplate = stringResource(R.string.export_debug_status_failed)
    val exportShareTitle = stringResource(R.string.export_debug_share_title)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_settings_button)) },
                navigationIcon = {
                    IconButton(onClick = handleBack) { NfIcon(NfIcons.BACK, 20.sp) }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Collections Tab Section
            Text(
                stringResource(R.string.tab_position),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = tabPosition == "top",
                    onClick = { onTabPositionChange("top") },
                    label = { Text(stringResource(R.string.tab_pos_top)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = tabPosition == "bottom",
                    onClick = { onTabPositionChange("bottom") },
                    label = { Text(stringResource(R.string.tab_pos_bottom)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            ) {
                Switch(checked = tabAutoHide, onCheckedChange = onTabAutoHideChange)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tab_auto_hide), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Sorting Rules
            Text(
                stringResource(R.string.sorting_timeframes),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = sortStandardByPriority, onCheckedChange = { sortStandardByPriority = it })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sort_standard_by_priority_label))
            }
            Text(
                stringResource(R.string.priority_rules),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.due_within_days), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = urgentDays,
                    onValueChange = { urgentDays = it },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.priority_le), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = urgentPrio,
                    onValueChange = { urgentPrio = it },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.default_priority_label), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = defaultPriority,
                    onValueChange = { defaultPriority = it },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Text(
                stringResource(R.string.sorting_timeframes),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.start_grace_days), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = startGracePeriodDays,
                    onValueChange = { startGracePeriodDays = it },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.priority_cutoff_months), modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = sortMonths,
                    onValueChange = { sortMonths = it },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Tag Aliases
            Text(
                stringResource(R.string.tag_aliases),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            aliases.keys.toList().sorted().forEach { key ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        if (key.startsWith("@@")) key else "#$key",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(80.dp)
                    )
                    Text("→", modifier = Modifier.padding(horizontal = 8.dp))
                    Text(aliases[key]?.joinToString(", ") ?: "", modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        scope.launch {
                            api.removeAlias(key)
                            reload()
                            com.trougnouf.cfait.ui.triggerBackgroundSync(context, api)
                        }
                    }) { NfIcon(NfIcons.CROSS, 16.sp, MaterialTheme.colorScheme.error) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = newAliasKey,
                    onValueChange = { newAliasKey = it },
                    label = { Text(stringResource(R.string.alias_key_label)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.placeholder_key_tag)) },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = newAliasTags,
                    onValueChange = { newAliasTags = it },
                    label = { Text(stringResource(R.string.alias_value_label)) },
                    placeholder = { Text(stringResource(R.string.placeholder_values)) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (newAliasKey.isNotBlank() && newAliasTags.isNotBlank()) {
                        val tags = newAliasTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        scope.launch {
                            try {
                                api.addAlias(newAliasKey.trimStart('#'), tags)
                                newAliasKey = ""
                                newAliasTags = ""
                                reload()
                                if (status.startsWith("Error")) status = ""
                                com.trougnouf.cfait.ui.triggerBackgroundSync(context, api)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                status = context.getString(R.string.error_adding_alias, e.message ?: "")
                            }
                        }
                    }
                }) { NfIcon(NfIcons.ADD) }
            }
            if (status.isNotEmpty()) {
                Text(status, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Display Limits Section
            Text(
                text = stringResource(R.string.display_limits),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = maxDoneRoots,
                onValueChange = { maxDoneRoots = it },
                label = { Text(stringResource(R.string.max_completed_tasks_root)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Text(
                stringResource(R.string.max_completed_tasks_root_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = maxDoneSubtasks,
                onValueChange = { maxDoneSubtasks = it },
                label = { Text(stringResource(R.string.max_completed_subtasks)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Text(
                stringResource(R.string.max_completed_subtasks_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Notifications Section
            Text(
                stringResource(R.string.notifications),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = showOngoingNotifications,
                    onCheckedChange = { showOngoingNotifications = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.show_ongoing_notifications_label))
            }
            Text(
                stringResource(R.string.show_ongoing_notifications_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Quick Filter Section
            // Goals / Habits Extras (Data Management extension)
            Text(
                stringResource(R.string.goals),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = showGoalsTab,
                    onCheckedChange = { showGoalsTab = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.show_goals_tab))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                Switch(
                    checked = showTaskGoalsInSidebar,
                    onCheckedChange = { showTaskGoalsInSidebar = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.show_task_goals_in_sidebar))
            }
            OutlinedTextField(
                value = defaultDurationGoalMins,
                onValueChange = { defaultDurationGoalMins = it },
                label = { Text(stringResource(R.string.implicit_goal_duration)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Text(
                stringResource(R.string.implicit_goal_duration_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = sessionsCountAsCompletions,
                    onCheckedChange = { sessionsCountAsCompletions = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.sessions_count_as_completions))
            }
             
            // Goals
            goals.keys.toList().sorted().forEach { key ->
                val goal = goals[key]!!
                val isEditingThis = editingGoalKey == key
                
                val typeStr = if (goal.goalType == MobileGoalType.DURATION) stringResource(R.string.goal_type_duration) else stringResource(R.string.goal_type_count)
                
                val unitStr = when(goal.interval.unit) {
                    MobileIntervalUnit.DAYS -> "d"
                    MobileIntervalUnit.WEEKS -> "w"
                    MobileIntervalUnit.MONTHS -> "mo"
                    MobileIntervalUnit.YEARS -> "y"
                }
                val periodStr = if (goal.interval.amount == 1u) "/$unitStr" else "/${goal.interval.amount}$unitStr"

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        key,
                        fontWeight = FontWeight.Bold,
                        color = if (isEditingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(typeStr, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Text(goal.target.toString(), modifier = Modifier.weight(0.5f), fontSize = 12.sp)
                    Text(periodStr, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    
                    IconButton(onClick = {
                        editingGoalKey = key
                        goalInputKey = key
                        goalInputTarget = goal.target.toString()
                        goalInputType = goal.goalType
                        goalInputAmount = goal.interval.amount.toString()
                        goalInputUnit = goal.interval.unit
                    }) { NfIcon(NfIcons.EDIT, 16.sp, MaterialTheme.colorScheme.secondary) }

                    IconButton(onClick = {
                        if (editingGoalKey == key) {
                            editingGoalKey = null
                            goalInputKey = ""
                            goalInputTarget = ""
                        }
                        val newGoals = goals.toMutableMap()
                        newGoals.remove(key)
                        goals = newGoals
                        saveToDisk()
                    }) { NfIcon(NfIcons.CROSS, 16.sp, MaterialTheme.colorScheme.error) }
                }
            }
            
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = goalInputKey,
                        onValueChange = { goalInputKey = it },
                        label = { Text(stringResource(R.string.alias_key_label)) },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )
                    DropdownPicker(
                        label = "Type",
                        selected = goalInputType,
                        options = listOf(
                            MobileGoalType.COUNT to stringResource(R.string.goal_type_count),
                            MobileGoalType.DURATION to stringResource(R.string.goal_type_duration)
                        ),
                        onSelect = { goalInputType = it },
                        modifier = Modifier.weight(1.5f).padding(top = 8.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = goalInputTarget,
                        onValueChange = { goalInputTarget = it },
                        label = { Text("Target") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = goalInputAmount,
                        onValueChange = { goalInputAmount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    DropdownPicker(
                        label = "Unit",
                        selected = goalInputUnit,
                        options = listOf(
                            MobileIntervalUnit.DAYS to stringResource(R.string.interval_unit_days),
                            MobileIntervalUnit.WEEKS to stringResource(R.string.interval_unit_weeks),
                            MobileIntervalUnit.MONTHS to stringResource(R.string.interval_unit_months),
                            MobileIntervalUnit.YEARS to stringResource(R.string.interval_unit_years)
                        ),
                        onSelect = { goalInputUnit = it },
                        modifier = Modifier.weight(1.5f).padding(top = 8.dp)
                    )
                    if (editingGoalKey != null) {
                        IconButton(onClick = {
                            if (goalInputKey.isNotBlank() && goalInputTarget.isNotBlank()) {
                                var safeKey = goalInputKey.trim()
                                if (safeKey.lowercase().startsWith("loc:")) safeKey = "@@" + safeKey.substring(4).trim()
                                else if (!safeKey.startsWith("#") && !safeKey.startsWith("@@")) safeKey = "#$safeKey"

                                val targetVal = if (goalInputType == MobileGoalType.DURATION) {
                                    api.parseDurationString(goalInputTarget)?.toInt() ?: goalInputTarget.toIntOrNull() ?: 0
                                } else {
                                    goalInputTarget.toIntOrNull() ?: 0
                                }

                                if (targetVal > 0) {
                                    val newGoals = goals.toMutableMap()
                                    if (editingGoalKey != safeKey) newGoals.remove(editingGoalKey)
                                    val amt = goalInputAmount.toUIntOrNull()?.coerceAtLeast(1u) ?: 1u
                                    newGoals[safeKey] = MobileGoal(goalInputType, targetVal.toUInt(), MobileInterval(amt, goalInputUnit))
                                    goals = newGoals
                                    editingGoalKey = null
                                    goalInputKey = ""
                                    goalInputTarget = ""
                                    goalInputAmount = "1"
                                    saveToDisk()
                                }
                            }
                        }) { NfIcon(NfIcons.CHECK, 20.sp, MaterialTheme.colorScheme.primary) }
                        
                        IconButton(onClick = {
                            editingGoalKey = null
                            goalInputKey = ""
                            goalInputTarget = ""
                            goalInputAmount = "1"
                        }) { NfIcon(NfIcons.CROSS, 20.sp, MaterialTheme.colorScheme.error) }
                    } else {
                        IconButton(onClick = {
                            if (goalInputKey.isNotBlank() && goalInputTarget.isNotBlank()) {
                                var safeKey = goalInputKey.trim()
                                if (safeKey.lowercase().startsWith("loc:")) safeKey = "@@" + safeKey.substring(4).trim()
                                else if (!safeKey.startsWith("#") && !safeKey.startsWith("@@")) safeKey = "#$safeKey"

                                val targetVal = if (goalInputType == MobileGoalType.DURATION) {
                                    api.parseDurationString(goalInputTarget)?.toInt() ?: goalInputTarget.toIntOrNull() ?: 0
                                } else {
                                    goalInputTarget.toIntOrNull() ?: 0
                                }

                                if (targetVal > 0) {
                                    val newGoals = goals.toMutableMap()
                                    val amt = goalInputAmount.toUIntOrNull()?.coerceAtLeast(1u) ?: 1u
                                    newGoals[safeKey] = MobileGoal(goalInputType, targetVal.toUInt(), MobileInterval(amt, goalInputUnit))
                                    goals = newGoals
                                    goalInputKey = ""
                                    goalInputTarget = ""
                                    goalInputAmount = "1"
                                    saveToDisk()
                                }
                            }
                        }) { NfIcon(NfIcons.ADD) }
                    }
                }
            }
            
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Text(
                stringResource(R.string.quick_filter_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = showQuickFilter,
                    onCheckedChange = { showQuickFilter = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quick_filter_show_button))
            }
            OutlinedTextField(
                value = quickFilterTerm,
                onValueChange = { quickFilterTerm = it },
                label = { Text(stringResource(R.string.quick_filter_search_term)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = quickFilterIcon,
                onValueChange = { quickFilterIcon = it },
                label = { Text(stringResource(R.string.quick_filter_icon)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                singleLine = true
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Calendar Integration Section
            Text(
                stringResource(R.string.calendar_integration),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = deleteEventsOnCompletion,
                    onCheckedChange = { deleteEventsOnCompletion = it }
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete_calendar_events_on_completion_label))
            }
            Text(
                stringResource(R.string.events_deleted_on_task_delete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Data Management Section
            Text(
                stringResource(R.string.data_management),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            OutlinedTextField(
                value = trashRetention,
                onValueChange = { trashRetention = it },
                label = { Text(stringResource(R.string.trash_retention_days_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Text(
                stringResource(R.string.trash_retention_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Debug Section (Moved from SettingsScreen)
            Text(
                stringResource(R.string.export_debug_share_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                stringResource(R.string.debug_export_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = {
                    scope.launch {
                        try {
                            debugIsError = false
                            debugStatus = exportExporting
                            val zipPath = api.createDebugExport()
                            val sourceFile = File(zipPath)
                            val destFile = File(context.cacheDir, "cfait_debug_export.zip")

                            sourceFile.inputStream().use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                destFile
                            )

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            val shareIntent = Intent.createChooser(intent, exportShareTitle)
                            context.startActivity(shareIntent)
                            debugIsError = false
                            debugStatus = exportReady
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            debugIsError = true
                            debugStatus = try {
                                String.format(exportFailedTemplate, e.message ?: e.toString())
                            } catch (_: Exception) {
                                // Fallback if formatting fails
                                "${exportFailedTemplate} ${e.message ?: e.toString()}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NfIcon(NfIcons.ARCHIVE_ARROW_UP, 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export))
                }
            }

            if (debugStatus.isNotEmpty()) {
                Text(
                    text = debugStatus,
                    color = if (debugIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
