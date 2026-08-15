package com.jbsan.ldapadvisor.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jbsan.ldapadvisor.R
import com.jbsan.ldapadvisor.domain.model.DiagnosticStatus
import com.jbsan.ldapadvisor.ui.ComposeModifier
import com.jbsan.ldapadvisor.ui.theme.StatusError
import com.jbsan.ldapadvisor.ui.theme.StatusErrorContainer
import com.jbsan.ldapadvisor.ui.theme.StatusInfo
import com.jbsan.ldapadvisor.ui.theme.StatusInfoContainer
import com.jbsan.ldapadvisor.ui.theme.StatusRunning
import com.jbsan.ldapadvisor.ui.theme.StatusRunningContainer
import com.jbsan.ldapadvisor.ui.theme.StatusSkipped
import com.jbsan.ldapadvisor.ui.theme.StatusSkippedContainer
import com.jbsan.ldapadvisor.ui.theme.StatusSuccess
import com.jbsan.ldapadvisor.ui.theme.StatusSuccessContainer
import com.jbsan.ldapadvisor.ui.theme.StatusUnsupported
import com.jbsan.ldapadvisor.ui.theme.StatusUnsupportedContainer
import com.jbsan.ldapadvisor.ui.theme.StatusWarning
import com.jbsan.ldapadvisor.ui.theme.StatusWarningContainer

data class StatusVisual(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val color: Color,
    val container: Color,
)

fun statusVisual(status: DiagnosticStatus): StatusVisual = when (status) {
    DiagnosticStatus.SUCCESS -> StatusVisual(Icons.Filled.CheckCircle, R.string.status_success, StatusSuccess, StatusSuccessContainer)
    DiagnosticStatus.WARNING -> StatusVisual(Icons.Filled.Warning, R.string.status_warning, StatusWarning, StatusWarningContainer)
    DiagnosticStatus.ERROR -> StatusVisual(Icons.Filled.Error, R.string.status_error, StatusError, StatusErrorContainer)
    DiagnosticStatus.INFO -> StatusVisual(Icons.Filled.Info, R.string.status_information, StatusInfo, StatusInfoContainer)
    DiagnosticStatus.RUNNING -> StatusVisual(Icons.Filled.Sync, R.string.status_running, StatusRunning, StatusRunningContainer)
    DiagnosticStatus.SKIPPED -> StatusVisual(Icons.Filled.RemoveCircleOutline, R.string.status_skipped, StatusSkipped, StatusSkippedContainer)
    DiagnosticStatus.UNSUPPORTED -> StatusVisual(Icons.Filled.Block, R.string.status_unsupported, StatusUnsupported, StatusUnsupportedContainer)
}

@Composable
fun StatusChip(status: DiagnosticStatus, modifier: ComposeModifier = ComposeModifier) {
    val visual = statusVisual(status)
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(stringResource(visual.labelRes)) },
        leadingIcon = {
            Icon(visual.icon, contentDescription = stringResource(visual.labelRes))
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = visual.container,
            disabledLabelColor = visual.color,
            disabledLeadingIconContentColor = visual.color,
        ),
    )
}

@Composable
fun SessionBanner(
    text: String,
    isWarning: Boolean,
    modifier: ComposeModifier = ComposeModifier,
) {
    Surface(
        modifier = modifier,
        color = if (isWarning) StatusWarningContainer else StatusInfoContainer,
        contentColor = if (isWarning) StatusWarning else StatusInfo,
    ) {
        Row(
            modifier = ComposeModifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
            )
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: ComposeModifier = ComposeModifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** App mark (icon only) or wordmark (logo + text). */
@Composable
fun AppBrandImage(
    @DrawableRes drawableRes: Int,
    modifier: ComposeModifier = ComposeModifier,
    height: Dp = 96.dp,
    contentScale: ContentScale = ContentScale.Fit,
) {
    Image(
        painter = painterResource(drawableRes),
        contentDescription = stringResource(R.string.cd_app_logo),
        modifier = modifier.height(height).fillMaxWidth(),
        contentScale = contentScale,
    )
}

@Composable
fun AppLogoMark(
    modifier: ComposeModifier = ComposeModifier,
    size: Dp = 72.dp,
) {
    Image(
        painter = painterResource(R.drawable.logo_mark),
        contentDescription = stringResource(R.string.cd_app_logo),
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
