package dev.anujyadav.harmoniq.ui.view.nowPlaying


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anujyadav.harmoniq.services.radio.Radio
import dev.anujyadav.harmoniq.ui.components.ScaffoldDialog
import dev.anujyadav.harmoniq.ui.helpers.ViewContext
import dev.anujyadav.harmoniq.utils.AppStrings
import dev.anujyadav.harmoniq.services.playback.SleepTimerPresets
import dev.anujyadav.harmoniq.utils.DurationUtils
import java.time.Duration
import java.util.Timer
import kotlin.concurrent.timer

@Composable
fun NowPlayingSleepTimerDialog(
    context: ViewContext,
    sleepTimer: Radio.SleepTimer,
    onDismissRequest: () -> Unit,
) {
    var updateTimer by remember { mutableStateOf<Timer?>(null) }
    val endsAtMs by remember { mutableLongStateOf(sleepTimer.endsAt) }
    var endsIn by remember { mutableLongStateOf(0L) }

    LaunchedEffect(LocalContext.current) {
        updateTimer = timer(period = 500L) {
            endsIn = endsAtMs - System.currentTimeMillis()
        }
    }

    DisposableEffect(LocalContext.current) {
        onDispose {
            updateTimer?.cancel()
            updateTimer = null
        }
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(AppStrings.SleepTimer)
        },
        content = {
            Text(
                DurationUtils.formatMs(endsIn),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = 12.dp,
                    ),
            )
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                var quitOnEnd by remember { mutableStateOf(sleepTimer.quitOnEnd) }

                Checkbox(
                    checked = quitOnEnd,
                    onCheckedChange = {
                        quitOnEnd = it
                        sleepTimer.quitOnEnd = it
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    AppStrings.QuitAppOnEnd,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    context.symphony.radio.clearSleepTimer()
                    onDismissRequest()
                }
            ) {
                Text(AppStrings.Stop)
            }
        },
    )
}

@Composable
fun NowPlayingSleepTimerSetDialog(
    context: ViewContext,
    onDismissRequest: () -> Unit,
) {
    val minDurationMs = Duration.ofMinutes(1).toMillis()
    var inputHours by remember { mutableLongStateOf(0L) }
    var inputMinutes by remember { mutableLongStateOf(10L) }
    var quitOnEnd by remember { mutableStateOf(false) }
    val inputDuration by remember(inputHours, inputMinutes) {
        derivedStateOf {
            Duration
                .ofHours(inputHours)
                .plusMinutes(inputMinutes)
                .toMillis()
        }
    }
    val isValidDuration by remember(inputDuration, minDurationMs) {
        derivedStateOf { inputDuration >= minDurationMs }
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(AppStrings.SleepTimer)
        },
        content = {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        4.dp,
                        Alignment.CenterHorizontally
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SleepTimerPresets.all.forEach { x ->
                        val hours = x.hours
                        val minutes = x.minutes
                        val shape = RoundedCornerShape(4.dp)

                        Text(
                            DurationUtils.formatMinSec(0L, 0L, hours, minutes),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    shape,
                                )
                                .clip(shape)
                                .clickable {
                                    inputHours = hours
                                    inputMinutes = minutes
                                }
                                .padding(8.dp, 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.padding(20.dp, 0.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = DividerDefaults.color,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = {
                            Text(AppStrings.Hours)
                        },
                        value = inputHours.toString(),
                        onValueChange = {
                            inputHours = it.toLongOrNull() ?: 0
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = DividerDefaults.color,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = {
                            Text(AppStrings.Minutes)
                        },
                        value = inputMinutes.toString(),
                        onValueChange = {
                            inputMinutes = it.toLongOrNull() ?: 0
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp, 0.dp),
                ) {
                    Checkbox(
                        checked = quitOnEnd,
                        onCheckedChange = {
                            quitOnEnd = it
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(AppStrings.QuitAppOnEnd)
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(AppStrings.Cancel)
            }
            TextButton(
                enabled = isValidDuration,
                onClick = {
                    context.symphony.radio.setSleepTimer(
                        duration = inputDuration,
                        quitOnEnd = quitOnEnd,
                    )
                    onDismissRequest()
                }
            ) {
                Text(AppStrings.Done)
            }
        },
    )
}
