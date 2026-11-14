import { describe, expect, it } from "vitest";
import { waRecipient } from "./whatsapp";

describe("waRecipient", () => {
  it("quita el 1 de los celulares de México", () => {
    expect(waRecipient("5218123547765")).toBe("528123547765");
    expect(waRecipient("528123547765")).toBe("528123547765");
    expect(waRecipient("15551879304")).toBe("15551879304");
  });
});
