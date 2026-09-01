package com.smsforwarder.oppo.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsforwarder.oppo.domain.model.FilterRule
import com.smsforwarder.oppo.domain.model.FilterRuleType

/**
 * SMS Filter Rules screen — full CRUD UI.
 *
 * Rules are grouped into two sections:
 *   • SENDER RULES  — EXACT_SENDER and SENDER_CONTAINS
 *   • KEYWORD RULES — BODY_CONTAINS
 *
 * Each rule has:
 *   • Toggle switch (enable / disable)
 *   • Delete button
 *   • Visual indicator of rule type
 *
 * FAB opens the Add Rule dialog.
 *
 * IMPORTANT: Disabled rules are shown greyed out but NOT deleted.
 * This lets users re-enable default Indian bank sender IDs without
 * having to remember or re-type them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: FilterRulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar on feedback messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Filter Rules", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add filter rule")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val senderRules = uiState.rules.filter {
            it.type == FilterRuleType.EXACT_SENDER || it.type == FilterRuleType.SENDER_CONTAINS
        }
        val keywordRules = uiState.rules.filter { it.type == FilterRuleType.BODY_CONTAINS }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp) // FAB clearance
        ) {
            // ─── Info Banner ───────────────────────────────────────
            item {
                InfoBanner(
                    text = "A message is forwarded if ANY enabled rule matches. " +
                           "All default rules are disabled until you enable them."
                )
            }

            // ─── Sender Rules section ──────────────────────────────
            item {
                SectionHeader(
                    title = "SENDER RULES",
                    subtitle = "${senderRules.count { it.enabled }} of ${senderRules.size} enabled",
                    icon = Icons.Outlined.Person
                )
            }

            if (senderRules.isEmpty()) {
                item { EmptySection("No sender rules — tap + to add one") }
            } else {
                items(senderRules, key = { it.id }) { rule ->
                    FilterRuleRow(
                        rule = rule,
                        onToggle = { viewModel.toggleRule(rule, it) },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
            }

            // ─── Keyword Rules section ─────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "KEYWORD RULES",
                    subtitle = "${keywordRules.count { it.enabled }} of ${keywordRules.size} enabled",
                    icon = Icons.Outlined.Search
                )
            }

            if (keywordRules.isEmpty()) {
                item { EmptySection("No keyword rules — tap + to add one") }
            } else {
                items(keywordRules, key = { it.id }) { rule ->
                    FilterRuleRow(
                        rule = rule,
                        onToggle = { viewModel.toggleRule(rule, it) },
                        onDelete = { viewModel.deleteRule(rule) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }

        // ─── Add Rule Dialog ───────────────────────────────────────
        if (uiState.showAddDialog) {
            AddRuleDialog(
                type = uiState.addDialogType,
                value = uiState.addDialogValue,
                error = uiState.addDialogError,
                onTypeChange = viewModel::updateAddDialogType,
                onValueChange = viewModel::updateAddDialogValue,
                onConfirm = viewModel::confirmAddRule,
                onDismiss = viewModel::dismissAddDialog
            )
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptySection(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun FilterRuleRow(
    rule: FilterRule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val alpha = if (rule.enabled) 1f else 0.45f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(if (rule.enabled) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rule type chip
            RuleTypeChip(rule.type, alpha)
            Spacer(Modifier.width(10.dp))

            // Rule value
            Text(
                text = rule.value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (rule.enabled) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                modifier = Modifier.weight(1f)
            )

            // Toggle
            Switch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Delete
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete rule",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RuleTypeChip(type: FilterRuleType, alpha: Float) {
    val (label, color) = when (type) {
        FilterRuleType.EXACT_SENDER    -> Pair("EXACT",    MaterialTheme.colorScheme.primary)
        FilterRuleType.SENDER_CONTAINS -> Pair("CONTAINS", MaterialTheme.colorScheme.secondary)
        FilterRuleType.BODY_CONTAINS   -> Pair("KEYWORD",  MaterialTheme.colorScheme.tertiary)
    }
    Surface(
        color = color.copy(alpha = 0.15f * alpha + 0.05f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = alpha),
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    type: FilterRuleType,
    value: String,
    error: String?,
    onTypeChange: (FilterRuleType) -> Unit,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Filter Rule", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Rule type selector
                Text(
                    "Rule type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterRuleType.values().forEach { ruleType ->
                        val selected = ruleType == type
                        FilterChip(
                            selected = selected,
                            onClick = { onTypeChange(ruleType) },
                            label = {
                                Text(
                                    when (ruleType) {
                                        FilterRuleType.EXACT_SENDER    -> "Exact Sender"
                                        FilterRuleType.SENDER_CONTAINS -> "Sender Contains"
                                        FilterRuleType.BODY_CONTAINS   -> "Body Keyword"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                // Helper text
                Text(
                    when (type) {
                        FilterRuleType.EXACT_SENDER    ->
                            "e.g. SBIINB — matches only this exact sender ID (prefix VM-/VD- stripped automatically)"
                        FilterRuleType.SENDER_CONTAINS ->
                            "e.g. HDFC — matches any sender whose ID contains this text"
                        FilterRuleType.BODY_CONTAINS   ->
                            "e.g. OTP — matches any message whose body contains this word"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Value input
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = {
                        Text(
                            when (type) {
                                FilterRuleType.EXACT_SENDER    -> "Sender ID"
                                FilterRuleType.SENDER_CONTAINS -> "Sender substring"
                                FilterRuleType.BODY_CONTAINS   -> "Keyword"
                            }
                        )
                    },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        if (error != null) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Add Rule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

