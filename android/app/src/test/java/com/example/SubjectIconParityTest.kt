package com.example

import com.example.ui.components.SubjectIcons
import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectIconParityTest {
    @Test
    fun appMatchesPortalExpectations() {
        val expected = mapOf(
            "Engineering mathematics I" to "calculate",
            "Engineering mathematics II" to "calculate",
            "Data Structures & Algorithms" to "data_object",
            "Discrete Mathematical Structures" to "calculate",
            "Computer Networks" to "router",
            "Microprocessor Engineering" to "memory",
            "VLSI Design" to "memory",
            "Power Systems Engineering" to "electric_bolt",
            "Digital Signal Processing" to "cell_tower",
            "Thermodynamics" to "thermostat",
            "Operating Systems" to "computer",
            "DBMS" to "storage",
            "Digital Electronics" to "memory",
            "Environmental Studies" to "forest",
            "Machine Learning" to "smart_toy",
            "Engineering Physics" to "science",
            "Humanities" to "history",
            "Control Systems" to "settings",
            "Software Engineering" to "developer_mode",
            "Business Communication" to "record_voice_over",
            "Communication Skills" to "record_voice_over",
            "BCME (Basic Civil and Mechanical Engineering)" to "construction",
            "BEEE (Basic Electrical and Electronics Engineering)" to "electrical_services",
            "PPS (Programming for Problem Solving)" to "developer_mode",
            "Engineering Chemistry" to "biotech",
            "Engineering Mechanics" to "settings",
            "Object Oriented Programming" to "developer_mode",
            "Mobile App Development" to "phone_android",
            "Cyber Security" to "security",
            "Engineering Graphics" to "straighten",
            "Some Random New Subject XYZ" to "menu_book"
        )
        for ((subject, id) in expected) {
            assertEquals("app auto-match for \"$subject\"", id, SubjectIcons.autoFor(subject))
        }
    }
}
