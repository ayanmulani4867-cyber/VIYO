import React, { useState, useEffect } from 'react';
import { useAuth } from '../store/authContext';
import { api } from '../services/api';
import { Shield, Bell, Video, Mic, Check, BarChart3, Database } from 'lucide-react';

export const SettingsPage: React.FC = () => {
  const { user, refreshUser } = useAuth();

  const [privacyLastSeen, setPrivacyLastSeen] = useState(user?.settings?.privacy_last_seen || 'contacts_only');
  const [privacyCalls, setPrivacyCalls] = useState(user?.settings?.privacy_calls || 'everyone');
  const [allowCalls, setAllowCalls] = useState(user?.settings?.allow_incoming_calls ?? true);
  const [cameraDefault, setCameraDefault] = useState(user?.settings?.camera_enabled_by_default ?? true);
  const [micDefault, setMicDefault] = useState(user?.settings?.microphone_enabled_by_default ?? true);
  const [pushEnabled, setPushEnabled] = useState(user?.settings?.push_notifications_enabled ?? true);
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (user?.is_admin) {
      api.getStats().then(setStats).catch(console.error);
    }
  }, [user?.is_admin]);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setSaved(false);
    try {
      await api.updateSettings({
        privacy_last_seen: privacyLastSeen,
        privacy_calls: privacyCalls,
        allow_incoming_calls: allowCalls,
        camera_enabled_by_default: cameraDefault,
        microphone_enabled_by_default: micDefault,
        push_notifications_enabled: pushEnabled,
      });
      await refreshUser();
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err: any) {
      alert(`Settings update failed: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-6 md:p-10 max-w-4xl mx-auto flex flex-col gap-8">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold text-white">Settings & Privacy</h1>
        <p className="text-xs text-slate-400 mt-1">
          Manage your presence visibility, call privacy, audio/video preferences, and administrative tools.
        </p>
      </div>

      {saved && (
        <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 rounded-xl text-xs flex items-center gap-2">
          <Check size={16} /> Settings saved successfully!
        </div>
      )}

      <form onSubmit={handleSave} className="flex flex-col gap-8">
        {/* Privacy Section */}
        <div className="p-6 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-xl flex flex-col gap-6">
          <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
            <Shield size={20} className="text-violet-400" />
            <h3 className="font-bold text-white text-base">Privacy & Visibility</h3>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-2">Who can see my last seen & online status?</label>
              <select
                value={privacyLastSeen}
                onChange={(e: any) => setPrivacyLastSeen(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500"
              >
                <option value="everyone">Everyone</option>
                <option value="contacts_only">Contacts Only</option>
                <option value="nobody">Nobody</option>
              </select>
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-2">Who can call me?</label>
              <select
                value={privacyCalls}
                onChange={(e: any) => setPrivacyCalls(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500"
              >
                <option value="everyone">Everyone</option>
                <option value="contacts_only">Contacts Only</option>
                <option value="nobody">Nobody</option>
              </select>
            </div>
          </div>

          <label className="flex items-center gap-3 cursor-pointer pt-2">
            <input
              type="checkbox"
              checked={allowCalls}
              onChange={(e) => setAllowCalls(e.target.checked)}
              className="rounded border-slate-700 bg-slate-950 text-violet-600 focus:ring-0"
            />
            <span className="text-xs md:text-sm text-slate-300">Allow incoming calls on this account</span>
          </label>
        </div>

        {/* Media Defaults */}
        <div className="p-6 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-xl flex flex-col gap-6">
          <div className="flex items-center gap-3 border-b border-slate-800 pb-4">
            <Video size={20} className="text-violet-400" />
            <h3 className="font-bold text-white text-base">Media & Device Defaults</h3>
          </div>

          <div className="flex flex-col gap-4">
            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={cameraDefault}
                onChange={(e) => setCameraDefault(e.target.checked)}
                className="rounded border-slate-700 bg-slate-950 text-violet-600 focus:ring-0"
              />
              <span className="text-xs md:text-sm text-slate-300">Enable camera automatically when joining a call</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={micDefault}
                onChange={(e) => setMicDefault(e.target.checked)}
                className="rounded border-slate-700 bg-slate-950 text-violet-600 focus:ring-0"
              />
              <span className="text-xs md:text-sm text-slate-300">Enable microphone automatically when joining a call</span>
            </label>

            <label className="flex items-center gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={pushEnabled}
                onChange={(e) => setPushEnabled(e.target.checked)}
                className="rounded border-slate-700 bg-slate-950 text-violet-600 focus:ring-0"
              />
              <span className="text-xs md:text-sm text-slate-300">Enable browser notifications for calls and messages</span>
            </label>
          </div>
        </div>

        <button
          type="submit"
          disabled={loading}
          className="self-start px-6 py-3 rounded-xl bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm shadow-lg shadow-violet-600/30 disabled:opacity-50"
        >
          {loading ? 'Saving...' : 'Save Settings'}
        </button>
      </form>

      {/* Admin Analytics / Platform Health (Only for admin) */}
      {user?.is_admin && stats && (
        <div className="p-6 rounded-3xl bg-slate-900/80 border border-violet-500/30 shadow-xl flex flex-col gap-6 mt-4">
          <div className="flex items-center justify-between border-b border-slate-800 pb-4">
            <div className="flex items-center gap-3">
              <BarChart3 size={20} className="text-violet-400" />
              <h3 className="font-bold text-white text-base">Administrative Dashboard & Platform Metrics</h3>
            </div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-violet-400 bg-violet-500/20 px-2.5 py-1 rounded-full">
              Admin Access
            </span>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
              <p className="text-xs text-slate-400">Total Users</p>
              <p className="text-2xl font-extrabold text-white mt-1">{stats.total_users}</p>
            </div>
            <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
              <p className="text-xs text-slate-400">Total Calls</p>
              <p className="text-2xl font-extrabold text-emerald-400 mt-1">{stats.total_calls}</p>
            </div>
            <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
              <p className="text-xs text-slate-400">Total Messages</p>
              <p className="text-2xl font-extrabold text-blue-400 mt-1">{stats.total_messages}</p>
            </div>
            <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800">
              <p className="text-xs text-slate-400">Meetings Hosted</p>
              <p className="text-2xl font-extrabold text-violet-400 mt-1">{stats.total_meetings}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

