import { useState } from "react";
import { Coins as CoinsIcon, Minus, Plus } from "lucide-react";
import { errorMessage, saveRow, useSave } from "../data/useData";
import type { Snapshot } from "../data/types";
import {
  COIN_DENOMINATIONS,
  MAX_COIN_QUANTITY,
  coinQuantities,
  coinRowId,
  coinTotals,
  money,
  parseCoinQuantity,
} from "../domain";

export function Coins({ data, uid }: { data: Snapshot; uid: string }) {
  const saved = coinQuantities(data.coinCounts);
  // Borrador de texto por denominación; sin entrada se muestra lo guardado.
  const [draft, setDraft] = useState<Record<number, string>>({});
  const save = useSave(uid);
  const quantities: Record<number, number> = {};
  let invalid = false;
  let dirty = false;
  for (const d of COIN_DENOMINATIONS) {
    const raw = draft[d];
    const parsed = raw === undefined ? saved[d] : parseCoinQuantity(raw);
    if (parsed === null) invalid = true;
    quantities[d] = parsed ?? 0;
    if (parsed !== null && parsed !== saved[d]) dirty = true;
  }
  const { count, sum } = coinTotals(quantities);
  const step = (d: number, delta: number) =>
    setDraft((current) => ({
      ...current,
      [d]: String(
        Math.min(MAX_COIN_QUANTITY, Math.max(0, quantities[d] + delta)),
      ),
    }));
  function submit() {
    const now = new Date().toISOString();
    const rows = COIN_DENOMINATIONS.filter(
      (d) => quantities[d] !== saved[d],
    ).map((d) => ({
      id: coinRowId(uid, d),
      user_id: uid,
      denomination: d,
      quantity: quantities[d],
      updated_at: now,
    }));
    save.mutate(
      async () => {
        await Promise.all(rows.map((row) => saveRow("coin_counts", row)));
      },
      { onSuccess: () => setDraft({}) },
    );
  }
  return (
    <section className="coins" aria-labelledby="coins-title">
      <div className="section-heading">
        <h2 id="coins-title">
          Conteo de monedas <CoinsIcon size={18} />
        </h2>
      </div>
      <div className="coin-grid">
        {COIN_DENOMINATIONS.map((d) => (
          <div className="coin-row" key={d}>
            <strong>{money(d)}</strong>
            <div className="coin-stepper">
              <button
                type="button"
                className="icon-button"
                aria-label={`Quitar una moneda de ${money(d)}`}
                disabled={save.isPending || quantities[d] <= 0}
                onClick={() => step(d, -1)}
              >
                <Minus size={17} />
              </button>
              <input
                type="number"
                inputMode="numeric"
                min={0}
                max={MAX_COIN_QUANTITY}
                step={1}
                aria-label={`Cantidad de monedas de ${money(d)}`}
                disabled={save.isPending}
                value={draft[d] ?? String(saved[d])}
                onChange={(event) =>
                  setDraft((current) => ({
                    ...current,
                    [d]: event.target.value,
                  }))
                }
              />
              <button
                type="button"
                className="icon-button"
                aria-label={`Agregar una moneda de ${money(d)}`}
                disabled={save.isPending || quantities[d] >= MAX_COIN_QUANTITY}
                onClick={() => step(d, 1)}
              >
                <Plus size={17} />
              </button>
            </div>
            <small>{money(quantities[d] * d)}</small>
          </div>
        ))}
      </div>
      <div className="coin-summary" aria-live="polite">
        <div>
          <span>Total de monedas</span>
          <strong>{count}</strong>
        </div>
        <div>
          <span>Suma total</span>
          <strong>{money(sum)}</strong>
        </div>
        <button
          className="primary"
          disabled={!dirty || invalid || save.isPending}
          onClick={submit}
        >
          {save.isPending ? "Guardando…" : "Guardar conteo"}
        </button>
      </div>
      {(invalid || save.error) && (
        <p className="error" role="alert">
          {invalid
            ? "Usa solo números enteros de 0 en adelante."
            : errorMessage(save.error)}
        </p>
      )}
    </section>
  );
}
