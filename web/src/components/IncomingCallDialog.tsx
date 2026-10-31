import React from 'react';
import { useCall } from '../store/callContext';
import { Phone, PhoneOff, Video } from 'lucide-react';

export const IncomingCallDialog: React.FC = () => {
  const { incomingCall, acceptCall, rejectCall } = useCall();

  if (!incomingCall) return null;

  const isVideo = incomingCall.call_type === 'video';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-md animate-fade-in p-4">
      <div className="bg-slate-900 border border-violet-500/30 rounded-3xl p-8 max-w-sm w-full shadow-2xl flex flex-col items-center text-center">
        {/* Pulsing Avatar */}
        <div className="relative mb-6">
          <div className="w-24 h-24 rounded-full bg-gradient-to-tr from-violet-600 to-indigo-600 flex items-center justify-center text-white text-3xl font-bold overflow-hidden shadow-lg border-2 border-violet-400">
            {incomingCall.caller.avatar_url ? (
              <img
                src={incomingCall.caller.avatar_url}
                alt={incomingCall.caller.display_name}
                className="w-full h-full object-cover"
              />
            ) : (
              incomingCall.caller.display_name.charAt(0).toUpperCase()
            )}
          </div>
          <div className="absolute inset-0 rounded-full border-4 border-violet-500 animate-ping opacity-75 pointer-events-none" />
        </div>

        <h3 className="text-xl font-bold text-white mb-1">
          {incomingCall.caller.display_name}
        </h3>
        <p className="text-sm text-slate-400 mb-6 flex items-center gap-1.5 justify-center">
          {isVideo ? <Video size={16} className="text-violet-400" /> : <Phone size={16} className="text-violet-400" />}
          Incoming {isVideo ? 'Video' : 'Voice'} Call...
        </p>

        {/* Action Buttons */}
        <div className="flex items-center gap-8">
          <button
            onClick={rejectCall}
            className="w-14 h-14 rounded-full bg-rose-600 hover:bg-rose-700 active:scale-95 transition-all text-white flex items-center justify-center shadow-lg shadow-rose-600/30"
            title="Decline"
          >
            <PhoneOff size={24} />
          </button>
          <button
            onClick={acceptCall}
            className="w-14 h-14 rounded-full bg-emerald-600 hover:bg-emerald-700 active:scale-95 transition-all text-white flex items-center justify-center shadow-lg shadow-emerald-600/30 animate-pulse"
            title="Accept"
          >
            <Phone size={24} />
          </button>
        </div>
      </div>
    </div>
  );
};

