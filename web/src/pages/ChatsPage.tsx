import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../store/authContext';
import { useCall } from '../store/callContext';
import { api } from '../services/api';
import { wsClient } from '../services/websocket';
import { Conversation, Message, Contact } from '../types';
import {
  Send,
  Paperclip,
  Video,
  Phone,
  Image as ImageIcon,
  MessageSquare,
  Search,
  Plus,
  X,
  User,
} from 'lucide-react';

export const ChatsPage: React.FC = () => {
  const { user } = useAuth();
  const { startCall } = useCall();
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConv, setActiveConv] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputText, setInputText] = useState('');
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [showNewModal, setShowNewModal] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [isTyping, setIsTyping] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadConversations = async () => {
    try {
      const data = await api.getConversations();
      setConversations(data);
      if (data.length > 0 && !activeConv) {
        setActiveConv(data[0]);
      }
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => {
    loadConversations();
    api.getContacts().then(setContacts).catch(console.error);
  }, []);

  useEffect(() => {
    if (!activeConv) return;
    api.getMessages(activeConv.id).then(setMessages).catch(console.error);
  }, [activeConv?.id]);

  useEffect(() => {
    // Scroll to bottom on new messages
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    // Listen for incoming WebSocket messages
    const unsub = wsClient.on('new_message', (payload) => {
      const msg = payload.message;
      if (activeConv && msg.conversation_id === activeConv.id) {
        setMessages((prev) => [...prev, msg]);
      }
      // Refresh conversation preview
      loadConversations();
    });

    return () => {
      unsub();
    };
  }, [activeConv?.id]);

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim() || !activeConv) return;

    const text = inputText.trim();
    setInputText('');

    try {
      const sent = await api.sendMessage(activeConv.id, text);
      setMessages((prev) => [...prev, sent]);
      loadConversations();
    } catch (err: any) {
      alert(`Failed to send message: ${err.message}`);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !activeConv) return;

    setUploading(true);
    try {
      const att = await api.uploadAttachment(file);
      const sent = await api.sendMessage(activeConv.id, '', [att.id]);
      setMessages((prev) => [...prev, sent]);
      loadConversations();
    } catch (err: any) {
      alert(`Upload failed: ${err.message}`);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const startDirectChat = async (contactUserId: string) => {
    try {
      const conv = await api.createConversation('direct', [contactUserId]);
      setShowNewModal(false);
      await loadConversations();
      setActiveConv(conv);
    } catch (err: any) {
      alert(`Could not create chat: ${err.message}`);
    }
  };

  const otherMember = activeConv?.members.find((m) => m.user_id !== user?.id);

  return (
    <div className="h-full flex flex-col md:flex-row bg-[#0B0F19] overflow-hidden">
      {/* Left Column: Conversation List */}
      <div className="w-full md:w-80 lg:w-96 border-r border-slate-800 bg-slate-950/40 flex flex-col h-full">
        <div className="p-4 border-b border-slate-800 flex items-center justify-between">
          <h2 className="text-lg font-bold text-white">Conversations</h2>
          <button
            onClick={() => setShowNewModal(true)}
            className="p-2 rounded-xl bg-violet-600/20 text-violet-400 hover:bg-violet-600 hover:text-white transition-colors"
            title="Start New Chat"
          >
            <Plus size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-1.5">
          {conversations.length === 0 ? (
            <div className="py-12 text-center text-slate-500 text-xs">
              No conversations yet. Click + to start a chat with a contact.
            </div>
          ) : (
            conversations.map((conv) => {
              const isSelected = activeConv?.id === conv.id;
              return (
                <div
                  key={conv.id}
                  onClick={() => setActiveConv(conv)}
                  className={`p-3 rounded-2xl cursor-pointer transition-all flex items-center gap-3 ${
                    isSelected
                      ? 'bg-violet-600 text-white shadow-lg shadow-violet-600/20'
                      : 'hover:bg-slate-900/70 text-slate-300'
                  }`}
                >
                  <div className="w-11 h-11 rounded-full bg-slate-800 flex items-center justify-center font-bold text-violet-300 overflow-hidden flex-shrink-0 border border-slate-700">
                    {conv.avatar_url ? (
                      <img src={conv.avatar_url} alt="" className="w-full h-full object-cover" />
                    ) : (
                      conv.title?.charAt(0).toUpperCase() || 'C'
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-0.5">
                      <h4 className="text-sm font-semibold truncate">{conv.title || 'Conversation'}</h4>
                      {conv.last_message && (
                        <span className={`text-[10px] ${isSelected ? 'text-violet-200' : 'text-slate-500'}`}>
                          {new Date(conv.last_message.created_at).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      )}
                    </div>
                    <p className={`text-xs truncate ${isSelected ? 'text-violet-200' : 'text-slate-400'}`}>
                      {conv.last_message?.content || (conv.last_message?.attachments?.length ? '📎 Attachment' : 'No messages yet')}
                    </p>
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* Right Column: Chat Window */}
      {activeConv ? (
        <div className="flex-1 flex flex-col h-full bg-slate-950/20">
          {/* Header with Call Actions */}
          <div className="p-4 border-b border-slate-800 bg-slate-900/60 backdrop-blur-md flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-slate-800 flex items-center justify-center font-bold text-violet-300 border border-slate-700">
                {activeConv.title?.charAt(0).toUpperCase() || 'C'}
              </div>
              <div>
                <h3 className="font-bold text-white text-sm">{activeConv.title}</h3>
                <p className="text-[11px] text-slate-400">
                  {otherMember?.is_online ? (
                    <span className="text-emerald-400 font-medium">● Online</span>
                  ) : (
                    'Offline'
                  )}
                </p>
              </div>
            </div>

            {/* Quick Call Action Buttons */}
            {otherMember && (
              <div className="flex items-center gap-2">
                <button
                  onClick={() => startCall(otherMember.user_id, 'video')}
                  className="p-2.5 rounded-xl bg-violet-600/20 hover:bg-violet-600 text-violet-400 hover:text-white transition-colors"
                  title="Video Call"
                >
                  <Video size={18} />
                </button>
                <button
                  onClick={() => startCall(otherMember.user_id, 'voice')}
                  className="p-2.5 rounded-xl bg-emerald-600/20 hover:bg-emerald-600 text-emerald-400 hover:text-white transition-colors"
                  title="Voice Call"
                >
                  <Phone size={18} />
                </button>
              </div>
            )}
          </div>

          {/* Messages Feed */}
          <div className="flex-1 overflow-y-auto p-4 md:p-6 flex flex-col gap-3">
            {messages.map((msg) => {
              const isMine = msg.sender_id === user?.id;
              return (
                <div
                  key={msg.id}
                  className={`flex flex-col max-w-[80%] md:max-w-md ${
                    isMine ? 'self-end items-end' : 'self-start items-start'
                  }`}
                >
                  {!isMine && (
                    <span className="text-[10px] text-slate-400 font-semibold mb-1 ml-1">
                      {msg.sender_display_name || msg.sender_username}
                    </span>
                  )}
                  <div
                    className={`rounded-2xl px-4 py-2.5 shadow-md ${
                      isMine
                        ? 'bg-violet-600 text-white rounded-br-none'
                        : 'bg-slate-900 border border-slate-800 text-slate-100 rounded-bl-none'
                    }`}
                  >
                    {msg.content && <p className="text-sm whitespace-pre-wrap">{msg.content}</p>}

                    {/* Attachments */}
                    {msg.attachments && msg.attachments.length > 0 && (
                      <div className="flex flex-col gap-2 mt-2">
                        {msg.attachments.map((att) => (
                          <div key={att.id} className="rounded-xl overflow-hidden border border-white/10">
                            {att.mime_type.startsWith('image/') ? (
                              <img src={att.file_url} alt="" className="max-h-60 object-cover w-full" />
                            ) : (
                              <a
                                href={att.file_url}
                                target="_blank"
                                rel="noreferrer"
                                className="flex items-center gap-2 p-2 bg-black/30 text-xs text-violet-200 underline"
                              >
                                <Paperclip size={14} /> {att.file_name}
                              </a>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                  <span className="text-[10px] text-slate-500 mt-1 mr-1">
                    {new Date(msg.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                </div>
              );
            })}
            <div ref={messagesEndRef} />
          </div>

          {/* Bottom Message Input Bar */}
          <form onSubmit={handleSendMessage} className="p-3 md:p-4 border-t border-slate-800 bg-slate-900/60 backdrop-blur-md flex items-center gap-2">
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileUpload}
              className="hidden"
              accept="image/*,audio/*,application/pdf"
            />
            <button
              type="button"
              disabled={uploading}
              onClick={() => fileInputRef.current?.click()}
              className="p-2.5 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
              title="Attach File / Image"
            >
              <Paperclip size={18} />
            </button>

            <input
              type="text"
              value={inputText}
              onChange={(e) => setInputText(e.target.value)}
              placeholder="Type your message..."
              className="flex-1 bg-slate-950/80 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500 transition-colors"
            />

            <button
              type="submit"
              disabled={!inputText.trim() && !uploading}
              className="p-2.5 rounded-xl bg-violet-600 hover:bg-violet-700 disabled:opacity-40 text-white transition-all shadow-md shadow-violet-600/30"
            >
              <Send size={18} />
            </button>
          </form>
        </div>
      ) : (
        <div className="hidden md:flex flex-1 items-center justify-center p-8 text-center text-slate-500">
          <div className="flex flex-col items-center gap-3">
            <div className="w-16 h-16 rounded-2xl bg-slate-900 border border-slate-800 flex items-center justify-center text-slate-400">
              <MessageSquare size={28} />
            </div>
            <p className="text-sm">Select a conversation or start a new chat</p>
          </div>
        </div>
      )}

      {/* New Chat Modal */}
      {showNewModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-3xl p-6 max-w-md w-full shadow-2xl">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-base font-bold text-white">Start New Chat</h3>
              <button onClick={() => setShowNewModal(false)} className="text-slate-400 hover:text-white">
                <X size={18} />
              </button>
            </div>

            <p className="text-xs text-slate-400 mb-4">Select a contact from your family network:</p>
            <div className="flex flex-col gap-2 max-h-60 overflow-y-auto">
              {contacts.length === 0 ? (
                <p className="text-xs text-slate-500 py-4 text-center">No contacts found. Add contacts first.</p>
              ) : (
                contacts.map((c) => (
                  <button
                    key={c.id}
                    onClick={() => startDirectChat(c.contact_user_id)}
                    className="flex items-center justify-between p-3 rounded-xl bg-slate-950/60 hover:bg-slate-800/80 transition-colors text-left"
                  >
                    <div>
                      <p className="text-sm font-semibold text-white">{c.display_name}</p>
                      <p className="text-xs text-slate-400">@{c.username}</p>
                    </div>
                    <span className="text-xs text-violet-400 font-medium">Message</span>
                  </button>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

