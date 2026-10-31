import React, { useState, useEffect } from 'react';
import { useCall } from '../store/callContext';
import { LiveKitRoom, RoomAudioRenderer, VideoConference, ControlBar } from '@livekit/components-react';
import { PhoneOff, Video, VideoOff, Mic, MicOff, Maximize2 } from 'lucide-react';

export const ActiveCallModal: React.FC = () => {
  const { activeCall, endCall } = useCall();
  const [seconds, setSeconds] = useState(0);

  useEffect(() => {
    if (!activeCall || activeCall.status !== 'accepted') {
      setSeconds(0);
      return;
    }
    const interval = setInterval(() => {
      setSeconds((prev) => prev + 1);
    }, 1000);
    return () => clearInterval(interval);
  }, [activeCall?.status]);

  if (!activeCall) return null;

  const formatTime = (totalSec: number) => {
    const mins = Math.floor(totalSec / 60);
    const secs = totalSec % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const isRinging = activeCall.status === 'ringing';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 p-2 sm:p-6 backdrop-blur-lg">
      <div className="relative w-full max-w-5xl h-[85vh] bg-slate-900 border border-slate-800 rounded-3xl overflow-hidden flex flex-col shadow-2xl">
        {/* Top bar with call status & timer */}
        <div className="absolute top-0 inset-x-0 z-20 flex items-center justify-between px-6 py-4 bg-gradient-to-b from-black/80 to-transparent">
          <div className="flex items-center gap-3">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
            <span className="font-semibold text-sm text-white">
              {isRinging ? 'Calling...' : `In Call (${formatTime(seconds)})`}
            </span>
          </div>
          <span className="text-xs uppercase tracking-wider text-slate-400 font-medium px-2.5 py-1 bg-white/10 rounded-full">
            {activeCall.call_type} Call
          </span>
        </div>

        {/* Media content */}
        <div className="flex-1 w-full h-full relative flex items-center justify-center">
          {activeCall.livekit_token && activeCall.livekit_url ? (
            <LiveKitRoom
              video={activeCall.call_type === 'video'}
              audio={true}
              token={activeCall.livekit_token}
              serverUrl={activeCall.livekit_url}
              data-lk-theme="default"
              style={{ height: '100%', width: '100%' }}
              onDisconnected={endCall}
            >
              <VideoConference />
              <RoomAudioRenderer />
            </LiveKitRoom>
          ) : (
            <div className="flex flex-col items-center justify-center gap-4 text-center">
              <div className="w-24 h-24 rounded-full bg-violet-600/30 border border-violet-500 flex items-center justify-center text-white text-3xl font-bold animate-pulse">
                {activeCall.receiver_display_name?.charAt(0).toUpperCase() || 'V'}
              </div>
              <div>
                <h3 className="text-xl font-bold text-white mb-1">
                  {activeCall.receiver_display_name || activeCall.receiver_username || 'Family Member'}
                </h3>
                <p className="text-sm text-slate-400">Connecting WebRTC media session...</p>
              </div>
              <button
                onClick={endCall}
                className="mt-6 px-6 py-3 rounded-full bg-rose-600 hover:bg-rose-700 text-white font-medium flex items-center gap-2 shadow-lg shadow-rose-600/30"
              >
                <PhoneOff size={18} /> Cancel Call
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

