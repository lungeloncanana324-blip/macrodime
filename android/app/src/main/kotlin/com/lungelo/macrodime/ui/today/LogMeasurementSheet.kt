/*
 * LogMeasurementSheet.kt
 * MacroDime
 *
 * Non-BMI progress entry: waist, hips, weight and a progress photo. Port of
 * MacroDime/Views/Components/LogMeasurementSheet.swift. BMI cannot tell a 5 kg
 * muscle gain from a 5 kg fat gain; a waist measurement and a photo taken in
 * the same light can.
 *
 * The photo comes through the system photo picker, so the app needs no storage
 * permission and only ever sees the one image the user chose.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.lungelo.macrodime.ui.today

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lungelo.macrodime.data.MacroDimeRepository
import com.lungelo.macrodime.data.PhotoStore
import com.lungelo.macrodime.data.UserProfileEntity
import com.lungelo.macrodime.data.measurementSystem
import com.lungelo.macrodime.domain.MeasurementSystem
import com.lungelo.macrodime.domain.UnitConversion
import com.lungelo.macrodime.ui.components.Caption
import com.lungelo.macrodime.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LogMeasurementSheet(
    profile: UserProfileEntity,
    repository: MacroDimeRepository,
    photos: PhotoStore,
    onDismiss: () -> Unit,
) {
    /** The imported copy, in app storage. Deleted again if the sheet is abandoned. */
    var photoFileName by rememberSaveable { mutableStateOf<String?>(null) }
    // However the sheet is closed without saving (Cancel, a swipe, a tap
    // outside, Back), a photo imported for it is deleted.
    val abandon = {
        photoFileName?.let(photos::delete)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = abandon, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LogMeasurementContent(profile, repository, photos, photoFileName, { photoFileName = it }, onCancel = abandon, onSaved = onDismiss)
    }
}

/** The sheet's body, apart from its window so tests can render and look at it. */
@Composable
fun LogMeasurementContent(
    profile: UserProfileEntity,
    repository: MacroDimeRepository,
    photos: PhotoStore,
    photoFileName: String?,
    onPhotoChange: (String?) -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    var waist by rememberSaveable { mutableStateOf("") }
    var hips by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val isMetric = profile.measurementSystem == MeasurementSystem.Metric
    val parse = OnboardingViewModel::parseDecimal
    // Nothing to save is not an error: the Save button just stays off.
    val hasAnything = parse(waist) != null || parse(hips) != null || parse(weight) != null ||
        photoFileName != null || notes.isNotBlank()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            val imported = withContext(Dispatchers.IO) { photos.importPicked(uri) }
            isImporting = false
            if (imported == null) {
                error = "That photo could not be loaded."
            } else {
                photoFileName?.let(photos::delete)
                onPhotoChange(imported)
            }
        }
    }

    LaunchedEffect(photoFileName) {
        val name = photoFileName
        preview = if (name == null) null else withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(photos.file(name).path, options)?.asImageBitmap()
        }
    }

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Log Progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                enabled = hasAnything && !isImporting,
                onClick = {
                    val weightKg = parse(weight)?.let { if (isMetric) it else UnitConversion.kilogramsFromPounds(it) }
                    scope.launch {
                        try {
                            repository.logMeasurement(profile, weightKg, parse(waist), parse(hips), notes.trim(), photoFileName)
                            onSaved()
                        } catch (failure: Exception) {
                            error = failure.message ?: "Could not save."
                        }
                    }
                },
            ) { Text("Save") }
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Measurements", style = MaterialTheme.typography.titleSmall)
            MeasurementField("Waist", waist, "cm") { waist = it }
            MeasurementField("Hips", hips, "cm") { hips = it }
            MeasurementField("Weight", weight, if (isMetric) "kg" else "lb") { weight = it }
            Caption("Measure your waist at the navel, first thing in the morning, before eating. Consistency matters more than precision.")

            Text("Progress photo", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isImporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (photoFileName == null) "Add photo" else "Replace photo")
            }
            preview?.let { bitmap ->
                Box {
                    Image(
                        bitmap,
                        contentDescription = "Selected progress photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    FilledIconButton(
                        onClick = {
                            photoFileName?.let(photos::delete)
                            onPhotoChange(null)
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
                    ) { Icon(Icons.Rounded.Close, contentDescription = "Remove photo") }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("How are you feeling? Sleep, energy, hunger…") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Caption(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MeasurementField(label: String, value: String, unit: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> if (text.length <= 6 && text.all { it.isDigit() || it == '.' || it == ',' }) onChange(text) },
        label = { Text(label) },
        placeholder = { Text("Optional") },
        suffix = { Text(unit) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}
