package com.bodysense

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Shared response ──────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class FeatureContribution(
    val feature: String,
    val contribution: Double,
    val description: String
)

@JsonClass(generateAdapter = true)
data class PredictionResponse(
    val disease: String,
    val risk: Float,
    val confidence: Float,
    val method: String,
    @Json(name = "top_contributors") val topContributors: List<FeatureContribution>? = null,
    val explanation: String? = null
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    val status: String,
    val models: Map<String, String>? = null,
    val model: String? = null, // kept for legacy /health backward compat
)

// ─── Assessment field types ────────────────────────────────────────────────────
enum class FieldType { NUMBER, INTEGER, CHOICE }

/**
 * Describes one input field for an assessment.
 */
data class AssessmentField(
    val key: String,
    val label: String,
    val hint: String,
    val unit: String = "",
    val type: FieldType = FieldType.NUMBER,
    val min: Float? = null,
    val max: Float? = null,
    /** For CHOICE fields: list of (display label, numeric value) pairs */
    val choices: List<Pair<String, Float>> = emptyList(),
    val helpText: String = "",
)

/**
 * Full configuration for a disease assessment.
 * The assessment engine uses this to render the correct form automatically.
 */
data class DiseaseConfig(
    val id: String,
    val name: String,
    val description: String,
    val iconResLabel: String,   // for future icon mapping
    val accentColor: Long,
    val fields: List<AssessmentField>,
    val riskLabel: String = "${name.uppercase()} RISK",
    val educationalNote: String = "",
    val disclaimer: String = "This assessment is for informational purposes only and does not constitute medical advice. Please consult a qualified healthcare professional for diagnosis and treatment.",
)

// ─── Disease Configurations ────────────────────────────────────────────────────
object DiseaseConfigs {

    val HEART = DiseaseConfig(
        id = "heart",
        name = "Heart Disease",
        description = "Cardiovascular health risk assessment",
        iconResLabel = "favorite",
        accentColor = 0xFFE53935,
        riskLabel = "CARDIAC RISK",
        educationalNote = "Cardiovascular disease is the leading cause of death worldwide. Key risk factors include high blood pressure, high cholesterol, smoking, and physical inactivity.",
        fields = listOf(
            AssessmentField("age", "Age", "Years", "yrs", FieldType.INTEGER, 1f, 120f, helpText = "Your current age in years"),
            AssessmentField("sex", "Sex", "0=Female, 1=Male", "", FieldType.CHOICE, choices = listOf("Female" to 0f, "Male" to 1f)),
            AssessmentField("chest_pain_type", "Chest Pain Type", "0–3", "", FieldType.CHOICE, choices = listOf("Typical Angina" to 0f, "Atypical Angina" to 1f, "Non-Anginal" to 2f, "Asymptomatic" to 3f)),
            AssessmentField("resting_bp", "Resting Blood Pressure", "mm Hg", "mmHg", FieldType.INTEGER, 60f, 220f),
            AssessmentField("cholesterol", "Serum Cholesterol", "mg/dl", "mg/dl", FieldType.INTEGER, 100f, 600f),
            AssessmentField("fasting_blood_sugar", "Fasting Blood Sugar > 120 mg/dl", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("resting_ecg", "Resting ECG Result", "0–2", "", FieldType.CHOICE, choices = listOf("Normal" to 0f, "ST-T Abnormality" to 1f, "LV Hypertrophy" to 2f)),
            AssessmentField("max_heart_rate", "Maximum Heart Rate", "bpm", "bpm", FieldType.INTEGER, 50f, 250f),
            AssessmentField("exercise_angina", "Exercise-Induced Angina", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("oldpeak", "ST Depression (Oldpeak)", "mm", "mm", FieldType.NUMBER, -3f, 10f),
            AssessmentField("st_slope", "ST Slope", "", "", FieldType.CHOICE, choices = listOf("Upsloping" to 0f, "Flat" to 1f, "Downsloping" to 2f)),
        )
    )

    val DIABETES = DiseaseConfig(
        id = "diabetes",
        name = "Diabetes",
        description = "Blood sugar & insulin level screening",
        iconResLabel = "water_drop",
        accentColor = 0xFF1E88E5,
        riskLabel = "DIABETES RISK",
        educationalNote = "Type 2 diabetes affects over 422 million people globally. Risk factors include high BMI, physical inactivity, family history, and elevated blood glucose.",
        fields = listOf(
            AssessmentField("pregnancies", "Number of Pregnancies", "count", "", FieldType.INTEGER, 0f, 20f),
            AssessmentField("glucose", "Plasma Glucose", "mg/dl", "mg/dl", FieldType.INTEGER, 40f, 300f),
            AssessmentField("blood_pressure", "Diastolic Blood Pressure", "mm Hg", "mmHg", FieldType.INTEGER, 20f, 150f),
            AssessmentField("skin_thickness", "Triceps Skinfold Thickness", "mm", "mm", FieldType.INTEGER, 0f, 100f),
            AssessmentField("insulin", "2-Hour Serum Insulin", "μU/ml", "μU/ml", FieldType.INTEGER, 0f, 900f),
            AssessmentField("bmi", "BMI", "kg/m²", "kg/m²", FieldType.NUMBER, 10f, 70f),
            AssessmentField("diabetes_pedigree", "Diabetes Pedigree Function", "0.0–2.5", "", FieldType.NUMBER, 0f, 3f, helpText = "Genetic influence score based on family history"),
            AssessmentField("age", "Age", "years", "yrs", FieldType.INTEGER, 1f, 120f),
        )
    )

    val KIDNEY = DiseaseConfig(
        id = "kidney",
        name = "Kidney Disease",
        description = "Chronic kidney disease risk assessment",
        iconResLabel = "sanitizer",
        accentColor = 0xFF8E24AA,
        riskLabel = "CKD RISK",
        educationalNote = "Chronic Kidney Disease (CKD) affects 1 in 10 people worldwide. Key risk factors are diabetes, hypertension, and family history of kidney disease.",
        fields = listOf(
            AssessmentField("age", "Age", "years", "yrs", FieldType.INTEGER, 1f, 100f),
            AssessmentField("blood_pressure", "Blood Pressure", "mm Hg", "mmHg", FieldType.INTEGER, 50f, 200f),
            AssessmentField("specific_gravity", "Urine Specific Gravity", "1.005–1.030", "", FieldType.NUMBER, 1.001f, 1.040f),
            AssessmentField("albumin", "Albumin in Urine", "0–5", "", FieldType.INTEGER, 0f, 5f),
            AssessmentField("sugar", "Sugar in Urine", "0–5", "", FieldType.INTEGER, 0f, 5f),
            AssessmentField("red_blood_cells", "Red Blood Cells", "", "", FieldType.CHOICE, choices = listOf("Normal" to 0f, "Abnormal" to 1f)),
            AssessmentField("pus_cell", "Pus Cells", "", "", FieldType.CHOICE, choices = listOf("Normal" to 0f, "Abnormal" to 1f)),
            AssessmentField("pus_cell_clumps", "Pus Cell Clumps", "", "", FieldType.CHOICE, choices = listOf("Not Present" to 0f, "Present" to 1f)),
            AssessmentField("bacteria", "Bacteria", "", "", FieldType.CHOICE, choices = listOf("Not Present" to 0f, "Present" to 1f)),
            AssessmentField("blood_glucose_random", "Random Blood Glucose", "mg/dl", "mg/dl", FieldType.INTEGER, 50f, 500f),
            AssessmentField("blood_urea", "Blood Urea", "mg/dl", "mg/dl", FieldType.INTEGER, 1f, 400f),
            AssessmentField("serum_creatinine", "Serum Creatinine", "mg/dl", "mg/dl", FieldType.NUMBER, 0.1f, 80f),
            AssessmentField("sodium", "Sodium", "mEq/L", "mEq/L", FieldType.INTEGER, 100f, 170f),
            AssessmentField("potassium", "Potassium", "mEq/L", "mEq/L", FieldType.NUMBER, 1f, 10f),
            AssessmentField("haemoglobin", "Haemoglobin", "g/dl", "g/dl", FieldType.NUMBER, 3f, 20f),
            AssessmentField("packed_cell_volume", "Packed Cell Volume", "%", "%", FieldType.INTEGER, 10f, 60f),
            AssessmentField("wbc_count", "WBC Count", "cells/μL", "cells/μL", FieldType.INTEGER, 2000f, 25000f),
            AssessmentField("rbc_count", "RBC Count", "million/μL", "mil/μL", FieldType.NUMBER, 2f, 8f),
            AssessmentField("hypertension", "Hypertension", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("diabetes_mellitus", "Diabetes Mellitus", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("coronary_artery_disease", "Coronary Artery Disease", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("appetite", "Appetite", "", "", FieldType.CHOICE, choices = listOf("Good" to 0f, "Poor" to 1f)),
            AssessmentField("pedal_edema", "Pedal Edema", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("anemia", "Anemia", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
        )
    )

    val STROKE = DiseaseConfig(
        id = "stroke",
        name = "Stroke Risk",
        description = "Neurological stroke risk factor analysis",
        iconResLabel = "warning",
        accentColor = 0xFFFB8C00,
        riskLabel = "STROKE RISK",
        educationalNote = "Stroke is the second leading cause of death globally. Risk factors include high blood pressure, smoking, obesity, and irregular heartbeat.",
        fields = listOf(
            AssessmentField("age", "Age", "years", "yrs", FieldType.INTEGER, 1f, 120f),
            AssessmentField("hypertension", "Hypertension", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("heart_disease", "Heart Disease", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("ever_married", "Ever Married", "", "", FieldType.CHOICE, choices = listOf("No" to 0f, "Yes" to 1f)),
            AssessmentField("work_type", "Work Type", "", "", FieldType.CHOICE, choices = listOf("Children" to 0f, "Govt Job" to 1f, "Never Worked" to 2f, "Private" to 3f, "Self-Employed" to 4f)),
            AssessmentField("residence_type", "Residence Type", "", "", FieldType.CHOICE, choices = listOf("Rural" to 0f, "Urban" to 1f)),
            AssessmentField("avg_glucose_level", "Average Glucose Level", "mg/dl", "mg/dl", FieldType.NUMBER, 50f, 300f),
            AssessmentField("bmi", "BMI", "kg/m²", "kg/m²", FieldType.NUMBER, 10f, 70f),
            AssessmentField("smoking_status", "Smoking Status", "", "", FieldType.CHOICE, choices = listOf("Formerly Smoked" to 0f, "Never Smoked" to 1f, "Currently Smokes" to 2f, "Unknown" to 3f)),
        )
    )

    val PARKINSONS = DiseaseConfig(
        id = "parkinsons",
        name = "Parkinson's Disease",
        description = "Motor function & voice biomarker evaluation",
        iconResLabel = "accessibility",
        accentColor = 0xFF6D4C41,
        riskLabel = "PARKINSON'S RISK",
        educationalNote = "Parkinson's Disease is a progressive neurological disorder. Voice and speech biomarkers can be used as early indicators of motor dysfunction.",
        fields = listOf(
            AssessmentField("mdvp_fo", "MDVP Fo (Hz)", "Average vocal fundamental frequency", "Hz", FieldType.NUMBER),
            AssessmentField("mdvp_fhi", "MDVP Fhi (Hz)", "Maximum vocal fundamental frequency", "Hz", FieldType.NUMBER),
            AssessmentField("mdvp_flo", "MDVP Flo (Hz)", "Minimum vocal fundamental frequency", "Hz", FieldType.NUMBER),
            AssessmentField("mdvp_jitter_percent", "Jitter (%)", "MDVP Jitter in percent", "%", FieldType.NUMBER),
            AssessmentField("mdvp_jitter_abs", "Jitter (Abs)", "MDVP Absolute Jitter", "μs", FieldType.NUMBER),
            AssessmentField("mdvp_rap", "MDVP RAP", "Relative Amplitude Perturbation", "", FieldType.NUMBER),
            AssessmentField("mdvp_ppq", "MDVP PPQ", "Five-point Period Perturbation Quotient", "", FieldType.NUMBER),
            AssessmentField("jitter_ddp", "Jitter DDP", "Average difference of differences of periods", "", FieldType.NUMBER),
            AssessmentField("mdvp_shimmer", "Shimmer", "MDVP Shimmer", "", FieldType.NUMBER),
            AssessmentField("mdvp_shimmer_db", "Shimmer (dB)", "MDVP Shimmer in dB", "dB", FieldType.NUMBER),
            AssessmentField("shimmer_apq3", "APQ3", "3-point Amplitude Perturbation Quotient", "", FieldType.NUMBER),
            AssessmentField("shimmer_apq5", "APQ5", "5-point Amplitude Perturbation Quotient", "", FieldType.NUMBER),
            AssessmentField("mdvp_apq", "MDVP APQ", "11-point Amplitude Perturbation Quotient", "", FieldType.NUMBER),
            AssessmentField("shimmer_dda", "Shimmer DDA", "Average absolute differences between consecutive differences", "", FieldType.NUMBER),
            AssessmentField("nhr", "NHR", "Noise-to-harmonics ratio", "", FieldType.NUMBER),
            AssessmentField("hnr", "HNR", "Harmonics-to-noise ratio", "dB", FieldType.NUMBER),
            AssessmentField("rpde", "RPDE", "Recurrence period density entropy", "", FieldType.NUMBER, 0f, 1f),
            AssessmentField("dfa", "DFA", "Signal fractal scaling exponent", "", FieldType.NUMBER),
            AssessmentField("spread1", "Spread1", "Nonlinear measure of fundamental frequency variation", "", FieldType.NUMBER),
            AssessmentField("spread2", "Spread2", "Nonlinear measure of fundamental frequency variation", "", FieldType.NUMBER),
            AssessmentField("d2", "D2", "Correlation dimension", "", FieldType.NUMBER),
            AssessmentField("ppe", "PPE", "Pitch period entropy", "", FieldType.NUMBER, 0f, 1f),
        )
    )

    val LIVER = DiseaseConfig(
        id = "liver",
        name = "Liver Disease",
        description = "Hepatic function & enzyme level screening",
        iconResLabel = "local_hospital",
        accentColor = 0xFF43A047,
        riskLabel = "LIVER DISEASE RISK",
        educationalNote = "Liver disease encompasses conditions like hepatitis, cirrhosis, and fatty liver disease. Key biomarkers include bilirubin, ALT, and AST levels.",
        fields = listOf(
            AssessmentField("age", "Age", "years", "yrs", FieldType.INTEGER, 1f, 120f),
            AssessmentField("gender", "Gender", "", "", FieldType.CHOICE, choices = listOf("Female" to 0f, "Male" to 1f)),
            AssessmentField("total_bilirubin", "Total Bilirubin", "mg/dl", "mg/dl", FieldType.NUMBER, 0f, 80f),
            AssessmentField("direct_bilirubin", "Direct Bilirubin", "mg/dl", "mg/dl", FieldType.NUMBER, 0f, 20f),
            AssessmentField("alkaline_phosphotase", "Alkaline Phosphotase", "IU/L", "IU/L", FieldType.INTEGER, 0f, 3000f),
            AssessmentField("alamine_aminotransferase", "ALT (SGPT)", "IU/L", "IU/L", FieldType.INTEGER, 0f, 2000f),
            AssessmentField("aspartate_aminotransferase", "AST (SGOT)", "IU/L", "IU/L", FieldType.INTEGER, 0f, 5000f),
            AssessmentField("total_proteins", "Total Proteins", "g/dl", "g/dl", FieldType.NUMBER, 0f, 12f),
            AssessmentField("albumin", "Albumin", "g/dl", "g/dl", FieldType.NUMBER, 0f, 6f),
            AssessmentField("albumin_globulin_ratio", "Albumin/Globulin Ratio", "ratio", "", FieldType.NUMBER, 0f, 5f),
        )
    )

    val LUNG = DiseaseConfig(
        id = "lung",
        name = "Lung Disease",
        description = "Respiratory capacity & cancer risk screening",
        iconResLabel = "air",
        accentColor = 0xFF00ACC1,
        riskLabel = "LUNG CANCER RISK",
        educationalNote = "Lung cancer is the most common cause of cancer death worldwide. Smoking is the single largest risk factor, accounting for 85% of all cases.",
        fields = listOf(
            AssessmentField("gender", "Gender", "", "", FieldType.CHOICE, choices = listOf("Female" to 0f, "Male" to 1f)),
            AssessmentField("age", "Age", "years", "yrs", FieldType.INTEGER, 1f, 120f),
            AssessmentField("smoking", "Smoking", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("yellow_fingers", "Yellow Fingers", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("anxiety", "Anxiety", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("peer_pressure", "Peer Pressure", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("chronic_disease", "Chronic Disease", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("fatigue", "Fatigue", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("allergy", "Allergy", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("wheezing", "Wheezing", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("alcohol_consuming", "Alcohol Consuming", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("coughing", "Coughing", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("shortness_of_breath", "Shortness of Breath", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("swallowing_difficulty", "Swallowing Difficulty", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
            AssessmentField("chest_pain", "Chest Pain", "", "", FieldType.CHOICE, choices = listOf("No" to 1f, "Yes" to 2f)),
        )
    )

    val ALL: Map<String, DiseaseConfig> = mapOf(
        HEART.id to HEART,
        DIABETES.id to DIABETES,
        KIDNEY.id to KIDNEY,
        STROKE.id to STROKE,
        PARKINSONS.id to PARKINSONS,
        LIVER.id to LIVER,
        LUNG.id to LUNG,
    )

    fun forId(id: String): DiseaseConfig = ALL[id] ?: HEART
}
