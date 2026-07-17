package com.bodysense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the DiseaseConfig system and data models.
 * These are pure JVM tests — no Robolectric required.
 */
class DiseaseConfigTest {

    @Test
    fun `all seven disease configs are present`() {
        val expected = setOf("heart", "diabetes", "kidney", "stroke", "parkinsons", "liver", "lung")
        assertEquals(expected, DiseaseConfigs.ALL.keys)
    }

    @Test
    fun `forId returns correct config`() {
        val heart = DiseaseConfigs.forId("heart")
        assertEquals("heart", heart.id)
        assertEquals("Heart Disease", heart.name)
    }

    @Test
    fun `forId with unknown id falls back to heart`() {
        val result = DiseaseConfigs.forId("nonexistent")
        assertEquals("heart", result.id)
    }

    @Test
    fun `each disease config has at least one field`() {
        DiseaseConfigs.ALL.values.forEach { config ->
            assertTrue(
                "Disease ${config.id} must have at least 1 field",
                config.fields.isNotEmpty()
            )
        }
    }

    @Test
    fun `each field has a non-blank key and label`() {
        DiseaseConfigs.ALL.values.forEach { config ->
            config.fields.forEach { field ->
                assertTrue("Field key must not be blank in ${config.id}", field.key.isNotBlank())
                assertTrue("Field label must not be blank in ${config.id}", field.label.isNotBlank())
            }
        }
    }

    @Test
    fun `choice fields have at least two options`() {
        DiseaseConfigs.ALL.values.forEach { config ->
            config.fields.filter { it.type == FieldType.CHOICE }.forEach { field ->
                assertTrue(
                    "Choice field '${field.key}' in ${config.id} must have >= 2 choices",
                    field.choices.size >= 2
                )
            }
        }
    }

    @Test
    fun `heart disease has expected field count`() {
        assertEquals(11, DiseaseConfigs.HEART.fields.size)
    }

    @Test
    fun `diabetes has expected field count`() {
        assertEquals(8, DiseaseConfigs.DIABETES.fields.size)
    }

    @Test
    fun `parkinsons has expected field count`() {
        assertEquals(22, DiseaseConfigs.PARKINSONS.fields.size)
    }

    @Test
    fun `prediction response data class equality works`() {
        val r1 = PredictionResponse("heart", 45.5f, 0.87f, "ml_model")
        val r2 = PredictionResponse("heart", 45.5f, 0.87f, "ml_model")
        assertEquals(r1, r2)
    }

    @Test
    fun `all disease configs have non-blank names and descriptions`() {
        DiseaseConfigs.ALL.values.forEach { config ->
            assertTrue("Name blank for ${config.id}", config.name.isNotBlank())
            assertTrue("Description blank for ${config.id}", config.description.isNotBlank())
        }
    }

    @Test
    fun `all disease configs have non-blank disclaimers`() {
        DiseaseConfigs.ALL.values.forEach { config ->
            assertTrue("Disclaimer blank for ${config.id}", config.disclaimer.isNotBlank())
        }
    }
}
