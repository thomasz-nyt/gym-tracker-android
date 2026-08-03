package com.gymtracker.feature.catalog

import app.cash.turbine.test
import com.gymtracker.core.domain.exercise.ExerciseCatalog
import com.gymtracker.core.domain.member.CurrentMember
import com.gymtracker.core.domain.model.BodyPart
import com.gymtracker.core.domain.model.Equipment
import com.gymtracker.core.domain.model.Exercise
import com.gymtracker.core.domain.model.ExerciseId
import com.gymtracker.core.domain.model.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-12 and US-13 as the browse and detail screens see them. Hand-written fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val member = UserId("alice")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun exercise(
        name: String,
        primary: List<BodyPart>,
        equipment: Equipment,
        aliases: List<String> = emptyList(),
        instructions: List<String> = emptyList(),
    ) = Exercise(
        id = ExerciseId(name.lowercase().replace(' ', '-')),
        name = name,
        aliases = aliases,
        primaryMuscles = primary,
        secondaryMuscles = emptyList(),
        equipment = equipment,
        instructions = instructions,
        mediaUrl = null,
        mediaType = null,
        youtubeUrl = null,
        source = "test",
    )

    private val butterfly =
        exercise("Butterfly", listOf(BodyPart.CHEST), Equipment.MACHINE, aliases = listOf("pec deck"))
    private val benchPress =
        exercise("Barbell Bench Press", listOf(BodyPart.CHEST), Equipment.BARBELL, instructions = listOf("Press."))
    private val latPulldown = exercise("Lat Pulldown", listOf(BodyPart.BACK), Equipment.CABLE)
    private val abRoller = exercise("Ab Roller", listOf(BodyPart.CORE), Equipment.UNSPECIFIED)

    private val catalog = FakeCatalog(listOf(butterfly, benchPress, latPulldown, abRoller))

    private fun viewModel() = CatalogViewModel(catalog, FakeCurrentMember(member))

    private suspend fun CatalogViewModel.names(): List<String> = uiState.first { !it.isLoading }.results.map { it.name }

    @Test
    fun `browse opens on the whole catalog, in the order the catalog ranked it`() =
        runTest {
            assertEquals(
                listOf("Butterfly", "Barbell Bench Press", "Lat Pulldown", "Ab Roller"),
                viewModel().names(),
            )
        }

    @Test
    fun `typing narrows by name`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onQueryChanged("bench")

            assertEquals(listOf("Barbell Bench Press"), viewModel.names())
        }

    @Test
    fun `typing narrows by alias, so what the household calls it works too`() =
        runTest {
            // ADR-0015. The whole point of the alias table, reached through the screen.
            val viewModel = viewModel()

            viewModel.onQueryChanged("pec deck")

            assertEquals(listOf("Butterfly"), viewModel.names())
        }

    @Test
    fun `a body-part chip narrows to that muscle group`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onBodyPartToggled(BodyPart.CHEST)

            assertEquals(listOf("Butterfly", "Barbell Bench Press"), viewModel.names())
        }

    @Test
    fun `an equipment chip narrows to that equipment`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEquipmentToggled(Equipment.CABLE)

            assertEquals(listOf("Lat Pulldown"), viewModel.names())
        }

    @Test
    fun `equipment the catalog never recorded is selectable in its own right`() =
        runTest {
            // ADR-0015: "not specified" is an answer you can filter for, not a hidden bucket.
            val viewModel = viewModel()

            viewModel.onEquipmentToggled(Equipment.UNSPECIFIED)

            assertEquals(listOf("Ab Roller"), viewModel.names())
        }

    @Test
    fun `chips and the query combine rather than replacing each other`() =
        runTest {
            // US-12: "Filters and the search query combine".
            val viewModel = viewModel()

            viewModel.onBodyPartToggled(BodyPart.CHEST)
            viewModel.onQueryChanged("bench")

            assertEquals(listOf("Barbell Bench Press"), viewModel.names())
        }

    @Test
    fun `tapping a lit chip turns it off again`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onBodyPartToggled(BodyPart.CHEST)
            viewModel.onBodyPartToggled(BodyPart.CHEST)

            assertEquals(4, viewModel.names().size, "back to the whole catalog")
        }

    @Test
    fun `two chips in one dimension widen rather than narrow`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onBodyPartToggled(BodyPart.CHEST)
            viewModel.onBodyPartToggled(BodyPart.BACK)

            assertEquals(listOf("Butterfly", "Barbell Bench Press", "Lat Pulldown"), viewModel.names())
        }

    @Test
    fun `clearing returns the full catalog, query and chips both`() =
        runTest {
            // US-12: "Clearing the filters returns the full catalog."
            val viewModel = viewModel()
            viewModel.onQueryChanged("bench")
            viewModel.onBodyPartToggled(BodyPart.CHEST)

            viewModel.onFiltersCleared()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(4, state.results.size)
                assertEquals("", state.query)
                assertTrue(state.filter.isEmpty)
                assertTrue(!state.isNarrowed)
            }
        }

    @Test
    fun `the screen knows when something is narrowing the list`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertTrue(!expectMostRecentItem().isNarrowed)

                viewModel.onQueryChanged("bench")
                assertTrue(expectMostRecentItem().isNarrowed, "a query counts")

                viewModel.onQueryChanged("")
                viewModel.onEquipmentToggled(Equipment.CABLE)
                assertTrue(expectMostRecentItem().isNarrowed, "so does a chip")
            }
        }

    @Test
    fun `a query matching nothing empties the list rather than falling back to everything`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onQueryChanged("zercher")

            assertEquals(emptyList(), viewModel.names())
        }

    @Test
    fun `the detail screen gets the exercise it asked for`() =
        runTest {
            assertEquals(butterfly, viewModel().exercise(butterfly.id).first())
        }

    @Test
    fun `an exercise that is not in the catalog resolves to nothing, not a crash`() =
        runTest {
            assertNull(viewModel().exercise(ExerciseId("never-existed")).first())
        }

    // ---- US-02a: several exercises in one visit --------------------------------------------

    @Test
    fun `nothing is marked as added before anything has been`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(emptyList(), expectMostRecentItem().addedThisVisit)
            }
        }

    @Test
    fun `adding to the session records what this visit added, in order`() =
        runTest {
            // The screen stays put across picks (US-02a), so it has to be able to say what it
            // has already done — otherwise the same row looks untouched on the second look.
            val viewModel = viewModel()

            viewModel.onAddedToSession(butterfly.id)
            viewModel.onAddedToSession(benchPress.id)

            viewModel.uiState.test {
                assertEquals(
                    listOf(butterfly.id, benchPress.id),
                    expectMostRecentItem().addedThisVisit,
                )
            }
        }

    @Test
    fun `the same exercise added twice is counted twice, not deduplicated`() =
        runTest {
            // US-02 allows an exercise to appear twice in a session, so the second tap is a
            // real action and the row says "Added 2×" rather than staying at "Added".
            val viewModel = viewModel()

            viewModel.onAddedToSession(butterfly.id)
            viewModel.onAddedToSession(butterfly.id)

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals(2, state.addedThisVisit.size)
                assertEquals(2, state.timesAdded(butterfly.id))
                assertEquals(0, state.timesAdded(benchPress.id))
            }
        }

    @Test
    fun `narrowing the catalog does not forget what was added`() =
        runTest {
            // Searching for the next exercise must not clear the markers on the previous ones.
            val viewModel = viewModel()
            viewModel.onAddedToSession(butterfly.id)

            viewModel.onQueryChanged("bench")
            viewModel.onEquipmentToggled(Equipment.BARBELL)

            viewModel.uiState.test {
                assertEquals(1, expectMostRecentItem().timesAdded(butterfly.id))
            }
        }

    @Test
    fun `clearing the filters does not forget what was added either`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAddedToSession(butterfly.id)
            viewModel.onQueryChanged("bench")

            viewModel.onFiltersCleared()

            viewModel.uiState.test {
                val state = expectMostRecentItem()
                assertEquals("", state.query)
                assertEquals(1, state.timesAdded(butterfly.id))
            }
        }

    private class FakeCurrentMember(
        private val id: UserId,
    ) : CurrentMember {
        override suspend fun id(): UserId = id
    }

    /**
     * Supplies the ranking only, exactly as the real catalog does. Narrowing is
     * `CatalogQuery`'s, run by the interface's own `browse`, so this fake cannot drift
     * from production behaviour by reimplementing matching.
     */
    private class FakeCatalog(
        private val all: List<Exercise>,
    ) : ExerciseCatalog {
        override fun observeRanked(forMember: UserId): Flow<List<Exercise>> = MutableStateFlow(all)
    }
}
