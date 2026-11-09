package com.staninna.aufmachen.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.staninna.aufmachen.R
import com.staninna.aufmachen.container
import com.staninna.aufmachen.data.LockMode
import kotlinx.coroutines.delay

private const val MAX_TRIES = 5
private const val LOCKOUT_SECONDS = 30

@Composable
fun LockScreen(mode: LockMode, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val title = stringResource(R.string.setup_unlock_biometric_title)
    val canBio = remember { BiometricSupport.available(ctx) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var tries by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    if (mode == LockMode.BIOMETRIC) {
        LaunchedEffect(Unit) { if (canBio) BiometricSupport.prompt(ctx, title, onUnlocked) }
    }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            secondsLeft = ((lockedUntil - System.currentTimeMillis() + 999) / 1000).toInt()
            delay(250)
        }
        secondsLeft = 0
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.systemBarsPadding().padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.setup_unlock_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            if (mode == LockMode.BIOMETRIC) {
                if (!canBio) {
                    Text(
                        stringResource(R.string.setup_unlock_biometric_unavailable),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Button(onClick = { BiometricSupport.prompt(ctx, title, onUnlocked) }, enabled = canBio) {
                    Text(stringResource(R.string.setup_unlock_retry))
                }
            } else {
                Text(stringResource(R.string.setup_unlock_enter_pin), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                PinDots(pin.length)
                Spacer(Modifier.height(8.dp))
                val msg = if (secondsLeft > 0) stringResource(R.string.setup_unlock_locked_out, secondsLeft) else error
                Text(
                    msg.orEmpty(), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.height(20.dp),
                )
                Spacer(Modifier.height(8.dp))
                PinPad(
                    enabled = secondsLeft == 0,
                    onDigit = { if (pin.length < 8) { pin += it; error = null } },
                    onDelete = { pin = pin.dropLast(1) },
                    onSubmit = {
                        if (pin.length >= 4) {
                            if (ctx.container.secrets.checkPin(pin)) {
                                onUnlocked()
                            } else {
                                tries++
                                pin = ""
                                if (tries >= MAX_TRIES) {
                                    tries = 0
                                    error = null
                                    lockedUntil = System.currentTimeMillis() + LOCKOUT_SECONDS * 1000L
                                } else {
                                    error = ctx.getString(R.string.setup_unlock_wrong_pin, MAX_TRIES - tries)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(16.dp)) {
        repeat(count) {
            Spacer(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, onDigit: (String) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit) {
    val keySize = Modifier.size(width = 76.dp, height = 56.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d ->
                    FilledTonalButton(onClick = { onDigit(d.toString()) }, modifier = keySize, enabled = enabled) {
                        Text(d.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDelete, modifier = keySize, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.setup_pad_delete))
            }
            FilledTonalButton(onClick = { onDigit("0") }, modifier = keySize, enabled = enabled) {
                Text("0", style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = onSubmit, modifier = keySize, enabled = enabled) { Text(stringResource(R.string.setup_ok)) }
        }
    }
}
