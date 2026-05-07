package com.washdog.app.data

data class WDService(val name: String, val price: Double) {
    val label: String get() = "$name  ·  \$${"%.0f".format(price)}"
}

val WD_SERVICES: List<WDService> = listOf(
    WDService("Baño Perro Pequeño",          200.0),
    WDService("Baño Perro Mediano",          250.0),
    WDService("Baño Perro Grande",           300.0),
    WDService("Baño Perro Jumbo",            375.0),
    WDService("Baño Gato Pelo Corto",        250.0),
    WDService("Baño Gato Pelo Largo",        300.0),
    WDService("Corte de Uñas",                50.0),
    WDService("Detalles Mínimos c/Tijera",    50.0),
    WDService("Corte Higiénico",              50.0),
    WDService("Limpieza de Oídos",            35.0),
    WDService("Deslanado",                   120.0),
    WDService("Estética Canina Básica",      150.0),
    WDService("Rapa Completa",               120.0),
    WDService("Acondicionador",               50.0),
    WDService("Domicilio · Sector Granada",   30.0),
    WDService("Domicilio · Mitras Poniente",  50.0),
    WDService("Domicilio · Alrededores",      60.0),
)
