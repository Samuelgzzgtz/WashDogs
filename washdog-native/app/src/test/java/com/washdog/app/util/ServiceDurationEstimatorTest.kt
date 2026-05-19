package com.washdog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceDurationEstimatorTest {
    @Test fun `Jumbo returns 360`() = assertEquals(360, ServiceDurationEstimator.suggest("Baño Perro Jumbo"))
    @Test fun `Grande returns 240`() = assertEquals(240, ServiceDurationEstimator.suggest("Baño Perro Grande"))
    @Test fun `Mediano returns 120`() = assertEquals(120, ServiceDurationEstimator.suggest("Baño Perro Mediano"))
    @Test fun `Pequeño returns 90`() = assertEquals(90, ServiceDurationEstimator.suggest("Baño Perro Pequeño"))
    @Test fun `Gato returns 90`() = assertEquals(90, ServiceDurationEstimator.suggest("Baño Gato Pelo Corto"))
    @Test fun `unknown service returns 60`() = assertEquals(60, ServiceDurationEstimator.suggest("Limpieza de Oídos"))
    @Test fun `case insensitive`() = assertEquals(360, ServiceDurationEstimator.suggest("baño perro jumbo"))
    @Test fun `pequeno without accent matches`() = assertEquals(90, ServiceDurationEstimator.suggest("Baño Perro Pequeño Toy"))
}
