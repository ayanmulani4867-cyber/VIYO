import React, { useState, useEffect } from 'react';
import { useAuth } from '../store/authContext';
import { useCall } from '../store/callContext';
import { api } from '../services/api';
import { Contact, ContactRequest } from '../types';
import {
  Users,
  UserPlus,
  Search,
  Check,
  X,
  Phone,
  Video,
  ShieldBan,
  Clock,
} from 'lucide-react';

export const ContactsPage: React.FC = () => {
  const { user } = useAuth();
  const { startCall } = useCall();

  const [activeTab, setActiveTab] = useState<'contacts' | 'pending' | 'discover'>('contacts');
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [pendingRequests, setPendingRequests] = useState<ContactRequest[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [searching, setSearching] = useState(false);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  const loadData = async () => {
    try {
      const [cList, pList] = await Promise.all([
        api.getContacts(),
        api.getPendingRequests(),
      ]);
      setContacts(cList);
      setPendingRequests(pList);
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setSearching(true);
    try {
      const results = await api.searchUsers(searchQuery);
      setSearchResults(results);
    } catch (err: any) {
      alert(`Search failed: ${err.message}`);
    } finally {
      setSearching(false);
    }
  };

  const sendRequest = async (username: string) => {
    try {
      await api.sendContactRequest(username);
      setActionMsg(`Request sent to @${username}`);
      setTimeout(() => setActionMsg(null), 3000);
    } catch (err: any) {
      alert(`Could not send request: ${err.message}`);
    }
  };

  const acceptRequest = async (requestId: string) => {
    try {
      await api.acceptContactRequest(requestId);
      loadData();
    } catch (err: any) {
      alert(`Error accepting request: ${err.message}`);
    }
  };

  const rejectRequest = async (requestId: string) => {
    try {
      await api.rejectContactRequest(requestId);
      loadData();
    } catch (err: any) {
      alert(`Error rejecting request: ${err.message}`);
    }
  };

  const removeContact = async (userId: string) => {
    if (!confirm('Are you sure you want to remove this contact?')) return;
    try {
      await api.removeContact(userId);
      loadData();
    } catch (err: any) {
      alert(`Error removing contact: ${err.message}`);
    }
  };

  return (
    <div className="p-6 md:p-10 max-w-5xl mx-auto flex flex-col gap-6">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold text-white">Contacts & Directory</h1>
        <p className="text-xs text-slate-400 mt-1">
          Connect with approved family members and manage your private contact network.
        </p>
      </div>

      {actionMsg && (
        <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 rounded-xl text-xs">
          {actionMsg}
        </div>
      )}

      {/* Tabs */}
      <div className="flex bg-slate-900 p-1.5 rounded-2xl border border-slate-800 w-full max-w-md">
        <button
          onClick={() => setActiveTab('contacts')}
          className={`flex-1 py-2 text-xs md:text-sm font-semibold rounded-xl transition-all ${
            activeTab === 'contacts' ? 'bg-violet-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
          }`}
        >
          My Contacts ({contacts.length})
        </button>
        <button
          onClick={() => setActiveTab('pending')}
          className={`flex-1 py-2 text-xs md:text-sm font-semibold rounded-xl transition-all relative ${
            activeTab === 'pending' ? 'bg-violet-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
          }`}
        >
          Requests ({pendingRequests.length})
          {pendingRequests.length > 0 && (
            <span className="ml-1.5 px-1.5 py-0.5 rounded-full bg-rose-500 text-[10px] text-white font-bold">
              {pendingRequests.length}
            </span>
          )}
        </button>
        <button
          onClick={() => setActiveTab('discover')}
          className={`flex-1 py-2 text-xs md:text-sm font-semibold rounded-xl transition-all ${
            activeTab === 'discover' ? 'bg-violet-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
          }`}
        >
          Discover
        </button>
      </div>

      {/* Tab 1: Contacts List */}
      {activeTab === 'contacts' && (
        <div className="rounded-3xl bg-slate-900/70 border border-slate-800 overflow-hidden shadow-xl">
          {contacts.length === 0 ? (
            <div className="py-16 text-center text-slate-500 text-sm">
              No contacts in your network yet. Switch to "Discover" to find and add members.
            </div>
          ) : (
            <div className="divide-y divide-slate-800/80">
              {contacts.map((c) => (
                <div key={c.id} className="p-4 md:p-5 flex items-center justify-between hover:bg-slate-850/50">
                  <div className="flex items-center gap-3.5">
                    <div className="relative">
                      <div className="w-12 h-12 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center font-bold text-violet-300 text-lg overflow-hidden">
                        {c.avatar_url ? (
                          <img src={c.avatar_url} alt="" className="w-full h-full object-cover" />
                        ) : (
                          c.display_name.charAt(0).toUpperCase()
                        )}
                      </div>
                      <span
                        className={`absolute bottom-0 right-0 w-3.5 h-3.5 rounded-full border-2 border-slate-900 ${
                          c.is_online ? 'bg-emerald-500' : 'bg-slate-600'
                        }`}
                      />
                    </div>

                    <div>
                      <h4 className="text-sm font-bold text-white">{c.display_name}</h4>
                      <p className="text-xs text-slate-400">@{c.username}</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => startCall(c.contact_user_id, 'video')}
                      className="p-2.5 rounded-xl bg-violet-600/20 hover:bg-violet-600 text-violet-400 hover:text-white transition-colors"
                      title="Video Call"
                    >
                      <Video size={16} />
                    </button>
                    <button
                      onClick={() => startCall(c.contact_user_id, 'voice')}
                      className="p-2.5 rounded-xl bg-emerald-600/20 hover:bg-emerald-600 text-emerald-400 hover:text-white transition-colors"
                      title="Voice Call"
                    >
                      <Phone size={16} />
                    </button>
                    <button
                      onClick={() => removeContact(c.contact_user_id)}
                      className="p-2.5 rounded-xl text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
                      title="Remove Contact"
                    >
                      <X size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Pending Requests */}
      {activeTab === 'pending' && (
        <div className="rounded-3xl bg-slate-900/70 border border-slate-800 overflow-hidden shadow-xl">
          {pendingRequests.length === 0 ? (
            <div className="py-16 text-center text-slate-500 text-sm">
              No pending contact requests.
            </div>
          ) : (
            <div className="divide-y divide-slate-800/80">
              {pendingRequests.map((req) => (
                <div key={req.id} className="p-4 md:p-5 flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-bold text-white">{req.sender_display_name || req.sender_username}</h4>
                    <p className="text-xs text-slate-400">@{req.sender_username} requested to connect</p>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => acceptRequest(req.id)}
                      className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold flex items-center gap-1.5 shadow-md shadow-emerald-600/20"
                    >
                      <Check size={14} /> Accept
                    </button>
                    <button
                      onClick={() => rejectRequest(req.id)}
                      className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold flex items-center gap-1.5"
                    >
                      <X size={14} /> Decline
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 3: Discover Directory */}
      {activeTab === 'discover' && (
        <div className="flex flex-col gap-6">
          <form onSubmit={handleSearch} className="flex gap-2 max-w-md">
            <div className="relative flex-1">
              <Search size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search username or display name..."
                className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500"
              />
            </div>
            <button
              type="submit"
              disabled={searching || !searchQuery.trim()}
              className="px-5 py-2.5 rounded-xl bg-violet-600 hover:bg-violet-700 text-white text-sm font-semibold shadow-md shadow-violet-600/30 disabled:opacity-50"
            >
              Search
            </button>
          </form>

          {searchResults.length > 0 && (
            <div className="rounded-3xl bg-slate-900/70 border border-slate-800 overflow-hidden shadow-xl divide-y divide-slate-800/80">
              {searchResults.map((userResult) => (
                <div key={userResult.id} className="p-4 md:p-5 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-slate-800 flex items-center justify-center font-bold text-violet-300 border border-slate-700">
                      {userResult.display_name.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-white">{userResult.display_name}</h4>
                      <p className="text-xs text-slate-400">@{userResult.username}</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => sendRequest(userResult.username)}
                      className="px-4 py-2 rounded-xl bg-violet-600/20 hover:bg-violet-600 text-violet-300 hover:text-white text-xs font-semibold flex items-center gap-1.5 transition-colors"
                    >
                      <UserPlus size={14} /> Connect
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

