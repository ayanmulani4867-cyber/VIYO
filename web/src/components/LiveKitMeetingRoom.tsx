import React from 'react';
import {
  LiveKitRoom,
  RoomAudioRenderer,
  VideoConference,
} from '@livekit/components-react';
import { Meeting } from '../types';
import { PhoneOff, Share2, Copy, Check } from 'lucide-react';

interface Props {
  meeting: Meeting;
  onLeave: () => void;
}

export const LiveKitMeetingRoom: React.FC<Props> = ({ meeting, onLeave }) => {
  const [copied, setCopied] = React.useState(false);

  const copyCode = () => {
    navigator.clipboard.writeText(meeting.meeting_code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!meeting.livekit_token || !meeting.livekit_url) {
    return (
      <div className="flex flex-col items-center justify-center p-12 text-center text-slate-400">
        <p>No valid token provided for this meeting.</p>
        <button
          onClick={onLeave}
          className="mt-4 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-xl text-sm"
        >
          Return
        </button>
      </div>
    );
  }

  return (
    <div className="relative w-full h-[calc(100vh-2rem)] rounded-2xl overflow-hidden border border-slate-800 bg-slate-950 flex flex-col shadow-2xl">
      {/* Top Banner with Meeting Code */}
      <div className="flex items-center justify-between px-6 py-3 bg-slate-900/90 border-b border-slate-800 backdrop-blur-md z-20">
        <div className="flex items-center gap-3">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
          <h2 className="font-semibold text-white text-base">{meeting.title}</h2>
          <div className="flex items-center gap-1.5 ml-4 px-3 py-1 bg-violet-950/60 border border-violet-800/40 rounded-lg text-xs font-mono text-violet-300">
            <span>{meeting.meeting_code}</span>
            <button
              onClick={copyCode}
              className="hover:text-white transition-colors"
              title="Copy meeting code"
            >
              {copied ? <Check size={14} className="text-emerald-400" /> : <Copy size={14} />}
            </button>
          </div>
        </div>

        <button
          onClick={onLeave}
          className="px-4 py-2 rounded-xl bg-rose-600/90 hover:bg-rose-700 text-white text-sm font-medium flex items-center gap-2 transition-all shadow-md"
        >
          <PhoneOff size={16} /> Leave Meeting
        </button>
      </div>

      {/* LiveKit SFU Video Room */}
      <div className="flex-1 w-full h-full relative">
        <LiveKitRoom
          video={true}
          audio={true}
          token={meeting.livekit_token}
          serverUrl={meeting.livekit_url}
          data-lk-theme="default"
          style={{ height: '100%', width: '100%' }}
          onDisconnected={onLeave}
        >
          <VideoConference />
          <RoomAudioRenderer />
        </LiveKitRoom>
      </div>
    </div>
  );
};

