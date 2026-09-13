//
//  LogMeasurementSheet.swift
//  MacroDime
//
//  Non-BMI progress entry: waist, hips, weight and a progress photo.
//
//  This is the counterweight to the BMI number on the dashboard. BMI cannot
//  distinguish a 5 kg muscle gain from a 5 kg fat gain; waist circumference and
//  a photo taken in the same light can.
//

import SwiftUI
import SwiftData
import PhotosUI
import UIKit   // `UIImage`, for rendering the picked photo back into the form

@MainActor
struct LogMeasurementSheet: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    let profile: UserProfile

    @State private var waistText: String = ""
    @State private var hipText: String = ""
    @State private var weightText: String = ""
    @State private var notes: String = ""
    @State private var photoSelection: PhotosPickerItem?
    @State private var photoData: Data?
    @State private var isLoadingPhoto = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    measurementField("Waist", text: $waistText, unit: "cm")
                    measurementField("Hips", text: $hipText, unit: "cm")
                    measurementField(
                        "Weight",
                        text: $weightText,
                        unit: profile.measurementSystem == .metric ? "kg" : "lb"
                    )
                } header: {
                    Text("Measurements")
                } footer: {
                    Text("Measure your waist at the navel, first thing in the morning, before eating. Consistency matters more than precision.")
                }

                Section("Progress photo") {
                    PhotosPicker(selection: $photoSelection, matching: .images, photoLibrary: .shared()) {
                        HStack {
                            Label(photoData == nil ? "Add photo" : "Replace photo", systemImage: "camera.fill")
                            Spacer()
                            if isLoadingPhoto { ProgressView() }
                        }
                    }

                    if let photoData, let image = UIImage(data: photoData) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                            .frame(height: 220)
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .listRowInsets(EdgeInsets())
                            .overlay(alignment: .topTrailing) {
                                Button("Remove", systemImage: "xmark.circle.fill") {
                                    self.photoData = nil
                                    photoSelection = nil
                                }
                                .labelStyle(.iconOnly)
                                .font(.title2)
                                .foregroundStyle(.white, .black.opacity(0.5))
                                .padding(8)
                            }
                    }
                }

                Section("Notes") {
                    TextField("How are you feeling? Sleep, energy, hunger…", text: $notes, axis: .vertical)
                        .lineLimit(3...6)
                }
            }
            .navigationTitle("Log Progress")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { save() }
                        .fontWeight(.semibold)
                        .disabled(!hasAnything)
                }
            }
            .onChange(of: photoSelection) { _, newValue in
                loadPhoto(from: newValue)
            }
            .alert("Could not save", isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )) {
                Button("OK", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    /// Nothing to save is not an error — it just means the Save button stays off.
    private var hasAnything: Bool {
        parsed(waistText) != nil
            || parsed(hipText) != nil
            || parsed(weightText) != nil
            || photoData != nil
            || !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func measurementField(_ title: String, text: Binding<String>, unit: String) -> some View {
        LabeledContent(title) {
            HStack(spacing: 6) {
                TextField("—", text: text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 100)
                Text(unit)
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// Accepts both `.` and `,` decimal separators, so the field works in every
    /// locale without a number formatter fighting the keyboard.
    private func parsed(_ text: String) -> Double? {
        let normalised = text
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: ",", with: ".")
        guard !normalised.isEmpty else { return nil }
        return Double(normalised)
    }

    private func loadPhoto(from item: PhotosPickerItem?) {
        guard let item else { return }
        isLoadingPhoto = true
        Task {
            defer { isLoadingPhoto = false }
            do {
                photoData = try await item.loadTransferable(type: Data.self)
            } catch {
                errorMessage = "That photo could not be loaded."
            }
        }
    }

    private func save() {
        // Weight is entered in the user's own units but stored metric, like
        // every other length and mass in the app.
        let weightKg: Double? = parsed(weightText).map { value in
            profile.measurementSystem == .metric
                ? value
                : UnitConversion.kilograms(fromPounds: value)
        }

        let measurement = BodyMeasurement(
            weightKg: weightKg,
            waistCm: parsed(waistText),
            hipCm: parsed(hipText),
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
            photoData: photoData
        )
        measurement.profile = profile
        context.insert(measurement)
        profile.measurements.append(measurement)

        // A new weight is also the profile's current weight — the prescription
        // depends on it, so leaving the profile stale would quietly keep the
        // user on last month's targets.
        if let weightKg {
            profile.weightKg = weightKg
            profile.touch()
        }

        do {
            try context.save()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
