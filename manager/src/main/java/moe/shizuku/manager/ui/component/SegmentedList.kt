package moe.shizuku.manager.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A Material 3 list rendered as a single rounded container with dividers between
 * rows (KernelSU-style "segmented list").
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    content: @Composable SegmentedColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val scope = SegmentedColumnScope()
        Column { scope.content() }
    }
}

class SegmentedColumnScope {
    private var count = 0

    @Composable
    fun item(content: @Composable () -> Unit) {
        if (count > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        count++
        content()
    }
}

@Composable
fun SegmentedListItem(
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}
