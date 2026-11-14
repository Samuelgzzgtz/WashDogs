import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { supabase } from "../data/supabase";
import type { BotAlert } from "../data/types";
import { withDefaults, type BotConfig } from "./config";

export type BotData = { config: BotConfig; savedAt: string; alerts: BotAlert[] };

/** Configuración y avisos del bot. Va aparte del resto para no afectar la app si falla. */
export function useBot(uid: string) {
  return useQuery({
    queryKey: ["washdog-bot", uid],
    refetchInterval: 30_000,
    queryFn: async (): Promise<BotData> => {
      if (!supabase) throw new Error("La app no está conectada.");
      const [config, alerts] = await Promise.all([
        supabase.from("bot_config").select("*").eq("user_id", uid).maybeSingle(),
        supabase
          .from("bot_alerts")
          .select("*")
          .eq("user_id", uid)
          .order("created_at", { ascending: false })
          .limit(100),
      ]);
      if (config.error) throw config.error;
      if (alerts.error) throw alerts.error;
      return {
        config: withDefaults(config.data?.config),
        savedAt: config.data?.updated_at ?? "",
        alerts: alerts.data ?? [],
      };
    },
  });
}

export function useBotSave(uid: string) {
  const client = useQueryClient();
  return useMutation({
    networkMode: "always",
    mutationFn: async (action: () => Promise<void>) => {
      if (!navigator.onLine) throw new Error("Sin conexión. Reconéctate e inténtalo de nuevo.");
      await action();
    },
    onSuccess: () => void client.invalidateQueries({ queryKey: ["washdog-bot", uid] }),
  });
}

export async function saveBotConfig(uid: string, config: BotConfig) {
  if (!supabase) throw new Error("La app no está conectada.");
  const { error } = await supabase.from("bot_config").upsert({
    user_id: uid,
    config: config as unknown as Record<string, unknown>,
    updated_at: new Date().toISOString(),
  });
  if (error) throw error;
}

export async function setAlertStatus(alert: BotAlert, status: BotAlert["status"]) {
  if (!supabase) throw new Error("La app no está conectada.");
  const { error } = await supabase
    .from("bot_alerts")
    .update({ status, updated_at: new Date().toISOString() })
    .eq("id", alert.id)
    .eq("user_id", alert.user_id);
  if (error) throw error;
}
