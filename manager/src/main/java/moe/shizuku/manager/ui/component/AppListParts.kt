package moe.shizuku.manager.ui.component

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R

/**
 * The pictures the two app screens share: a filter chip that carries its own count, the
 * count badge inside it, app icons, the state chip and the centred block the empty states
 * are built from. They live here rather than in either screen because Apps and Manage are
 * the same list about different questions, and they should not drift apart.
 */

/** The label, or the package name when there is no application record to read one from. */
fun appLabel(pm: PackageManager, pi: PackageInfo): String =
    runCatching { pi.applicationInfo?.loadLabel(pm)?.toString() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: pi.packageName

/**
 * One filter, sized to its share of the row rather than to its label, with its label and how
 * many apps it holds centred together. A stock chip sizes to its text, which left the four
 * ragged on the left and hid the counts somewhere else entirely.
 */
@Composable
fun AppFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    // A row of chips that shares the width evenly wants each chip to fill its slot; a row
    // that scrolls, because there are more filters than fit, wants them to hug their text.
    fill: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            1.dp,
            if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = if (fill) {
                Modifier.fillMaxSize().padding(horizontal = 6.dp)
            } else {
                Modifier.padding(horizontal = 12.dp)
            },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            // Only the filter in force carries its number: the four labels together read
            // as one row of names, and the count for the one you picked is the count you
            // were asking about. It also leaves the label the room it needs to stay whole.
            if (selected) {
                Spacer(modifier = Modifier.width(6.dp))
                CountBadge(count, selected)
            }
        }
    }
}

/**
 * Just the number, in a small rounded chip the same shape family as the filter it sits
 * in, rather than a circle, so a selected filter reads as one object. Enough to read at a
 * glance, not enough to shout.
 */
@Composable
fun CountBadge(count: Int, selected: Boolean) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(MaterialTheme.shapes.small)
            // Background before padding, so the tint covers the whole chip and not just
            // the space the digits take up inside it.
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            )
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * A short label in a small rounded chip, the same shape family as the filter chips and the
 * count inside them.
 *
 * A row's state belongs here rather than at the end of the line under the name: that line is
 * the package name, which is long, and a suffix bolted onto it is the first thing a narrow
 * screen cuts off leaving a stray separator and no state at all.
 */
/** How much a chip wants to be noticed. */
enum class ChipEmphasis {
    /** A fact about the app: it came with the system. */
    NONE,

    /** A state worth seeing disabled, suspended without shouting it. */
    SOFT,

    /** Something is wrong: the app is not installed any more. */
    WARN
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: ChipEmphasis = ChipEmphasis.NONE
) {
    val container = when (emphasis) {
        ChipEmphasis.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest
        ChipEmphasis.SOFT -> MaterialTheme.colorScheme.secondaryContainer
        ChipEmphasis.WARN -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (emphasis) {
        ChipEmphasis.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        ChipEmphasis.SOFT -> MaterialTheme.colorScheme.onSecondaryContainer
        ChipEmphasis.WARN -> MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        color = content,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * What a row says about the app beside its name.
 *
 * Two at most: what kind of app it is - system or user, which is a fact about the app that
 * nothing else in the list shows - and one thing worth knowing about its state, most actionable
 * first: gone from this user, suspended, disabled, or with no launcher entry. Always at least
 * the kind, so a row that says nothing else still says what it is.
 */
fun appStatusChips(
    pi: PackageInfo,
    hidden: Boolean,
    removed: Boolean = false
): List<Pair<Int, ChipEmphasis>> {
    val ai = pi.applicationInfo
    if (removed || ai == null) {
        return listOf(R.string.manage_status_removed to ChipEmphasis.WARN)
    }

    val chips = mutableListOf(
        (if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) R.string.manage_status_system
        else R.string.manage_status_user) to ChipEmphasis.NONE
    )
    val flags = ai.flags
    when {
        flags and ApplicationInfo.FLAG_SUSPENDED != 0 ->
            chips += R.string.manage_status_suspended to ChipEmphasis.SOFT

        !ai.enabled -> chips += R.string.manage_status_disabled to ChipEmphasis.SOFT

        hidden -> chips += R.string.manage_status_hidden to ChipEmphasis.NONE
    }
    return chips
}

/**
 * The chips of [appStatusChips], laid out for a row's trailing slot.
 *
 * Stacked, not side by side. Two chips in a row take the width the name needs, and a name
 * squeezed into what is left wraps one letter per line - which is what "3 Button Navigation
 * Bar" did next to System and No launcher icon. A taller row is the better trade, and one chip
 * is a column of one.
 */
@Composable
fun AppStatusChips(
    pi: PackageInfo,
    hidden: Boolean,
    removed: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        appStatusChips(pi, hidden, removed).forEach { (label, emphasis) ->
            StatusChip(stringResource(label), emphasis = emphasis)
        }
    }
}

/** Centred content for the states that aren't a list. */
@Composable
fun CenteredMessage(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
fun AppIcon(pi: PackageInfo) {
    val context = LocalContext.current
    val icon = remember(pi.packageName) {
        runCatching { pi.applicationInfo!!.loadIcon(context.packageManager) }.getOrNull()
    }
    AppIcon(icon)
}

/**
 * The icon for a package that has no [PackageInfo] to hand an app that is only listed by
 * name, or one whose application record is gone because it is no longer installed.
 */
@Composable
fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    AppIcon(icon)
}

@Composable
private fun AppIcon(drawable: android.graphics.drawable.Drawable?) {
    val bitmap by produceState<ImageBitmap?>(null, drawable) {
        value = withContext(Dispatchers.IO) {
            runCatching { drawable?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap = bitmap!!, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Spacer(modifier = Modifier.size(40.dp))
    }
}
