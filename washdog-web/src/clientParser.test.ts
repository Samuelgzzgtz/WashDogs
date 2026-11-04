import { describe, expect, it } from "vitest";
import { normalizePhone, parseClientMessage } from "./clientParser";

describe("normalizePhone", () => {
  it("deja 10 dígitos de México sin lada del país", () => {
    expect(normalizePhone("81 1234 5678")).toBe("8112345678");
    expect(normalizePhone("(81) 1234-5678")).toBe("8112345678");
    expect(normalizePhone("+52 1 81 1234 5678")).toBe("8112345678");
    expect(normalizePhone("+52 81 1234 5678")).toBe("8112345678");
    expect(normalizePhone("044 81 1234 5678")).toBe("8112345678");
  });
  it("conserva números internacionales y rechaza basura", () => {
    expect(normalizePhone("+1 305 555 0100")).toBe("+13055550100");
    expect(normalizePhone("12345")).toBeNull();
    expect(normalizePhone("2026-11-03")).toBeNull();
  });
});

describe("parseClientMessage", () => {
  it("lee etiquetas con negritas de WhatsApp", () => {
    expect(
      parseClientMessage(
        "*Nombre:* María López\n*Teléfono:* 81 1234 5678\n*Mascota:* Luna\n*Raza:* Poodle",
      ),
    ).toEqual({
      owner_name: "María López",
      phone: "8112345678",
      pet_name: "Luna",
      breed: "Poodle",
      pet_type: undefined,
    });
  });

  it("distingue el nombre del dueño del de la mascota", () => {
    const parsed = parseClientMessage(
      "Nombre del cliente: Ana Torres\nNombre de la perrita: Lola\nCelular: 8187654321",
    );
    expect(parsed.owner_name).toBe("Ana Torres");
    expect(parsed.pet_name).toBe("Lola");
    expect(parsed.phone).toBe("8187654321");
    expect(parsed.pet_type).toBe("DOG");
  });

  it("detecta gato por la etiqueta de tipo", () => {
    expect(
      parseClientMessage("Dueño: Pedro\nMascota: Michi\nTipo: Gato").pet_type,
    ).toBe("CAT");
  });

  it("acepta listas numeradas y el valor en la línea siguiente", () => {
    const parsed = parseClientMessage(
      "1. Nombre:\nJuan Pérez\n2) Teléfono:\n+52 1 81 1111 2222\n3. Perrito: Rocky",
    );
    expect(parsed).toMatchObject({
      owner_name: "Juan Pérez",
      phone: "8111112222",
      pet_name: "Rocky",
    });
  });

  it("ignora el encabezado de un chat exportado", () => {
    const parsed = parseClientMessage(
      "[18:30, 3/11/2026] Carlos: Nombre: Carlos Ruiz\n[18:31, 3/11/2026] Carlos: Mascota: Toby\n[18:31, 3/11/2026] Carlos: Tel: 81 2222 3333",
    );
    expect(parsed).toMatchObject({
      owner_name: "Carlos Ruiz",
      pet_name: "Toby",
      phone: "8122223333",
    });
  });

  it("entiende texto libre", () => {
    const parsed = parseClientMessage(
      "Hola, soy Laura Gómez y mi perrito se llama Bruno, raza schnauzer. Mi cel es 8199998888",
    );
    expect(parsed).toMatchObject({
      owner_name: "Laura Gómez",
      pet_name: "Bruno",
      breed: "Schnauzer",
      phone: "8199998888",
      pet_type: "DOG",
    });
  });

  it("usa una lista de líneas sueltas como dueño, mascota y raza", () => {
    expect(
      parseClientMessage("sofia ramirez\nnala\nlabrador\n81 5555 6666"),
    ).toMatchObject({
      owner_name: "Sofia Ramirez",
      pet_name: "Nala",
      breed: "Labrador",
      phone: "8155556666",
    });
  });

  it("encuentra solo el teléfono si es lo único que hay", () => {
    expect(parseClientMessage("+52 1 81 4444 0000")).toEqual({
      phone: "8144440000",
    });
  });

  it("no inventa datos de un texto sin información", () => {
    expect(parseClientMessage("Gracias, nos vemos el martes a las 5")).toEqual(
      {},
    );
    expect(parseClientMessage("")).toEqual({});
  });
});
