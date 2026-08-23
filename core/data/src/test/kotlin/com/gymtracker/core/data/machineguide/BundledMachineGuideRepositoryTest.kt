package com.gymtracker.core.data.machineguide

import com.gymtracker.core.domain.machineguide.MachineDemonstration
import com.gymtracker.core.domain.model.ExerciseId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundledMachineGuideRepositoryTest {
    @Test
    fun `an exact exercise id returns its reviewed guide`() =
        runBlocking {
            val repository = repository(GOOD_GUIDE)

            val guide = repository.observeFor(ExerciseId(LEG_PRESS_ID)).first()

            assertEquals("REP Fitness", guide?.manufacturer)
            assertEquals("LP-5000", guide?.model)
            assertEquals(MachineDemonstration.LEG_PRESS, guide?.demonstration)
            assertEquals(listOf("Set the back pad"), guide?.cues?.setup)
        }

    @Test
    fun `unknown and merely similar ids are absent`() =
        runBlocking {
            val repository = repository(GOOD_GUIDE)

            assertNull(repository.observeFor(ExerciseId("Leg Press")).first())
            assertNull(repository.observeFor(ExerciseId("$LEG_PRESS_ID-variant")).first())
        }

    @Test
    fun `missing review provenance fails closed`() =
        runBlocking {
            val unreviewed = GOOD_GUIDE.replace("Gym trainer", "")

            assertNull(repository(unreviewed).observeFor(ExerciseId(LEG_PRESS_ID)).first())
        }

    @Test
    fun `duplicate mappings fail closed instead of picking one`() =
        runBlocking {
            val duplicate = "[${GOOD_GUIDE.removePrefix("[").removeSuffix("]")}," +
                GOOD_GUIDE.removePrefix("[").removeSuffix("]") + "]"

            assertNull(repository(duplicate).observeFor(ExerciseId(LEG_PRESS_ID)).first())
        }

    @Test
    fun `malformed bundled data is absent rather than crashing exercise detail`() =
        runBlocking {
            assertNull(repository("not json").observeFor(ExerciseId(LEG_PRESS_ID)).first())
        }

    @Test
    fun `the bundled asset is parsed once for repeated observations`() =
        runBlocking {
            val reader = CountingReader(GOOD_GUIDE)
            val repository = BundledMachineGuideRepository(reader, Json { ignoreUnknownKeys = true })

            repository.observeFor(ExerciseId(LEG_PRESS_ID)).first()
            repository.observeFor(ExerciseId("unknown")).first()

            assertEquals(1, reader.readCount)
        }

    private fun repository(json: String) =
        BundledMachineGuideRepository(CountingReader(json), Json { ignoreUnknownKeys = true })

    private class CountingReader(
        private val json: String,
    ) : MachineGuideAssetReader {
        var readCount = 0
            private set

        override suspend fun read(): String {
            readCount += 1
            return json
        }
    }

    private companion object {
        const val LEG_PRESS_ID = "492fa83f-3134-5d16-8b03-386dada93dad"
        val GOOD_GUIDE =
            """
            [
              {
                "id": "leg-press-lp-5000-v1",
                "exerciseId": "$LEG_PRESS_ID",
                "manufacturer": "REP Fitness",
                "model": "LP-5000",
                "setup": ["Set the back pad"],
                "movement": ["Press the platform away"],
                "checkpoints": ["Keep hips on the pad"],
                "manualReference": "https://example.test/lp-5000-manual",
                "reviewer": "Gym trainer",
                "reviewedAt": "2026-08-23",
                "demonstration": "LEG_PRESS"
              }
            ]
            """.trimIndent()
    }
}
