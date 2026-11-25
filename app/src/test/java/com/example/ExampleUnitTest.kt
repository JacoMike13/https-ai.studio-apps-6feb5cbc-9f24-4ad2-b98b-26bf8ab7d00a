package com.example

import com.example.data.Audiencia
import com.example.ui.viewmodel.filterAudiencias
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    private val sampleAudiencias = listOf(
        Audiencia(
            id = "1",
            nuc = "NUC-2026-00142",
            numeroCausa = "CP-104/2026",
            tipoAudiencia = "Control de detención",
            juez = "Juez Martín López",
            ministerioPublico = "Lic. Sofía García",
            asesorJuridico = "Lic. Raúl Méndez",
            victima = "Víctima Reservada A",
            defensor = "Lic. Elena Ruiz",
            imputado = "Carlos Ramírez"
        ),
        Audiencia(
            id = "2",
            nuc = "NUC-2026-00888",
            numeroCausa = "CP-215/2026",
            tipoAudiencia = "Juicio oral",
            juez = "Jueza Valeria Torres",
            ministerioPublico = "Lic. Jorge Navarro",
            asesorJuridico = "Lic. Patricia Solís",
            victima = "Víctima B",
            defensor = "Lic. Marcos Pineda",
            imputado = "Miguel Herrera"
        )
    )

    @Test
    fun `filterAudiencias matches NUC and Causa Penal`() {
        val byNuc = filterAudiencias(sampleAudiencias, null, null, "00142")
        assertEquals(listOf("1"), byNuc.map { it.id })

        val byCausa = filterAudiencias(sampleAudiencias, null, null, "CP-215")
        assertEquals(listOf("2"), byCausa.map { it.id })
    }

    @Test
    fun `filterAudiencias matches all party names case and accent insensitively`() {
        assertEquals(listOf("1"), filterAudiencias(sampleAudiencias, null, null, "sofia garcia").map { it.id })
        assertEquals(listOf("1"), filterAudiencias(sampleAudiencias, null, null, "martin lopez").map { it.id })
        assertEquals(listOf("1"), filterAudiencias(sampleAudiencias, null, null, "carlos ramirez").map { it.id })
        assertEquals(listOf("2"), filterAudiencias(sampleAudiencias, null, null, "marcos pineda").map { it.id })
        assertEquals(listOf("2"), filterAudiencias(sampleAudiencias, null, null, "patricia solis").map { it.id })
        assertEquals(listOf("1"), filterAudiencias(sampleAudiencias, null, null, "reservada a").map { it.id })
    }
}

