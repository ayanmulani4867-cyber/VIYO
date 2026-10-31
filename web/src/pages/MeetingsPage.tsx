import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuth } from '../store/authContext';
import { api } from '../services/api';
import { Meeting } from '../types';
import { LiveKitMeetingRoom } from '../components/LiveKitMeetingRoom';
import { Video, Plus, KeyRound, ShieldCheck, Users } from 'lucide-react';

export const MeetingsPage: React.FC = () => {
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [activeMeeting, setActiveMeeting] = useState<Meeting | null>(null);
  const [joinCode, setJoinCode] = useState('');
  const [meetingTitle, setMeetingTitle] = useState('');
  const [waitingRoom, setWaitingRoom] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Auto-join from URL parameter ?code=...
  useEffect(() => {
    const code = searchParams.get('code');
    if (code && !activeMeeting) {
      handleJoinByCode(code);
    }
  }, [searchParams]);

  const handleCreateMeeting = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const title = meetingTitle.trim() || `${user?.profile?.display_name || user?.username}'s Meeting`;
      const mtg = await api.createMeeting(title, waitingRoom);
      setActiveMeeting(mtg);
      setSearchParams({ code: mtg.meeting_code });
    } catch (err: any) {
      setError(err.message || 'Failed to create meeting');
    } finally {
      setLoading(false);
    }
  };

  const handleJoinByCode = async (codeToJoin?: string) => {
    const code = (codeToJoin || joinCode).trim().toLowerCase();
    if (!code) return;
    setLoading(true);
    setError(null);
    try {
      const mtg = await api.joinMeeting(code);
      setActiveMeeting(mtg);
      setSearchParams({ code: mtg.meeting_code });
    } catch (err: any) {
      setError(err.message || 'Failed to join meeting. Check meeting code.');
    } finally {
      setLoading(false);
    }
  };

  const handleLeaveMeeting = () => {
    setActiveMeeting(null);
    setSearchParams({});
  };

  if (activeMeeting) {
    return (
      <div className="p-4 h-full flex flex-col">
        <LiveKitMeetingRoom meeting={activeMeeting} onLeave={handleLeaveMeeting} />
      </div>
    );
  }

  return (
    <div className="p-6 md:p-10 max-w-4xl mx-auto flex flex-col gap-8">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold text-white">Group Video Meetings</h1>
        <p className="text-xs text-slate-400 mt-1">
          Host high-quality WebRTC multi-party video conferences with screen share and waiting rooms.
        </p>
      </div>

      {error && (
        <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Create Meeting Card */}
        <div className="p-8 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-2xl flex flex-col justify-between">
          <div>
            <div className="w-12 h-12 rounded-2xl bg-violet-600/20 text-violet-400 flex items-center justify-center border border-violet-500/30 mb-6">
              <Plus size={24} />
            </div>
            <h3 className="text-xl font-bold text-white mb-2">Host New Meeting</h3>
            <p className="text-xs text-slate-400 mb-6">
              Generate an instant private meeting link and invite family members or colleagues.
            </p>

            <form onSubmit={handleCreateMeeting} className="flex flex-col gap-4">
              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1.5">Meeting Title</label>
                <input
                  type="text"
                  value={meetingTitle}
                  onChange={(e) => setMeetingTitle(e.target.value)}
                  placeholder="e.g. Family Weekend Catch-up"
                  className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500"
                />
              </div>

              <label className="flex items-center gap-2 cursor-pointer mt-1">
                <input
                  type="checkbox"
                  checked={waitingRoom}
                  onChange={(e) => setWaitingRoom(e.target.checked)}
                  className="rounded border-slate-700 bg-slate-900 text-violet-600 focus:ring-0"
                />
                <span className="text-xs text-slate-300">Enable Waiting Room (Host approval)</span>
              </label>

              <button
                type="submit"
                disabled={loading}
                className="w-full mt-4 py-3 rounded-xl bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm transition-all shadow-lg shadow-violet-600/30 disabled:opacity-50"
              >
                {loading ? 'Starting...' : 'Start Meeting Now'}
              </button>
            </form>
          </div>
        </div>

        {/* Join Meeting Card */}
        <div className="p-8 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-2xl flex flex-col justify-between">
          <div>
            <div className="w-12 h-12 rounded-2xl bg-blue-600/20 text-blue-400 flex items-center justify-center border border-blue-500/30 mb-6">
              <KeyRound size={24} />
            </div>
            <h3 className="text-xl font-bold text-white mb-2">Join with Code</h3>
            <p className="text-xs text-slate-400 mb-6">
              Enter the unique 9-character code provided by the meeting host.
            </p>

            <div className="flex flex-col gap-4">
              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1.5">Meeting Code</label>
                <input
                  type="text"
                  value={joinCode}
                  onChange={(e) => setJoinCode(e.target.value)}
                  placeholder="abc-defg-hij"
                  className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                />
              </div>

              <button
                type="button"
                onClick={() => handleJoinByCode()}
                disabled={loading || !joinCode.trim()}
                className="w-full mt-4 py-3 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm transition-all shadow-lg shadow-blue-600/30 disabled:opacity-50"
              >
                {loading ? 'Joining...' : 'Join Meeting'}
              </button>
            </div>
          </div>

          <div className="mt-8 pt-6 border-t border-slate-800/80 flex items-center gap-2 text-xs text-slate-500">
            <ShieldCheck size={14} className="text-violet-400" />
            <span>SFU Architecture • DTLS-SRTP Audio/Video Encryption</span>
          </div>
        </div>
      </div>
    </div>
  );
};

