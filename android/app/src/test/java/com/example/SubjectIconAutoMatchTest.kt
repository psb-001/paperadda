package com.example

import com.example.ui.components.SubjectIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the subject-name -> icon mapping so it cannot silently regress.
 * The admin portal's auto-match in `admin-portal/js/icons.js` must return the
 * same ids for the same names.
 */
class SubjectIconAutoMatchTest {

    private fun check(subject: String, expected: String) {
        assertEquals(
            "auto-match for \"$subject\"",
            expected,
            SubjectIcons.autoFor(subject)
        )
    }

    @Test
    fun mathsAndCoreEngineering() {
        // Every maths subject auto-matches to the calculator; a more specific
        // icon (e.g. "functions" for Mathematics II) is set explicitly in the
        // portal, and an explicit icon always wins over the guess.
        check("Engineering mathematics I", "calculate")
        check("Engineering mathematics II", "calculate")
        check("Discrete Mathematical Structures", "calculate")
        check("Applied Mathematics", "calculate")
        check("Engineering Physics", "science")
        check("Engineering Chemistry", "biotech")
        check("BCME (Basic Civil and Mechanical Engineering)", "construction")
        check("Engineering Graphics", "straighten")
        check("BEEE (Basic Electrical and Electronics Engineering)", "electrical_services")
    }

    @Test
    fun electronicsAndCommunication() {
        check("Power Systems Engineering", "electric_bolt")
        check("Digital Signal Processing", "cell_tower")
        check("Microprocessor Engineering", "memory")
        check("VLSI Design", "memory")
        check("Digital Electronics", "memory")
        check("Computer Networks", "router")
        check("Thermodynamics", "thermostat")
    }

    @Test
    fun computing() {
        check("Data Structures & Algorithms", "data_object")
        check("PPS (Programming for Problem Solving)", "developer_mode")
        check("Object Oriented Programming", "developer_mode")
        check("Software Engineering", "developer_mode")
        check("Operating Systems", "computer")
        check("DBMS", "storage")
        check("Cyber Security", "security")
        check("Mobile App Development", "phone_android")
        check("Web Technology", "code")
    }

    @Test
    fun managementHumanitiesAndSkills() {
        check("Communication Skills", "record_voice_over")
        check("Business Communication", "record_voice_over")
        check("Engineering Management", "business")
        check("Engineering Economics", "account_balance")
        check("Humanities", "history")
        check("Environmental Studies", "forest")
        check("Control Systems", "settings")
        check("Machine Learning", "smart_toy")
    }

    @Test
    fun unknownSubjectFallsBackToBook() {
        check("Some Entirely New Subject XYZ", "menu_book")
        check("", "menu_book")
    }

    @Test
    fun everyRuleTargetIsARealIcon() {
        // A rule pointing at an id the catalogue does not define would render
        // as the generic fallback and hide the mistake.
        val ids = SubjectIcons.ids.toSet()
        val probes = listOf(
            "Data Structures", "Operating Systems", "Networking", "Microprocessor",
            "Machine Learning", "Digital Signal Processing", "Communication Skills",
            "Civil Engineering", "Architecture", "Mathematics", "Control Systems",
            "Engineering Mechanics", "Statistics", "Physics", "Chemistry", "Biology",
            "Geology", "Thermodynamics", "Waves", "Kinematics", "Programming",
            "Web Technology", "Database", "Networking", "Security", "Cloud",
            "Operating Systems", "Linux", "Economics", "Management", "Marketing",
            "Law", "English", "History", "Psychology", "Graphics", "UI Design",
            "Android", "Environment", "Safety", "Physics Lab", "Workshop", "Project"
        )
        for (probe in probes) {
            val id = SubjectIcons.autoFor(probe)
            assertTrue("\"$probe\" -> $id is not a known icon", ids.contains(id))
        }
    }

    @Test
    fun legacyDatabaseIconsStillResolve() {
        // Ids already stored in public.subjects.icon_name must keep working.
        for (legacy in listOf("graphic_eq", "smart_toy", "memory", "cell_tower",
            "computer", "language")) {
            assertTrue("$legacy should resolve", SubjectIcons.isKnown(legacy))
        }
    }

    @Test
    fun unknownStoredIconFallsBackToNameMatch() {
        // A hand-edited or removed icon must not blank the row.
        assertTrue(SubjectIcons.isKnown("not_a_real_icon").not())
        val icon = SubjectIcons.resolve("not_a_real_icon", "Engineering mathematics I")
        assertEquals(SubjectIcons.resolve("calculate", "x"), icon)
    }
}
