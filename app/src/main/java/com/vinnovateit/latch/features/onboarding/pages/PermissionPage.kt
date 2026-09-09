package com.vinnovateit.latch.features.onboarding.pages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.vinnovateit.latch.features.onboarding.components.SlideContent
import com.vinnovateit.latch.ui.theme.SatoshiFontFamily

private fun isNotificationPermissionGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

/**
 * Notification permission state that follows the lifecycle.
 *
 * After a permanent denial the launcher returns denied without showing a dialog,
 * so the only way left to grant it is system Settings. Reading the permission
 * once would leave the page stuck behind a dead button when the user comes back
 * having granted it there.
 */
@Composable
private fun rememberNotificationPermissionState(): MutableState<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.value = isNotificationPermissionGranted(context)
    }
    return state
}

@Composable
fun NotificationPermissionPage(
    slide: SlideContent,
    onPermissionGranted: () -> Unit
) {
    var isGranted by rememberNotificationPermissionState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        if (granted) onPermissionGranted()
    }

    LaunchedEffect(isGranted) {
        if (isGranted) onPermissionGranted()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = slide.title,
            fontFamily = SatoshiFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 28.dp)
                .statusBarsPadding()
                .align(Alignment.TopStart)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(180.dp)
                ) { slide.icon() }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = slide.description.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    textAlign = TextAlign.Center,
                    fontFamily = SatoshiFontFamily
                )

                Spacer(modifier = Modifier.height(44.dp))

                Button(
                    onClick = {
                        if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    enabled = !isGranted,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    if (isGranted) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isGranted) "Permission Granted" else "Grant Permission",
                        fontFamily = SatoshiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}


@Composable
fun NotificationPermissionPageLandscape(slide: SlideContent, onPermissionGranted: () -> Unit) {
    var isGranted by rememberNotificationPermissionState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        if (granted) onPermissionGranted()
    }
    LaunchedEffect(isGranted) {
        if (isGranted) onPermissionGranted()
    }

    PageScaffoldLandscape(slide) {
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            enabled = !isGranted,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
        ) {
            if (isGranted) {
                Icon(Icons.Rounded.Check, null)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isGranted) "Permission Granted" else "Grant Permission",
                fontFamily = SatoshiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}