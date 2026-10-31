import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  CalendarDays,
  Cloud,
  Home,
  MessageCircle,
  PawPrint,
  Plus,
  Settings2,
  Users,
  Wallet,
} from "lucide-react";
import type { Session } from "@supabase/supabase-js";
import { AppUpdate, Brand, Empty, useOnline } from "./components";
import { supabase } from "./data/supabase";
import { useAuth } from "./data/useAuth";
import { useData } from "./data/useData";
import type { Snapshot } from "./data/types";
import {
  appointmentClient,
  clientsToRemind,
  isoDate,
  readableDate,
  whatsapp,
} from "./domain";
import { Agenda } from "./features/Agenda";
import { Clients } from "./features/Clients";
import { Coins } from "./features/Coins";
import { Goals } from "./features/Goals";
import { Login } from "./features/Login";
import { Balance, Reports, TransactionForm } from "./features/Reports";
import { Settings } from "./features/Settings";

const navigation = [
  ["inicio", "Inicio", Home],
  ["agenda", "Agenda", CalendarDays],
  ["clientes", "Clientes", Users],
  ["reportes", "Cuentas", Wallet],
  ["ajustes", "Ajustes", Settings2],
] as const;
type Page = (typeof navigation)[number][0];
function currentPage(): Page {
  return (
    navigation.find(([id]) => window.location.hash.slice(1) === id)?.[0] ??
    "inicio"
  );
}

export function App() {
  const { session, loading, error } = useAuth();
  return (
    <>
      {loading ? (
        <div className="splash" role="status">
          <PawPrint size={42} />
          <p>Abriendo tu espacio…</p>
        </div>
      ) : session ? (
        <Workspace key={session.user.id} session={session} />
      ) : (
        <Login sessionError={error} />
      )}
      <AppUpdate />
    </>
  );
}

function Workspace({ session }: { session: Session }) {
  const [page, setPage] = useState<Page>(currentPage);
  const [newAppointment, setNewAppointment] = useState(false);
  const online = useOnline();
  const query = useData(session.user.id);
  const queryClient = useQueryClient();
  useEffect(() => {
    const update = () => {
      setPage(currentPage());
      if (currentPage() !== "agenda") setNewAppointment(false);
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", update);
    return () => window.removeEventListener("hashchange", update);
  }, []);
  async function logout() {
    const { error } = await supabase!.auth.signOut({ scope: "local" });
    if (error) throw error;
    queryClient.clear();
  }
  function goAgenda() {
    window.location.hash = "agenda";
    setPage("agenda");
    setNewAppointment(true);
  }
  return (
    <div className="workspace">
      <aside className="sidebar">
        <Brand />
        <nav aria-label="Navegación principal">
          {navigation.map(([id, label, Icon]) => (
            <a
              href={`#${id}`}
              aria-current={page === id ? "page" : undefined}
              key={id}
            >
              <Icon size={21} />
              <span>{label}</span>
            </a>
          ))}
        </nav>
        <div className="sidebar-foot">
          <PawPrint size={25} />
          <p>
            Un buen día empieza
            <br />
            con una colita feliz.
          </p>
        </div>
      </aside>
      <div className="main-wrap">
        <header className="topbar">
          <a className="mobile-wordmark" href="#inicio">
            washdog.
          </a>
          <span>Tu espacio de cuidado</span>
          <div className="cloud-status">
            <Cloud size={16} />
            {!online
              ? "Sin conexión"
              : query.isFetching
                ? "Actualizando…"
                : query.isError
                  ? "Error de conexión"
                  : "En la nube"}
          </div>
          <a className="account-avatar" href="#ajustes" aria-label="Tu cuenta">
            {session.user.email?.slice(0, 1).toUpperCase() ?? "W"}
          </a>
        </header>
        {!online && (
          <div className="notice offline" role="status">
            Estás sin conexión. Puedes consultar lo que ya abriste; reconéctate
            para guardar cambios.
          </div>
        )}
        <main id="contenido" className="main-content">
          {query.isPending ? (
            <div className="splash" role="status">
              Cargando tus datos…
            </div>
          ) : !query.data ? (
            <Empty>
              <p>No pudimos cargar tus datos de la nube.</p>
              <button className="primary" onClick={() => void query.refetch()}>
                Volver a intentar
              </button>
            </Empty>
          ) : (
            <>
              {query.isError && (
                <p className="notice" role="status">
                  No se pudieron actualizar los datos. Mostramos la última
                  consulta; vuelve a intentarlo al recuperar la conexión.
                </p>
              )}
              {page === "inicio" && (
                <Dashboard
                  data={query.data}
                  uid={session.user.id}
                  goAgenda={goAgenda}
                />
              )}
              {page === "agenda" && (
                <Agenda
                  data={query.data}
                  uid={session.user.id}
                  initialNew={newAppointment}
                />
              )}
              {page === "clientes" && (
                <Clients data={query.data} uid={session.user.id} />
              )}
              {page === "reportes" && (
                <Reports data={query.data} uid={session.user.id} />
              )}
              {page === "ajustes" && (
                <Settings
                  settings={query.data.settings}
                  email={session.user.email ?? ""}
                  logout={logout}
                />
              )}
            </>
          )}
        </main>
        <footer className="app-footer">
          Cuidamos de tu negocio. Tú cuidas de ellos.
        </footer>
      </div>
      <nav className="bottom-nav" aria-label="Navegación móvil">
        {navigation.map(([id, label, Icon]) => (
          <a
            href={`#${id}`}
            aria-current={page === id ? "page" : undefined}
            key={id}
          >
            <Icon size={22} />
            <span>{label}</span>
          </a>
        ))}
      </nav>
    </div>
  );
}

function Dashboard({
  data,
  uid,
  goAgenda,
}: {
  data: Snapshot;
  uid: string;
  goAgenda: () => void;
}) {
  const [type, setType] = useState<"IN" | "OUT" | null>(null);
  const today = isoDate();
  const appointments = data.appointments
    .filter((a) => a.date === today)
    .sort((a, b) => a.time.localeCompare(b.time));
  const pending = appointments.filter((a) => a.status !== "DONE");
  const reminders = clientsToRemind(data.clients, data.appointments);
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="today-date">
            {new Date().toLocaleDateString("es-MX", {
              weekday: "long",
              day: "numeric",
              month: "long",
            })}
          </p>
          <h1>
            Un buen día para cuidar<span className="heading-dot">.</span>
          </h1>
          <p>
            {pending.length
              ? `${pending.length} ${pending.length === 1 ? "colita te espera" : "colitas te esperan"} hoy.`
              : "Tu día, con todo en su lugar."}
          </p>
        </div>
        <button className="primary" onClick={goAgenda}>
          <Plus size={18} />
          Nueva cita
        </button>
      </div>
      <Balance data={data} month={today.slice(0, 7)} />
      <Goals data={data} uid={uid} />
      <Coins data={data} uid={uid} />
      <div className="quick-actions">
        <button onClick={() => setType("IN")}>
          <ArrowDownLeft />
          <span>Registrar ingreso</span>
          <ArrowRight size={17} />
        </button>
        <button onClick={() => setType("OUT")}>
          <ArrowUpRight />
          <span>Registrar gasto</span>
          <ArrowRight size={17} />
        </button>
        <a href="#clientes">
          <Users />
          <span>Ver clientes</span>
          <ArrowRight size={17} />
        </a>
      </div>
      <div className="dashboard-columns">
        <section>
          <div className="section-heading">
            <h2>
              Hoy en Washdog{" "}
              <span className="count">{appointments.length}</span>
            </h2>
            <a href="#agenda" className="text-button">
              Ver agenda
              <ArrowRight size={17} />
            </a>
          </div>
          {!appointments.length ? (
            <Empty>
              <CalendarDays size={30} />
              <p>Hoy la agenda está tranquila.</p>
              <button className="text-button" onClick={goAgenda}>
                Agregar una cita
              </button>
            </Empty>
          ) : (
            appointments.map((a) => {
              const client = appointmentClient(a, data.clients);
              return (
                <a href="#agenda" className="today-appointment" key={a.id}>
                  <span className="today-time">{a.time}</span>
                  <div>
                    <h3>{client?.pet_name ?? a.pet_name}</h3>
                    <p>{a.service}</p>
                    <small>{client?.owner_name ?? a.client_name}</small>
                  </div>
                  <span
                    className={`appointment-state ${a.status === "DONE" ? "done" : ""}`}
                  >
                    {a.status === "DONE" ? "Lista" : "Pendiente"}
                  </span>
                </a>
              );
            })
          )}
        </section>
        <section className="reminders">
          <div className="section-heading">
            <h2>Los extrañamos</h2>
            <PawPrint size={20} />
          </div>
          <p className="section-description">
            Ya pasó un mes desde su última visita.
          </p>
          {!reminders.length ? (
            <Empty>Todos al día. Qué gusto.</Empty>
          ) : (
            reminders.map((client) => {
              const link = whatsapp(
                client.phone,
                data.settings.tpl_reschedule,
                client.owner_name,
              );
              return (
                <div className="reminder-row" key={client.id}>
                  <div>
                    <h3>{client.pet_name}</h3>
                    <p>{client.owner_name}</p>
                    <small>{readableDate(client.last_visit)}</small>
                  </div>
                  {link ? (
                    <a
                      className="icon-button"
                      href={link}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={`Recordar visita a ${client.owner_name}`}
                    >
                      <MessageCircle size={20} />
                    </a>
                  ) : (
                    <a className="text-button" href="#clientes">
                      Añadir teléfono
                    </a>
                  )}
                </div>
              );
            })
          )}
        </section>
      </div>
      {type && (
        <TransactionForm uid={uid} type={type} close={() => setType(null)} />
      )}
    </>
  );
}
