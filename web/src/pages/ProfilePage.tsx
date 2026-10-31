import React, { useState } from 'react';
import { useAuth } from '../store/authContext';
import { api } from '../services/api';
import { Camera, Check, User, Save } from 'lucide-react';

export const ProfilePage: React.FC = () => {
  const { user, refreshUser } = useAuth();
  const [displayName, setDisplayName] = useState(user?.profile?.display_name || '');
  const [bio, setBio] = useState(user?.profile?.bio || '');
  const [statusMessage, setStatusMessage] = useState(user?.profile?.status_message || '');
  const [loading, setLoading] = useState(false);
  const [avatarLoading, setAvatarLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setSuccess(false);
    try {
      await api.updateProfile({
        display_name: displayName,
        bio,
        status_message: statusMessage,
      });
      await refreshUser();
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (err: any) {
      alert(`Update failed: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setAvatarLoading(true);
    try {
      await api.uploadAvatar(file);
      await refreshUser();
    } catch (err: any) {
      alert(`Avatar upload failed: ${err.message}`);
    } finally {
      setAvatarLoading(false);
    }
  };

  return (
    <div className="p-6 md:p-10 max-w-3xl mx-auto flex flex-col gap-8">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold text-white">Your Profile</h1>
        <p className="text-xs text-slate-400 mt-1">
          Customize your appearance and personal details across the VIYO family platform.
        </p>
      </div>

      <div className="p-8 rounded-3xl bg-slate-900/80 border border-slate-800 shadow-2xl flex flex-col gap-8">
        {/* Avatar Upload */}
        <div className="flex items-center gap-6">
          <div className="relative group">
            <div className="w-24 h-24 rounded-full bg-slate-800 border-2 border-violet-500/40 flex items-center justify-center font-bold text-violet-300 text-3xl overflow-hidden shadow-lg">
              {user?.profile?.avatar_url ? (
                <img src={user.profile.avatar_url} alt="" className="w-full h-full object-cover" />
              ) : (
                user?.username?.charAt(0).toUpperCase() || 'U'
              )}
            </div>
            <label className="absolute inset-0 rounded-full bg-black/60 flex flex-col items-center justify-center text-white text-xs opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer">
              <Camera size={20} className="mb-1" />
              <span>{avatarLoading ? 'Uploading...' : 'Change'}</span>
              <input type="file" accept="image/*" onChange={handleAvatarChange} className="hidden" />
            </label>
          </div>

          <div>
            <h3 className="text-lg font-bold text-white">{user?.profile?.display_name || user?.username}</h3>
            <p className="text-xs text-slate-400">@{user?.username} • {user?.email}</p>
            <p className="text-xs text-emerald-400 font-medium mt-1">● Account Active</p>
          </div>
        </div>

        {success && (
          <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 rounded-xl text-xs flex items-center gap-2">
            <Check size={16} /> Profile updated successfully!
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1.5">Display Name</label>
            <input
              type="text"
              required
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1.5">Status Message</label>
            <input
              type="text"
              value={statusMessage}
              onChange={(e) => setStatusMessage(e.target.value)}
              placeholder="e.g. Working remotely / Ready for calls"
              className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500"
            />
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1.5">Bio</label>
            <textarea
              rows={3}
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="Tell other family members a bit about yourself..."
              className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500 resize-none"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="self-start px-6 py-3 rounded-xl bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm flex items-center gap-2 shadow-lg shadow-violet-600/30 disabled:opacity-50"
          >
            <Save size={16} /> {loading ? 'Saving...' : 'Save Changes'}
          </button>
        </form>
      </div>
    </div>
  );
};

