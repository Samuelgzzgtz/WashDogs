import { readFileSync } from 'node:fs'
import { PGlite } from '@electric-sql/pglite'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'

const owner = '11111111-1111-4111-8111-111111111111'
const other = '22222222-2222-4222-8222-222222222222'
let db: PGlite
const migration = readFileSync(
  new URL(
    '../../../supabase/migrations/20261023000000_webapp.sql',
    import.meta.url,
  ),
  'utf8',
)

async function asUser(id: string) {
  await db.exec(
    `reset role; select set_config('request.jwt.claim.sub', '${id}', false); set role authenticated;`,
  )
}
async function createClient(id = 'c1', user = owner) {
  await db.query(
    'insert into public.clients(id,user_id,owner_name,pet_name,last_visit) values ($1,$2,$3,$4,$5) on conflict (id) do nothing',
    [id, user, 'Samuel', id === 'c2' ? 'Toby' : 'Luna', '2026-09-01'],
  )
}
async function createAppointment(
  id: string,
  client = 'c1',
  date = '2026-10-23',
  series: string | null = null,
  status = 'SCHEDULED',
) {
  await db.query(
    `insert into public.appointments(id,user_id,client_id,client_name,pet_name,time,end_time,date,series_id,repeat_every_days,status)
    values ($1,$2,$3,'Samuel','Luna','09:00','10:00',$4,$5,$6,$7)`,
    [id, owner, client, date, series, series ? 7 : null, status],
  )
}

beforeAll(async () => {
  db = new PGlite()
  await db.exec(`create role authenticated; create role anon; create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid$$;
    grant usage on schema auth, public to authenticated;
    grant execute on function auth.uid() to authenticated;
    insert into auth.users values ('${owner}'), ('${other}');`)
  await db.exec(migration)
  // Reapplying the additive migration must preserve data and remain safe.
  await createClient()
  await db.exec(migration)
  expect((await db.query('select id from public.clients')).rows).toHaveLength(1)
}, 60_000)
beforeEach(async () => {
  await db.exec(
    'reset role; truncate public.clients, public.appointments, public.transactions, public.tombstones, public.user_settings, public.appointment_series;',
  )
  await db.exec(
    `insert into auth.users values ('${owner}'), ('${other}') on conflict do nothing;`,
  )
  await asUser(owner)
  await createClient()
})
afterAll(async () => {
  await db?.close()
})

describe('migración PostgreSQL y seguridad por usuario', () => {
  it('RLS impide leer o escribir clientes de otra cuenta', async () => {
    await createClient()
    await asUser(other)
    expect((await db.query('select * from public.clients')).rows).toEqual([])
    await expect(createClient('foreign', owner)).rejects.toThrow(
      /row-level security/,
    )
    await db.exec("delete from public.clients where id = 'c1'")
    await asUser(owner)
    expect((await db.query('select * from public.clients')).rows).toHaveLength(
      1,
    )
  })
  it('borra cliente y citas con tombstones atómicos y conserva otras mascotas del mismo dueño', async () => {
    await createClient()
    await createClient('c2')
    await createAppointment('a1')
    await db.query(
      "insert into public.appointments(id,user_id,client_name,pet_name,date,time) values ('legacy',$1,'Samuel','Luna','2026-10-24','09:00'), ('other-pet',$1,'Samuel','Toby','2026-10-25','09:00')",
      [owner],
    )
    await db.exec("delete from public.clients where id = 'c1'")
    expect((await db.query('select id from public.appointments')).rows).toEqual(
      [{ id: 'other-pet' }],
    )
    expect(
      (
        await db.query(
          'select entity,entity_id from public.tombstones order by entity_id',
        )
      ).rows,
    ).toEqual([
      { entity: 'appointments', entity_id: 'a1' },
      { entity: 'clients', entity_id: 'c1' },
      { entity: 'appointments', entity_id: 'legacy' },
    ])
  })
  it('bloquea traslapes en servidor y permite horarios contiguos', async () => {
    await createClient('c2')
    await createAppointment('a1')
    await expect(createAppointment('a2')).rejects.toThrow('WASHDOG_OVERLAP')
    await db.query(
      "insert into public.appointments(id,user_id,client_name,pet_name,time,end_time,date) values ('a3',$1,'Samuel','Toby','10:00','11:00','2026-10-23')",
      [owner],
    )
    expect(
      (await db.query('select id from public.appointments')).rows,
    ).toHaveLength(2)
  })
  it('revierte toda la serie si una ocurrencia choca', async () => {
    await createAppointment('existing', 'c1', '2026-10-30')
    await expect(
      db.query(
        "insert into public.appointments(id,user_id,client_name,pet_name,time,end_time,date) values ('first',$1,'Samuel','Luna','09:00','10:00','2026-10-23'), ('conflict',$1,'Samuel','Luna','09:00','10:00','2026-10-30')",
        [owner],
      ),
    ).rejects.toThrow('WASHDOG_OVERLAP')
    expect((await db.query('select id from public.appointments')).rows).toEqual(
      [{ id: 'existing' }],
    )
  })
  it('completar una cita actualiza última visita sin crear un cobro automático', async () => {
    await createClient()
    await createAppointment('a1')
    await db.exec(
      "update public.appointments set status = 'DONE' where id = 'a1'",
    )
    expect(
      (await db.query('select last_visit from public.clients')).rows,
    ).toEqual([{ last_visit: '2026-10-23' }])
    expect((await db.query('select * from public.transactions')).rows).toEqual(
      [],
    )
  })
  it('detiene la serie sin borrar visitas completadas ni permitir a Android regenerarla', async () => {
    await createAppointment('past', 'c1', '2026-10-16', 's1', 'DONE')
    await createAppointment('today', 'c1', '2026-10-23', 's1')
    await createAppointment('future', 'c1', '2026-10-30', 's1')
    await db.query('select public.washdog_stop_series($1,$2)', [
      's1',
      '2026-10-23',
    ])
    expect(
      (await db.query('select id,repeat_every_days from public.appointments'))
        .rows,
    ).toEqual([{ id: 'past', repeat_every_days: null }])
    expect(
      (
        await db.query(
          'select entity_id from public.tombstones order by entity_id',
        )
      ).rows,
    ).toEqual([{ entity_id: 'future' }, { entity_id: 'today' }])
  })
  it('ignora una subida antigua de Android sin resucitar clientes o bloquear horarios', async () => {
    await createClient()
    await createAppointment('a1', 'c1', '2026-10-23', null, 'DONE')
    await db.exec("delete from public.clients where id = 'c1'")
    await createClient()
    await createAppointment('a1', 'c1', '2026-10-23', null, 'DONE')
    expect((await db.query('select * from public.clients')).rows).toEqual([])
    expect((await db.query('select * from public.appointments')).rows).toEqual(
      [],
    )
    await createClient('c2')
    await createAppointment('new', 'c2')
    expect((await db.query('select id from public.appointments')).rows).toEqual(
      [{ id: 'new' }],
    )
  })
  it('permite una edición intencional posterior al tombstone', async () => {
    await createClient()
    await db.exec("delete from public.clients where id = 'c1'")
    await db.query(
      "insert into public.clients(id,user_id,owner_name,pet_name,updated_at) values ('c1',$1,'Samuel','Luna','2099-01-01T00:00:00Z')",
      [owner],
    )
    expect((await db.query('select id from public.clients')).rows).toHaveLength(
      1,
    )
  })
  it('una copia antigua de Android no reactiva una serie detenida', async () => {
    await createAppointment('past', 'c1', '2026-10-16', 's1', 'DONE')
    await db.query('select public.washdog_stop_series($1,$2)', [
      's1',
      '2026-10-23',
    ])
    await db.exec(
      "update public.appointments set repeat_every_days = 7, updated_at = '' where id = 'past'",
    )
    expect(
      (await db.query('select repeat_every_days from public.appointments'))
        .rows,
    ).toEqual([{ repeat_every_days: null }])
  })
  it('permite eliminar una cuenta Auth con todos sus datos sin fallar por tombstones', async () => {
    await createClient()
    await createAppointment('a1')
    await db.exec(`reset role; delete from auth.users where id = '${owner}';`)
    expect((await db.query('select * from public.clients')).rows).toEqual([])
    expect((await db.query('select * from public.appointments')).rows).toEqual(
      [],
    )
    expect((await db.query('select * from public.tombstones')).rows).toEqual([])
  })
  it('vincula citas legacy antes de renombrar al cliente y mantiene el borrado en cascada', async () => {
    await createClient()
    await createAppointment('legacy', '')
    await db.exec(
      "update public.clients set owner_name = 'Renombrado', pet_name = 'Nueva Luna' where id = 'c1'",
    )
    expect(
      (
        await db.query(
          'select client_id,client_name,pet_name from public.appointments',
        )
      ).rows,
    ).toEqual([
      { client_id: 'c1', client_name: 'Renombrado', pet_name: 'Nueva Luna' },
    ])
    await db.exec("delete from public.clients where id = 'c1'")
    expect((await db.query('select * from public.appointments')).rows).toEqual(
      [],
    )
  })
  it('guarda una regla estable al crear la serie y no la cambia al editar una ocurrencia', async () => {
    await createAppointment('first', 'c1', '2026-10-23', 's1')
    await db.exec(
      "update public.appointments set date = '2026-10-24', time = '11:00', end_time = '12:00' where id = 'first'",
    )
    expect(
      (
        await db.query(
          "select template->>'date' as date, template->>'time' as time from public.appointment_series",
        )
      ).rows,
    ).toEqual([{ date: '2026-10-23', time: '09:00' }])
  })
  it('rechaza ampliaciones calculadas antes de detener la serie', async () => {
    await createAppointment('past', 'c1', '2026-10-16', 's1', 'DONE')
    await db.query('select public.washdog_stop_series($1,$2)', [
      's1',
      '2026-10-23',
    ])
    await createAppointment('late-generated', 'c1', '2027-01-08', 's1')
    expect((await db.query('select id from public.appointments')).rows).toEqual(
      [{ id: 'past' }],
    )
  })
  it('reenviar una visita DONE sin cambios no adelanta la versión del cliente', async () => {
    await createClient()
    await createAppointment('done', 'c1', '2026-10-23', null, 'DONE')
    const before = (await db.query('select updated_at from public.clients'))
      .rows
    await db.exec(
      "update public.appointments set status = 'DONE' where id = 'done'",
    )
    expect(
      (await db.query('select updated_at from public.clients')).rows,
    ).toEqual(before)
  })
  it('conserva las plantillas web ante una sincronización antigua de Android', async () => {
    await db.query(
      "insert into public.user_settings values ($1,'Actual','Confirmación','Nueva visita','2026-10-23T12:00:00Z')",
      [owner],
    )
    await db.query(
      "insert into public.user_settings values ($1,'Antigua','Antigua','Antigua','') on conflict (user_id) do update set tpl_welcome = excluded.tpl_welcome, updated_at = excluded.updated_at",
      [owner],
    )
    expect(
      (await db.query('select tpl_welcome from public.user_settings')).rows,
    ).toEqual([{ tpl_welcome: 'Actual' }])
  })
  it('eliminar un cliente detiene sus series y evita que vuelvan a aparecer citas', async () => {
    await createClient()
    await createAppointment('a1', 'c1', '2026-10-23', 's1')
    await db.exec("delete from public.clients where id = 'c1'")
    await expect(
      createAppointment('future', 'c1', '2027-01-08', 's1'),
    ).rejects.toThrow('WASHDOG_CLIENT_MISSING')
    expect((await db.query('select * from public.appointments')).rows).toEqual(
      [],
    )
    expect(
      (
        await db.query(
          "select stopped_at <> '' as stopped from public.appointment_series",
        )
      ).rows,
    ).toEqual([{ stopped: true }])
  })
  it('renombrar no restaura una cita legacy todavía presente pero borrada por tombstone', async () => {
    await createClient()
    await createAppointment('ghost', '')
    await db.query(
      "insert into public.tombstones values ($1,'appointments','ghost','2026-10-23T12:00:00Z')",
      [owner],
    )
    await db.exec(
      "update public.clients set owner_name = 'Nuevo nombre' where id = 'c1'",
    )
    expect(
      (
        await db.query(
          "select client_name,updated_at from public.appointments where id = 'ghost'",
        )
      ).rows,
    ).toEqual([{ client_name: 'Samuel', updated_at: '' }])
  })
  it('el backfill omite citas borradas y conserva las reglas de citas activas', async () => {
    await createClient()
    await createAppointment('ghost', 'c1', '2026-10-23', 'deleted-series')
    await createAppointment('active', 'c1', '2026-10-24', 'active-series')
    await db.exec('delete from public.appointment_series')
    await db.query(
      "insert into public.tombstones values ($1,'appointments','ghost','2026-10-23T12:00:00Z')",
      [owner],
    )
    await db.exec('reset role')
    await db.exec(migration)
    expect(
      (await db.query('select id from public.appointment_series')).rows,
    ).toEqual([{ id: 'active-series' }])
  })
  it('el backfill omite series de clientes borrados aunque la cita tenga una versión posterior', async () => {
    await createClient()
    await createAppointment('orphan', 'c1', '2026-10-23', 's1')
    await db.exec(
      "update public.appointments set updated_at = '2099-01-01T00:00:00Z'; delete from public.appointment_series",
    )
    await db.query(
      "insert into public.tombstones values ($1,'clients','c1','2026-10-23T12:00:00Z')",
      [owner],
    )
    await db.exec('reset role')
    await db.exec(migration)
    expect(
      (await db.query('select * from public.appointment_series')).rows,
    ).toEqual([])
  })
  it('el backfill respeta las cancelaciones de serie persistidas por Android', async () => {
    await createClient()
    await createAppointment('past', 'c1', '2026-10-23', 's1', 'DONE')
    await db.exec('delete from public.appointment_series')
    await db.query(
      "insert into public.tombstones values ($1,'appointment_series','s1','2026-10-23T12:00:00Z')",
      [owner],
    )
    await db.exec('reset role')
    await db.exec(migration)
    expect(
      (await db.query('select * from public.appointment_series')).rows,
    ).toEqual([])
  })
  it('un reintento Android no atrasa la fecha de borrado registrada por el servidor', async () => {
    await db.query(
      "insert into public.tombstones values ($1,'appointments','a1','2026-10-23T12:00:00Z')",
      [owner],
    )
    await db.query(
      "insert into public.tombstones values ($1,'appointments','a1','2026-10-22T12:00:00Z') on conflict (user_id,entity,entity_id) do update set deleted_at = excluded.deleted_at",
      [owner],
    )
    expect(
      (await db.query('select deleted_at from public.tombstones')).rows,
    ).toEqual([{ deleted_at: '2026-10-23T12:00:00Z' }])
  })
  it('reproduce el borrado de un cliente offline y detiene sus series antes de volver a subir datos', async () => {
    await createClient()
    await createAppointment('a1', 'c1', '2026-10-23', 's1')
    await db.query('select public.washdog_delete_client($1,$2)', [
      'c1',
      '2026-10-23T12:00:00Z',
    ])
    await expect(
      createAppointment('late', 'c1', '2027-01-08', 's1'),
    ).rejects.toThrow('WASHDOG_CLIENT_MISSING')
    expect((await db.query('select * from public.clients')).rows).toEqual([])
    expect((await db.query('select * from public.appointments')).rows).toEqual(
      [],
    )
    expect(
      (
        await db.query(
          "select stopped_at <> '' as stopped from public.appointment_series",
        )
      ).rows,
    ).toEqual([{ stopped: true }])
  })
  it('un borrado offline antiguo conserva una edición posterior y respeta la cuenta autenticada', async () => {
    await createClient()
    await db.exec(
      "update public.clients set updated_at = '2099-01-01T00:00:00Z'",
    )
    await db.query('select public.washdog_delete_client($1,$2)', [
      'c1',
      '2026-10-23T12:00:00Z',
    ])
    expect((await db.query('select id from public.clients')).rows).toHaveLength(
      1,
    )
    await asUser(other)
    await db.query('select public.washdog_delete_client($1,$2)', [
      'c1',
      '2099-02-01T00:00:00Z',
    ])
    await asUser(owner)
    expect((await db.query('select id from public.clients')).rows).toHaveLength(
      1,
    )
  })
  it('detener una serie no revive una visita DONE borrada lógicamente', async () => {
    await createClient()
    await createAppointment('ghost-done', 'c1', '2026-10-16', 's1', 'DONE')
    await db.query(
      "insert into public.tombstones values ($1,'appointments','ghost-done','2026-10-23T12:00:00Z')",
      [owner],
    )
    await db.query('select public.washdog_stop_series($1,$2)', [
      's1',
      '2026-10-23',
    ])
    expect(
      (
        await db.query(
          "select updated_at from public.appointments where id = 'ghost-done'",
        )
      ).rows,
    ).toEqual([{ updated_at: '' }])
    expect(
      (
        await db.query(
          "select public.washdog_is_deleted($1, 'appointments', 'ghost-done', '') as deleted",
          [owner],
        )
      ).rows,
    ).toEqual([{ deleted: true }])
  })
  it('rechaza una serie creada offline que llega después del borrado de su cliente', async () => {
    await db.exec("delete from public.clients where id = 'c1'")
    await expect(
      createAppointment('offline-new', 'c1', '2027-01-08', 'offline-series'),
    ).rejects.toThrow('WASHDOG_CLIENT_MISSING')
    expect((await db.query('select * from public.appointments')).rows).toEqual(
      [],
    )
    expect(
      (await db.query('select * from public.appointment_series')).rows,
    ).toEqual([])
  })
  it('avisa si una cita llega antes que su cliente y permite reintentar después de subirlo', async () => {
    await expect(createAppointment('early', 'not-uploaded')).rejects.toThrow(
      'WASHDOG_CLIENT_MISSING',
    )
    expect((await db.query('select * from public.appointments')).rows).toEqual(
      [],
    )
    expect((await db.query('select * from public.tombstones')).rows).toEqual([])
    await createClient('not-uploaded')
    await createAppointment('early', 'not-uploaded')
    expect(
      (await db.query('select id,client_id from public.appointments')).rows,
    ).toEqual([{ id: 'early', client_id: 'not-uploaded' }])
  })
  it('conserva la fecha original del borrado offline y acepta una edición posterior todavía sin subir', async () => {
    await db.query('select public.washdog_delete_client($1,$2)', [
      'c1',
      '2026-01-01T12:00:00Z',
    ])
    expect(
      (
        await db.query(
          "select deleted_at from public.tombstones where entity = 'clients'",
        )
      ).rows,
    ).toEqual([{ deleted_at: '2026-01-01T12:00:00Z' }])
    await db.query(
      "insert into public.clients(id,user_id,owner_name,pet_name,updated_at) values ('c1',$1,'Editado después','Luna','2026-01-02T12:00:00Z')",
      [owner],
    )
    expect(
      (await db.query("select owner_name from public.clients where id = 'c1'"))
        .rows,
    ).toEqual([{ owner_name: 'Editado después' }])
  })
  it('permite completar una cita histórica desde Android después de detener la serie', async () => {
    await createAppointment('past', 'c1', '2026-01-01', 's1')
    await db.query('select public.washdog_stop_series($1,$2)', [
      's1',
      '2026-10-23',
    ])
    await db.query(
      "insert into public.appointments(id,user_id,client_id,client_name,pet_name,time,end_time,date,status,series_id,repeat_every_days,updated_at) values ('past',$1,'c1','Samuel','Luna','09:00','10:00','2026-01-01','DONE','s1',7,'2099-01-01T00:00:00Z') on conflict(id) do update set status=excluded.status, repeat_every_days=excluded.repeat_every_days, updated_at=excluded.updated_at",
      [owner],
    )
    expect(
      (
        await db.query(
          "select status,repeat_every_days from public.appointments where id = 'past'",
        )
      ).rows,
    ).toEqual([{ status: 'DONE', repeat_every_days: null }])
  })
  it('una edición nueva de plantillas Android conserva su timestamp al reintentarse después de un cambio web', async () => {
    await db.query(
      "insert into public.user_settings values ($1,'Web','Confirmar','Visita','2026-01-01T12:00:00Z')",
      [owner],
    )
    await db.query(
      "insert into public.user_settings values ($1,'Android nuevo','Confirmar','Visita','2026-01-02T12:00:00Z') on conflict(user_id) do update set tpl_welcome=excluded.tpl_welcome,updated_at=excluded.updated_at",
      [owner],
    )
    expect(
      (
        await db.query(
          'select tpl_welcome,updated_at from public.user_settings',
        )
      ).rows,
    ).toEqual([
      { tpl_welcome: 'Android nuevo', updated_at: '2026-01-02T12:00:00Z' },
    ])
  })
})
