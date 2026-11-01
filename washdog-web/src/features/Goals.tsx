import { useState } from "react";
import { PiggyBank, Plus, Pencil, Trash2 } from "lucide-react";
import { Empty, Field, Modal, MoneyInput } from "../components";
import { deleteRow, errorMessage, saveRow, useSave } from "../data/useData";
import type { Goal, Snapshot } from "../data/types";
import {
  cashBalance,
  goalContributionTransaction,
  goalPercent,
  goalRemaining,
  money,
  isoDate,
  readableDate,
  roundMoney,
  validateContribution,
  validateGoal,
} from "../domain";

type Dialog =
  { kind: "form"; goal?: Goal } | { kind: "add" | "delete"; goal: Goal };

export function Goals({ data, uid }: { data: Snapshot; uid: string }) {
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const goals = [...data.goals].sort((a, b) => a.name.localeCompare(b.name));
  const close = () => setDialog(null);
  return (
    <section className="goals" aria-labelledby="goals-title">
      <div className="section-heading">
        <h2 id="goals-title">
          Metas <span className="count">{goals.length}</span>
        </h2>
        <button
          className="text-button"
          onClick={() => setDialog({ kind: "form" })}
        >
          <Plus size={17} />
          Nueva meta
        </button>
      </div>
      {!goals.length ? (
        <Empty>
          <PiggyBank size={30} />
          <p>Aún no tienes metas. Crea una para controlar tus gastos.</p>
          <button
            className="text-button"
            onClick={() => setDialog({ kind: "form" })}
          >
            Crear mi primera meta
          </button>
        </Empty>
      ) : (
        <div className="goal-list">
          {goals.map((goal) => {
            const percent = goalPercent(
              goal.current_amount,
              goal.target_amount,
            );
            const remaining = goalRemaining(
              goal.current_amount,
              goal.target_amount,
            );
            return (
              <article className="goal-card" key={goal.id}>
                <div className="goal-head">
                  <div>
                    <h3>{goal.name}</h3>
                    {goal.deadline && (
                      <small>Fecha límite: {readableDate(goal.deadline)}</small>
                    )}
                  </div>
                  <strong>{percent}%</strong>
                </div>
                <div
                  className="goal-bar"
                  role="progressbar"
                  aria-label={`Progreso de ${goal.name}`}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={percent}
                >
                  <span style={{ width: `${percent}%` }} />
                </div>
                <p className="goal-amounts">
                  Llevas {money(goal.current_amount)} de{" "}
                  {money(goal.target_amount)}
                </p>
                <p className="goal-remaining">
                  {remaining > 0
                    ? `Faltan ${money(remaining)}`
                    : "¡Meta cumplida!"}
                </p>
                <div className="goal-actions">
                  <button
                    className="secondary"
                    onClick={() => setDialog({ kind: "add", goal })}
                  >
                    <Plus size={17} />
                    Agregar $
                  </button>
                  <button
                    className="icon-button"
                    aria-label={`Editar ${goal.name}`}
                    onClick={() => setDialog({ kind: "form", goal })}
                  >
                    <Pencil size={17} />
                  </button>
                  <button
                    className="icon-button danger"
                    aria-label={`Eliminar ${goal.name}`}
                    onClick={() => setDialog({ kind: "delete", goal })}
                  >
                    <Trash2 size={17} />
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
      {dialog?.kind === "form" && (
        <GoalForm uid={uid} goal={dialog.goal} close={close} />
      )}
      {dialog?.kind === "add" && (
        <ContributionForm
          uid={uid}
          goal={dialog.goal}
          data={data}
          close={close}
        />
      )}
      {dialog?.kind === "delete" && (
        <DeleteGoal uid={uid} goal={dialog.goal} close={close} />
      )}
    </section>
  );
}

function GoalForm({
  uid,
  goal,
  close,
}: {
  uid: string;
  goal?: Goal;
  close: () => void;
}) {
  const [id] = useState(() => goal?.id ?? crypto.randomUUID());
  const [problem, setProblem] = useState<string | null>(null);
  const save = useSave(uid);
  return (
    <Modal
      title={goal ? "Editar meta" : "Nueva meta"}
      close={close}
      busy={save.isPending}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          const name = String(form.get("name")).trim();
          const target = roundMoney(Number(form.get("target")));
          const current = roundMoney(Number(form.get("current") || 0));
          const invalid = validateGoal({ name, target, current });
          setProblem(invalid);
          if (invalid) return;
          const row: Goal = {
            id,
            user_id: uid,
            name,
            target_amount: target,
            current_amount: current,
            deadline: String(form.get("deadline")) || null,
            updated_at: new Date().toISOString(),
          };
          save.mutate(() => saveRow("goals", row), { onSuccess: close });
        }}
      >
        <fieldset disabled={save.isPending}>
          <Field label="Nombre de la meta">
            <input
              name="name"
              required
              maxLength={120}
              pattern=".*\S.*"
              defaultValue={goal?.name}
              placeholder="Ej. Gastos de insumos del mes"
            />
          </Field>
          <Field label="Monto de la meta (MXN)">
            <MoneyInput
              name="target"
              min="0.01"
              max="9999999"
              required
              defaultValue={goal?.target_amount}
              placeholder="0.00"
            />
          </Field>
          <Field label="Lo que llevas (MXN)">
            <MoneyInput
              name="current"
              min="0"
              max="9999999"
              defaultValue={goal?.current_amount ?? 0}
              placeholder="0.00"
            />
          </Field>
          <p className="field-hint">
            Lo que llevas al crear la meta es un punto de partida y no se resta
            de la cuenta principal. Solo los aportes con "Agregar $" se restan.
          </p>
          <Field label="Fecha límite (opcional)">
            <input
              name="deadline"
              type="date"
              defaultValue={goal?.deadline ?? ""}
            />
          </Field>
          {(problem || save.error) && (
            <p className="error" role="alert">
              {problem ?? errorMessage(save.error)}
            </p>
          )}
          <div className="form-actions">
            <button className="secondary" type="button" onClick={close}>
              Cancelar
            </button>
            <button className="primary" type="submit">
              {save.isPending ? "Guardando…" : "Guardar meta"}
            </button>
          </div>
        </fieldset>
      </form>
    </Modal>
  );
}

function ContributionForm({
  uid,
  goal,
  data,
  close,
}: {
  uid: string;
  goal: Goal;
  data: Snapshot;
  close: () => void;
}) {
  const [amount, setAmount] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  // Mismo id al reintentar: el gasto se guarda una sola vez aunque falle la meta.
  const [txId] = useState(() => crypto.randomUUID());
  const save = useSave(uid);
  const month = isoDate().slice(0, 7);
  const balance = cashBalance(data.transactions, month);
  const value = roundMoney(Number(amount));
  const valid = validateContribution(value) === null;
  const after = roundMoney(balance - (valid ? value : 0));
  return (
    <Modal title="Agregar a la meta" close={close} busy={save.isPending}>
      <p>
        {goal.name} · llevas {money(goal.current_amount)}
      </p>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const invalid = validateContribution(value);
          setProblem(invalid);
          if (invalid) return;
          const row: Goal = {
            ...goal,
            current_amount: roundMoney(goal.current_amount + value),
            updated_at: new Date().toISOString(),
          };
          const expense = goalContributionTransaction({
            id: txId,
            uid,
            goalName: goal.name,
            amount: value,
          });
          save.mutate(
            async () => {
              await saveRow("transactions", expense);
              await saveRow("goals", row);
            },
            { onSuccess: close },
          );
        }}
      >
        <fieldset disabled={save.isPending}>
          <Field label="Monto a sumar (MXN)">
            <MoneyInput
              name="amount"
              min="0.01"
              max="9999999"
              required
              autoFocus
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="0.00"
            />
          </Field>
          <p
            className={`goal-impact ${valid && after < 0 ? "negative" : ""}`}
            role="status"
          >
            {valid ? (
              <>
                <strong>Se restarán {money(value)} de tu cuenta principal</strong>
                Caja total: {money(balance)} → {money(after)}
                {after < 0 && ". Tu cuenta quedaría en negativo."}
                <br />
                Quedará registrado como gasto en Cuentas.
              </>
            ) : (
              <>
                Lo que agregues se restará de tu cuenta principal. Caja total:{" "}
                {money(balance)}
              </>
            )}
          </p>
          {(problem || save.error) && (
            <p className="error" role="alert">
              {problem ?? errorMessage(save.error)}
            </p>
          )}
          <div className="form-actions">
            <button className="secondary" type="button" onClick={close}>
              Cancelar
            </button>
            <button className="primary" type="submit">
              {save.isPending ? "Guardando…" : "Agregar y restar"}
            </button>
          </div>
        </fieldset>
      </form>
    </Modal>
  );
}

function DeleteGoal({
  uid,
  goal,
  close,
}: {
  uid: string;
  goal: Goal;
  close: () => void;
}) {
  const save = useSave(uid);
  return (
    <Modal title="¿Eliminar esta meta?" close={close} busy={save.isPending}>
      <p>
        {goal.name} · {money(goal.current_amount)} de{" "}
        {money(goal.target_amount)}
      </p>
      {save.error && (
        <p className="error" role="alert">
          {errorMessage(save.error)}
        </p>
      )}
      <div className="form-actions">
        <button className="secondary" disabled={save.isPending} onClick={close}>
          Cancelar
        </button>
        <button
          className="primary destructive"
          disabled={save.isPending}
          onClick={() =>
            save.mutate(() => deleteRow("goals", goal.id, uid), {
              onSuccess: close,
            })
          }
        >
          {save.isPending ? "Eliminando…" : "Eliminar meta"}
        </button>
      </div>
    </Modal>
  );
}
