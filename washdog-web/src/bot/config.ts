/**
 * Configuración del bot de WhatsApp. Se guarda como JSON en `bot_config.config`
 * y se edita desde la pantalla "Bot" de la app. Los valores por defecto salen de
 * la lista de precios de noviembre 2026 y de las duraciones que nos pasó Washdog.
 */

export const sizes = ["PEQUENO", "MEDIANO", "GRANDE", "JUMBO"] as const;
export type Size = (typeof sizes)[number];
export const petKinds = [...sizes, "GATO_CORTO", "GATO_LARGO"] as const;
export type PetKind = (typeof petKinds)[number];

export const petKindLabel: Record<PetKind, string> = {
  PEQUENO: "Perro pequeño",
  MEDIANO: "Perro mediano",
  GRANDE: "Perro grande",
  JUMBO: "Perro jumbo",
  GATO_CORTO: "Gato pelo corto",
  GATO_LARGO: "Gato pelo largo",
};
export const sizeLabel: Record<Size, string> = {
  PEQUENO: "Pequeño",
  MEDIANO: "Mediano",
  GRANDE: "Grande",
  JUMBO: "Jumbo",
};

/** Un precio con su duración. `from` = el precio es "desde" (se confirma al ver al perro). */
export type PriceTime = { price: number; minutes: number; from?: boolean };

/** Servicio extra con el mismo precio para todos (uñas, oídos…). */
export type FlatExtra = { id: string; name: string } & PriceTime;
/** Servicio extra que cambia según la talla (deslanado, cepillado profundo). */
export type SizedExtra = {
  id: string;
  name: string;
  bySize: Record<Size, PriceTime>;
};

export type ZoneMode = "NORMAL" | "ALERT";
export type Zone = {
  id: string;
  name: string;
  /** Palabras que, si aparecen en la dirección, ubican al cliente en esta zona. */
  keywords: string[];
  /** Cargo por servicio a domicilio. */
  fee: number;
  feeFrom?: boolean;
  /** Minutos aproximados de traslado desde la base (Mitras Poniente, Sector Granada). */
  travelMinutes: number;
  /** ALERT: el bot no ofrece horarios; contesta "déjame checar" y avisa en la app. */
  mode: ZoneMode;
};

export type BotTexts = {
  /** Nombre con el que el bot se presenta. */
  assistantName: string;
  greeting: string;
  bathIncludes: string;
  sameDay: string;
  bookingRequested: string;
  outOfZone: string;
  needsReview: string;
  unknown: string;
};

export type BotConfig = {
  version: 1;
  /** Si está apagado, el bot no contesta y solo deja avisos. */
  enabled: boolean;
  baths: Record<PetKind, PriceTime>;
  extras: FlatExtra[];
  sizedExtras: SizedExtra[];
  zones: Zone[];
  /** Zona que se usa cuando la dirección no coincide con ninguna. */
  defaultZoneId: string;
  rules: {
    /** Minutos de colchón entre terminar un servicio y empezar el siguiente, además del traslado. */
    marginMinutes: number;
    /** Traslado entre dos clientes de la misma zona. */
    sameZoneTravelMinutes: number;
    /** Hora máxima para terminar el último servicio del día ("21:30"). */
    latestFinish: string;
    /** Desde cuántos perros grandes/jumbo en una misma cita se pide revisión humana. */
    reviewLargeDogs: number;
  };
  texts: BotTexts;
};

const pt = (price: number, minutes: number, from = false): PriceTime =>
  from ? { price, minutes, from } : { price, minutes };

export const defaultBotConfig = (): BotConfig => ({
  version: 1,
  enabled: true,
  baths: {
    PEQUENO: pt(220, 60),
    MEDIANO: pt(250, 90),
    GRANDE: pt(320, 120),
    JUMBO: pt(390, 120),
    GATO_CORTO: pt(250, 60),
    GATO_LARGO: pt(300, 90),
  },
  extras: [
    { id: "unas", name: "Corte de uñas", ...pt(50, 0) },
    { id: "oidos", name: "Limpieza de oídos", ...pt(50, 0) },
    {
      id: "tijera",
      name: "Detalles con tijera (redondeo de patas y despeje de mirada)",
      ...pt(50, 15),
    },
    { id: "higienico", name: "Corte higiénico", ...pt(50, 15) },
    { id: "estetica", name: "Estética canina básica (con máquina)", ...pt(150, 30) },
    { id: "rapa", name: "Rapa completa de pelaje", ...pt(150, 30) },
    { id: "acondicionador", name: "Acondicionador", ...pt(50, 0) },
  ],
  sizedExtras: [
    {
      id: "deslanado",
      name: "Deslanado",
      bySize: {
        PEQUENO: pt(80, 30),
        MEDIANO: pt(80, 60),
        GRANDE: pt(150, 60),
        JUMBO: pt(180, 60, true),
      },
    },
    {
      id: "cepillado",
      name: "Cepillado profundo (para salvar el manto)",
      bySize: {
        PEQUENO: pt(80, 30),
        MEDIANO: pt(80, 60),
        GRANDE: pt(150, 60),
        JUMBO: pt(200, 60, true),
      },
    },
  ],
  zones: [
    {
      id: "granada",
      name: "Sector Granada",
      keywords: ["sector granada", "granada"],
      fee: 0,
      travelMinutes: 5,
      mode: "NORMAL",
    },
    {
      id: "mitras",
      name: "Mitras Poniente",
      keywords: ["mitras poniente", "mitras", "sector toledo", "sector jerez"],
      fee: 50,
      travelMinutes: 15,
      mode: "NORMAL",
    },
    {
      id: "cerca",
      name: "Alrededores cerca (Las Lomas, Cumbres, Solidaridad)",
      keywords: ["las lomas", "lomas", "cumbres", "solidaridad"],
      fee: 80,
      feeFrom: true,
      travelMinutes: 25,
      mode: "NORMAL",
    },
    {
      id: "monterrey",
      name: "Resto de Monterrey (incluye San Jerónimo)",
      keywords: ["san jeronimo", "san jerónimo", "monterrey", "centro"],
      fee: 80,
      feeFrom: true,
      travelMinutes: 35,
      mode: "NORMAL",
    },
    {
      id: "fuera",
      name: "Fuera de zona (Escobedo, Apodaca, Guadalupe, San Nicolás, San Pedro, Santa Catarina, García)",
      keywords: [
        "escobedo",
        "apodaca",
        "guadalupe",
        "san nicolas",
        "san nicolás",
        "san pedro",
        "santa catarina",
        "juarez",
        "juárez",
        "garcia",
        "garcía",
      ],
      fee: 0,
      travelMinutes: 50,
      mode: "ALERT",
    },
  ],
  defaultZoneId: "monterrey",
  rules: {
    marginMinutes: 10,
    sameZoneTravelMinutes: 10,
    latestFinish: "21:30",
    reviewLargeDogs: 2,
  },
  texts: {
    assistantName: "Washdog",
    greeting:
      "¡Hola! 🐶 Soy el asistente de Washdog, baño a tu perrito a domicilio. ¿En qué te puedo ayudar? Te puedo pasar precios, horarios disponibles o info del servicio.",
    bathIncludes:
      "El baño básico incluye: baño con dos tipos de shampoo (uno de limpieza y otro para su pelaje con aroma a avena), cepillado si es de pelo largo, corte de uñas (si se deja), limpieza de oídos y perfume.",
    sameDay:
      "Déjame checo si se puede hoy y en un momento te confirmo 🙌",
    bookingRequested:
      "¡Perfecto! Ya pasé tu solicitud y en un momento te confirmamos el horario 🐾",
    outOfZone:
      "Déjame checar si llegamos hasta tu zona y en un momento te confirmo 🙌",
    needsReview:
      "¿Me podrías mandar una foto de cómo está su pelaje? Así te digo cuánto tiempo y costo sería, y te confirmo el horario.",
    unknown:
      "Déjame consultarlo y en un momento te respondo 🙌",
  },
});

/** Completa una configuración guardada con los valores por defecto que le falten. */
export function withDefaults(saved: unknown): BotConfig {
  const base = defaultBotConfig();
  if (!saved || typeof saved !== "object") return base;
  const value = saved as Partial<BotConfig>;
  return {
    ...base,
    ...value,
    version: 1,
    baths: { ...base.baths, ...value.baths },
    extras: Array.isArray(value.extras) ? value.extras : base.extras,
    sizedExtras: Array.isArray(value.sizedExtras)
      ? value.sizedExtras
      : base.sizedExtras,
    zones: Array.isArray(value.zones) && value.zones.length ? value.zones : base.zones,
    rules: { ...base.rules, ...value.rules },
    texts: { ...base.texts, ...value.texts },
  };
}
