import { useEffect, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './supabase'

export function useAuth() {
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(Boolean(supabase))
  const [error, setError] = useState('')
  useEffect(() => {
    if (!supabase) return
    let active = true
    const { data } = supabase.auth.onAuthStateChange((_event, value) => {
      if (active) {
        setSession(value)
        setLoading(false)
      }
    })
    void supabase.auth
      .getSession()
      .then(({ data, error }) => {
        if (!active) return
        if (error)
          setError('No pudimos recuperar tu sesión. Vuelve a iniciar sesión.')
        setSession(data.session)
        setLoading(false)
      })
      .catch(() => {
        if (active) {
          setLoading(false)
          setError('No pudimos recuperar tu sesión.')
        }
      })
    return () => {
      active = false
      data.subscription.unsubscribe()
    }
  }, [])
  return { session, loading, error }
}
