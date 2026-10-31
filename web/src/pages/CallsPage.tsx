import React, { useState, useEffect } from 'react';
import { useAuth } from '../store/authContext';
import { useCall } from '../store/callContext';
import { api } from '../services/api';
import { CallRecord } from '../types';
import {
  Phone,
  Video,
  PhoneIncoming,
  PhoneOutgoing,
  PhoneMissed,
  Clock,
  ArrowUpRight,
} from 'lucide-react';

export const CallsPage: React.FC = () => {
  const { user } = useAuth();
  const { startCall } = useCall();
  const [calls, setCalls] = useState<CallRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getCallHistory().then(setCalls).finally(() => setLoading(false));
  }, []);

  const formatDuration = (sec: number) => {
    if (!sec) return '0s';
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return m > 0 ? `${m}m ${s}s` : `${s}s`;
  };

  return (
    <div className="p-6 md:p-10 max-w-5xl mx-auto flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold text-white">Call History</h1>
          <p className="text-xs text-slate-400 mt-1">
            Logs of your one-to-one audio and video communication sessions.
          </p>
        </div>
      </div>

      <div className="rounded-3xl bg-slate-900/70 border border-slate-800 overflow-hidden shadow-xl">
        {calls.length === 0 ? (
          <div className="py-16 text-center text-slate-500 text-sm">
            No call records found. Start a call with one of your contacts!
          </div>
        ) : (
          <div className="divide-y divide-slate-800/80">
            {calls.map((call) => {
              const isCaller = call.caller_id === user?.id;
              const isMissed = call.status === 'missed' || call.status === 'rejected';
              const targetUserId = isCaller ? call.receiver_id : call.caller_id;
              const targetName = isCaller
                ? call.receiver_display_name || call.receiver_username || 'Participant'
                : call.caller_display_name || call.caller_username || 'Participant';

              return (
                <div
                  key={call.id}
                  className="p-4 md:p-5 flex items-center justify-between hover:bg-slate-850/50 transition-colors"
                >
                  <div className="flex items-center gap-4">
                    <div
                      className={`w-12 h-12 rounded-2xl flex items-center justify-center ${
                        isMissed
                          ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          : isCaller
                          ? 'bg-blue-500/10 text-blue-400 border border-blue-500/20'
                          : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                      }`}
                    >
                      {isMissed ? (
                        <PhoneMissed size={20} />
                      ) : isCaller ? (
                        <PhoneOutgoing size={20} />
                      ) : (
                        <PhoneIncoming size={20} />
                      )}
                    </div>

                    <div>
                      <h4 className="text-sm md:text-base font-semibold text-white">{targetName}</h4>
                      <p className="text-xs text-slate-400 flex items-center gap-2 mt-0.5">
                        <span className="capitalize">{call.call_type} Call</span>
                        <span>•</span>
                        <span>{formatDuration(call.duration_seconds)}</span>
                        <span>•</span>
                        <span>{new Date(call.created_at).toLocaleDateString()}</span>
                      </p>
                    </div>
                  </div>

                  {targetUserId && (
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => startCall(targetUserId, 'video')}
                        className="p-2.5 rounded-xl bg-violet-600/20 hover:bg-violet-600 text-violet-400 hover:text-white transition-colors"
                        title="Video Call"
                      >
                        <Video size={16} />
                      </button>
                      <button
                        onClick={() => startCall(targetUserId, 'voice')}
                        className="p-2.5 rounded-xl bg-emerald-600/20 hover:bg-emerald-600 text-emerald-400 hover:text-white transition-colors"
                        title="Voice Call"
                      >
                        <Phone size={16} />
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

