import type { Client } from "./data/types";
import { normalized } from "./domain";

export type ParsedClient = {
  owner_name?: string;
  pet_name?: string;
  breed?: string;
  phone?: string;
  pet_type?: Client["pet_type"];
};

type Field = "owner_name" | "pet_name" | "breed" | "phone" | "pet_type";

const WORD = "[\\p{L}][\\p{L}'’\\-]*";
const PHONE_CANDIDATE = /\+?\d[\d\s().\-]{8,}\d/g;

/** Devuelve 10 dígitos para números de México, "+código…" para otros, o null. */
export function normalizePhone(raw: string): string | null {
  const international = raw.trim().startsWith("+");
  let digits = raw.replace(/\D/g, "");
  if (digits.length === 13 && /^(521|044|045)/.test(digits))
    digits = digits.slice(3);
  else if (digits.length === 12 && digits.startsWith("52"))
    digits = digits.slice(2);
  else if (digits.length === 12 && /^(044|045)/.test(digits))
    digits = digits.slice(3);
  if (digits.length === 10) return digits;
  if (international && digits.length >= 11 && digits.length <= 15)
    return `+${digits}`;
  return null;
}

function findPhone(text: string): string | null {
  for (const match of text.matchAll(PHONE_CANDIDATE)) {
    const phone = normalizePhone(match[0]);
    if (phone) return phone;
  }
  return null;
}

function tidyName(value: string): string {
  const text = value
    .replace(/[*_~]/g, "")
    .replace(/\s+/g, " ")
    .replace(/^[\s,.;:\-–—]+|[\s,.;:\-–—]+$/g, "");
  if (text && (text === text.toLowerCase() || text === text.toUpperCase()))
    return text.toLowerCase().replace(/(^|[\s'’\-])(\p{L})/gu, (_, a, b) => a + b.toUpperCase());
  return text;
}

function petTypeIn(text: string): Client["pet_type"] | undefined {
  const t = normalized(text);
  if (/\b(perro|perra|perrito|perrita|cachorro|cachorra|lomito)\b/.test(t))
    return "DOG";
  if (/\b(gato|gata|gatito|gatita|michi|felino)\b/.test(t)) return "CAT";
  return undefined;
}

function classify(label: string): Field | null {
  const l = normalized(label)
    .replace(/[^a-z0-9 ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  if (!l) return null;
  if (/\braza\b/.test(l)) return "breed";
  if (/\b(tipo|especie)\b/.test(l)) return "pet_type";
  if (
    /\b(mascota|perro|perra|perrito|perrita|gato|gata|gatito|gatita|peludo|peludito|animalito|animal|cachorro|cachorra)\b/.test(
      l,
    )
  )
    return "pet_name";
  if (
    /\b(telefono|tel|celular|cel|whatsapp|whats|wa|numero|num|movil|phone)\b/.test(
      l,
    )
  )
    return "phone";
  if (
    /\b(nombre|dueno|duena|propietario|propietaria|cliente|titular|tutor|responsable|contacto)\b/.test(
      l,
    )
  )
    return "owner_name";
  return null;
}

/** Quita el encabezado de un chat exportado de WhatsApp, viñetas y negritas. */
function cleanLine(line: string): string {
  return line
    .replace(/^\s*\[[^\]]*\d[^\]]*\]\s*[^:]{1,40}:\s+/, "")
    .replace(
      /^\s*\d{1,2}\/\d{1,2}\/\d{2,4},?\s+\d{1,2}:\d{2}(?:\s?[ap]\.?\s?m\.?)?\s+-\s+[^:]{1,40}:\s+/i,
      "",
    )
    .replace(/^\s*(?:[-•·*>]+|\d{1,2}[.)])\s+/, "")
    .replace(/[*~]/g, "")
    .replace(/^_+|_+$/g, "")
    .trim();
}

/**
 * Extrae los datos de un cliente de un mensaje de WhatsApp pegado.
 * Entiende "Etiqueta: valor", mensajes en texto libre ("se llama Luna") y,
 * como último recurso, una lista simple de líneas (dueño, mascota, raza).
 * Solo devuelve los campos que pudo encontrar.
 */
export function parseClientMessage(message: string): ParsedClient {
  const lines = message
    .split(/\r?\n/)
    .map(cleanLine)
    .filter((line) => line.length > 0);
  const result: ParsedClient = {};
  let labelled = 0;

  for (let i = 0; i < lines.length; i++) {
    const match = lines[i].match(/^(.{1,45}?)\s*[:：]\s*(.*)$/);
    if (!match) continue;
    const field = classify(match[1]);
    if (!field) continue;
    let value = match[2].trim();
    if (!value && i + 1 < lines.length && !/[:：]/.test(lines[i + 1]))
      value = lines[++i];
    if (!value) continue;
    labelled++;
    if (field === "phone") {
      const phone = findPhone(value) ?? normalizePhone(value);
      if (phone && !result.phone) result.phone = phone;
    } else if (field === "pet_type") {
      const type = petTypeIn(value);
      if (type && !result.pet_type) result.pet_type = type;
    } else if (!result[field]) {
      result[field] = tidyName(value);
    }
  }

  const text = lines.join("\n");
  if (!result.phone) {
    const phone = findPhone(text);
    if (phone) result.phone = phone;
  }

  const free = (pattern: RegExp) => text.match(pattern)?.[1];
  if (!result.pet_name) {
    const name =
      free(new RegExp(`\\b(?:se llama|se llamaba)\\s+(${WORD})`, "iu")) ??
      free(
        new RegExp(
          `\\bmi\\s+(?:perr[oa]|perrit[oa]|gat[oa]|gatit[oa]|mascota)\\s+(?!es\\b|se\\b|tiene\\b|esta\\b)(${WORD})`,
          "iu",
        ),
      );
    if (name) result.pet_name = tidyName(name);
  }
  if (!result.owner_name) {
    const name = free(
      new RegExp(
        `\\b(?:soy|me llamo|mi nombre es)\\s+(${WORD}(?:\\s+(?!y\\b|e\\b|mi\\b|quiero\\b|tengo\\b|con\\b|de\\s+mi\\b)${WORD}){0,3})`,
        "iu",
      ),
    );
    if (name) result.owner_name = tidyName(name);
  }
  if (!result.breed) {
    const breed = free(
      new RegExp(`\\braza\\s+(?:es\\s+)?(${WORD}(?:\\s+${WORD})?)`, "iu"),
    );
    if (breed) result.breed = tidyName(breed);
  }

  if (!result.pet_type) result.pet_type = petTypeIn(text);

  // Sin etiquetas ni frases: una lista de líneas sueltas, en orden dueño / mascota / raza.
  if (!labelled && !result.owner_name && !result.pet_name) {
    const plain = lines.filter(
      (line) =>
        line.length <= 40 &&
        !/[:：]/.test(line) &&
        !findPhone(line) &&
        !/^\+?[\d\s().\-]+$/.test(line),
    );
    if (plain.length >= 2 && plain.length <= 3) {
      result.owner_name = tidyName(plain[0]);
      result.pet_name = tidyName(plain[1]);
      if (plain[2] && !result.breed) result.breed = tidyName(plain[2]);
    }
  }

  for (const key of Object.keys(result) as Field[])
    if (!result[key]) delete result[key];
  return result;
}
