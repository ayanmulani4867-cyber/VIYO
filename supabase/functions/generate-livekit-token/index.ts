import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { AccessToken } from "npm:livekit-server-sdk"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { room_name, participant_identity, participant_name } = await req.json()

    if (!room_name || !participant_identity) {
      return new Response(
        JSON.stringify({ error: "room_name and participant_identity are required" }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 400 }
      )
    }

    const apiKey = Deno.env.get('LIVEKIT_API_KEY')
    const apiSecret = Deno.env.get('LIVEKIT_API_SECRET')
    const livekitUrl = Deno.env.get('LIVEKIT_URL') || 'wss://viyo-vz4rl9o4.livekit.cloud'

    if (!apiKey || !apiSecret) {
      return new Response(
        JSON.stringify({ error: "LIVEKIT_API_KEY or LIVEKIT_API_SECRET environment variables are missing in Supabase Edge Function Secrets." }),
        { headers: { ...corsHeaders, 'Content-Type': 'application/json' }, status: 500 }
      )
    }

    const at = new AccessToken(apiKey, apiSecret, {
      identity: participant_identity,
      name: participant_name || participant_identity,
      ttl: '4h',
    })
    at.addGrant({
      roomJoin: true,
      room: room_name,
      canPublish: true,
      canSubscribe: true,
    })

    const token = await at.toJwt()

    return new Response(
      JSON.stringify({
        token,
        livekit_url: livekitUrl,
        room_name
      }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      }
    )
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})

