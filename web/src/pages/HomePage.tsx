import React, { useState, useEffect } from 'react';
import { useAuth } from '../store/authContext';
import { useCall } from '../store/callContext';
import { useNavigate } from 'react-router-dom';
import { api } from '../services/api';
import { Contact, CallRecord } from '../types';
import {
  Video,
  Phone,
  MessageSquare,
  Users,
  Plus,
  ArrowUpRight,
  ShieldCheck,
  Activity,
  Clock,
  Sparkles,
} from 'lucide-react';

export const HomePage: React.FC = () => {
  const { user } = useAuth();
  const { startCall } = useCall();
  const navigate = useNavigate();

  const [contacts, setContacts] = useState<Contact[]>([]);
  const [recentCalls, setRecentCalls] = useState<CallRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [cList, calls] = await Promise.all([
          api.getContacts(),
          api.getCallHistory(),
        ]);
        setContacts(cList);
        setRecentCalls(calls.slice(0, 5));
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const handleCreateMeeting = async () => {
    try {
      const mtg = await api.createMeeting(`${user?.profile?.display_name || user?.username}'s Meeting`);
      navigate(`/meetings?code=${mtg.meeting_code}`);
    } catch (err: any) {
      alert(`Could not start meeting: ${err.message}`);
    }
  };

  return (
    <div className="p-6 md:p-10 max-w-7xl mx-auto flex flex-col gap-8">
      {/* Welcome Banner */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-violet-900/60 via-indigo-900/40 to-slate-900 border border-violet-500/20 p-8 shadow-2xl backdrop-blur-xl">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-500/20 text-violet-300 text-xs font-semibold mb-4 border border-violet-500/30">
            <Sparkles size={14} /> Private Family & Public Calling Platform
          </div>
          <h1 className="text-3xl md:text-4xl font-extrabold text-white tracking-tight">
            Welcome back, {user?.profile?.display_name || user?.username}
          </h1>
          <p className="mt-2 text-slate-300 text-sm md:text-base leading-relaxed">
            Experience ultra-low-latency HD audio/video calling, group conferencing, and private instant messaging.
          </p>

          <div className="flex flex-wrap items-center gap-4 mt-6">
            <button
              onClick={handleCreateMeeting}
              className="px-6 py-3 rounded-2xl bg-violet-600 hover:bg-violet-700 active:scale-95 transition-all text-white font-semibold text-sm flex items-center gap-2.5 shadow-lg shadow-violet-600/30"
            >
              <Video size={18} /> Instant Meeting
            </button>
            <button
              onClick={() => navigate('/contacts')}
              className="px-6 py-3 rounded-2xl bg-slate-800/80 hover:bg-slate-800 text-slate-200 hover:text-white font-semibold text-sm flex items-center gap-2.5 border border-slate-700 transition-all"
            >
              <Users size={18} /> Discover Contacts
            </button>
          </div>
        </div>
      </div>

      {/* Quick Action Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div
          onClick={() => navigate('/chats')}
          className="group cursor-pointer p-6 rounded-3xl bg-slate-900/70 border border-slate-800 hover:border-violet-500/50 hover:bg-slate-900 transition-all duration-300 shadow-xl flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-4">
            <div className="w-12 h-12 rounded-2xl bg-blue-600/20 text-blue-400 flex items-center justify-center border border-blue-500/30 group-hover:scale-110 transition-transform">
              <MessageSquare size={22} />
            </div>
            <ArrowUpRight size={18} className="text-slate-500 group-hover:text-blue-400 transition-colors" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white mb-1">Messages</h3>
            <p className="text-xs text-slate-400">Direct & group private messaging with attachments and delivery state.</p>
          </div>
        </div>

        <div
          onClick={handleCreateMeeting}
          className="group cursor-pointer p-6 rounded-3xl bg-slate-900/70 border border-slate-800 hover:border-violet-500/50 hover:bg-slate-900 transition-all duration-300 shadow-xl flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-4">
            <div className="w-12 h-12 rounded-2xl bg-violet-600/20 text-violet-400 flex items-center justify-center border border-violet-500/30 group-hover:scale-110 transition-transform">
              <Video size={22} />
            </div>
            <ArrowUpRight size={18} className="text-slate-500 group-hover:text-violet-400 transition-colors" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white mb-1">Group Meetings</h3>
            <p className="text-xs text-slate-400">Host meetings with shareable code, waiting rooms, and screen share.</p>
          </div>
        </div>

        <div
          onClick={() => navigate('/calls')}
          className="group cursor-pointer p-6 rounded-3xl bg-slate-900/70 border border-slate-800 hover:border-violet-500/50 hover:bg-slate-900 transition-all duration-300 shadow-xl flex flex-col justify-between"
        >
          <div className="flex items-center justify-between mb-4">
            <div className="w-12 h-12 rounded-2xl bg-emerald-600/20 text-emerald-400 flex items-center justify-center border border-emerald-500/30 group-hover:scale-110 transition-transform">
              <Phone size={22} />
            </div>
            <ArrowUpRight size={18} className="text-slate-500 group-hover:text-emerald-400 transition-colors" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white mb-1">Call Logs</h3>
            <p className="text-xs text-slate-400">Review incoming, outgoing, and missed WebRTC audio/video calls.</p>
          </div>
        </div>
      </div>

      {/* Two Columns: Online Contacts & Recent Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Contacts */}
        <div className="p-6 rounded-3xl bg-slate-900/70 border border-slate-800 shadow-xl">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2">
              <Users size={18} className="text-violet-400" />
              <h2 className="text-base font-bold text-white">Your Family & Contacts</h2>
            </div>
            <button
              onClick={() => navigate('/contacts')}
              className="text-xs text-violet-400 hover:text-violet-300 font-semibold"
            >
              View All ({contacts.length})
            </button>
          </div>

          {contacts.length === 0 ? (
            <div className="py-8 text-center text-slate-500 text-xs">
              No contacts yet. Search usernames to add family members and friends.
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {contacts.map((contact) => (
                <div
                  key={contact.id}
                  className="flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800/80 hover:border-slate-700 transition-all"
                >
                  <div className="flex items-center gap-3">
                    <div className="relative">
                      <div className="w-10 h-10 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center font-bold text-violet-300 overflow-hidden">
                        {contact.avatar_url ? (
                          <img src={contact.avatar_url} alt="" className="w-full h-full object-cover" />
                        ) : (
                          contact.display_name.charAt(0).toUpperCase()
                        )}
                      </div>
                      <span
                        className={`absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-slate-900 ${
                          contact.is_online ? 'bg-emerald-500' : 'bg-slate-600'
                        }`}
                      />
                    </div>
                    <div>
                      <h4 className="text-sm font-semibold text-white">{contact.display_name}</h4>
                      <p className="text-xs text-slate-400">@{contact.username}</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => startCall(contact.contact_user_id, 'video')}
                      className="p-2.5 rounded-xl bg-violet-600/20 hover:bg-violet-600 text-violet-400 hover:text-white transition-colors"
                      title="Video Call"
                    >
                      <Video size={16} />
                    </button>
                    <button
                      onClick={() => startCall(contact.contact_user_id, 'voice')}
                      className="p-2.5 rounded-xl bg-emerald-600/20 hover:bg-emerald-600 text-emerald-400 hover:text-white transition-colors"
                      title="Voice Call"
                    >
                      <Phone size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Recent Calls */}
        <div className="p-6 rounded-3xl bg-slate-900/70 border border-slate-800 shadow-xl">
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2">
              <Clock size={18} className="text-violet-400" />
              <h2 className="text-base font-bold text-white">Recent Calls</h2>
            </div>
            <button
              onClick={() => navigate('/calls')}
              className="text-xs text-violet-400 hover:text-violet-300 font-semibold"
            >
              All Calls
            </button>
          </div>

          {recentCalls.length === 0 ? (
            <div className="py-8 text-center text-slate-500 text-xs">
              No recent calls recorded.
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {recentCalls.map((call) => {
                const isCaller = call.caller_id === user?.id;
                const otherName = isCaller
                  ? call.receiver_display_name || call.receiver_username || 'Participant'
                  : call.caller_display_name || call.caller_username || 'Participant';

                return (
                  <div
                    key={call.id}
                    className="flex items-center justify-between p-3 rounded-2xl bg-slate-950/60 border border-slate-800/80"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-2xl bg-slate-800 flex items-center justify-center text-slate-300">
                        {call.call_type === 'video' ? <Video size={18} /> : <Phone size={18} />}
                      </div>
                      <div>
                        <h4 className="text-sm font-semibold text-white">{otherName}</h4>
                        <p className="text-xs text-slate-400 capitalize">
                          {isCaller ? 'Outgoing' : 'Incoming'} • {call.status} • {call.duration_seconds}s
                        </p>
                      </div>
                    </div>
                    <span className="text-xs text-slate-500">
                      {new Date(call.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

