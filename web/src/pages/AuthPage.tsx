import React, { useState } from 'react';
import { useAuth } from '../store/authContext';
import { useNavigate } from 'react-router-dom';
import { Video, ShieldCheck, Mail, Lock, User as UserIcon, ArrowRight } from 'lucide-react';

export const AuthPage: React.FC = () => {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [isLogin, setIsLogin] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [form, setForm] = useState({
    email: '',
    username: '',
    password: '',
    display_name: '',
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      if (isLogin) {
        await login({
          username_or_email: form.username || form.email,
          password: form.password,
        });
      } else {
        await register(form);
      }
      navigate('/');
    } catch (err: any) {
      setError(err.message || 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen w-full flex items-center justify-center p-4 bg-gradient-to-br from-[#0B0F19] via-[#120F26] to-[#0B0F19]">
      <div className="w-full max-w-md bg-slate-900/90 border border-slate-800 rounded-3xl p-8 backdrop-blur-xl shadow-2xl">
        {/* Brand Header */}
        <div className="flex flex-col items-center text-center mb-8">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-violet-600 to-blue-600 flex items-center justify-center shadow-xl shadow-violet-600/30 mb-3">
            <Video size={28} className="text-white" />
          </div>
          <h1 className="text-2xl font-extrabold text-white tracking-wide">VIYO</h1>
          <p className="text-xs uppercase tracking-widest text-violet-400 mt-1 font-semibold">
            See. Talk. Connect.
          </p>
        </div>

        {/* Tab switch */}
        <div className="flex bg-slate-950 p-1.5 rounded-2xl mb-6 border border-slate-800">
          <button
            type="button"
            onClick={() => { setIsLogin(true); setError(null); }}
            className={`flex-1 py-2 text-sm font-semibold rounded-xl transition-all ${
              isLogin ? 'bg-violet-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setIsLogin(false); setError(null); }}
            className={`flex-1 py-2 text-sm font-semibold rounded-xl transition-all ${
              !isLogin ? 'bg-violet-600 text-white shadow-md' : 'text-slate-400 hover:text-white'
            }`}
          >
            Create Account
          </button>
        </div>

        {error && (
          <div className="p-3 mb-6 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs text-center font-medium">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {!isLogin && (
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1.5">Full Name</label>
              <div className="relative">
                <UserIcon size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
                <input
                  type="text"
                  name="display_name"
                  required
                  value={form.display_name}
                  onChange={handleChange}
                  placeholder="Your Name"
                  className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-10 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500 transition-colors"
                />
              </div>
            </div>
          )}

          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1.5">
              {isLogin ? 'Username or Email' : 'Email Address'}
            </label>
            <div className="relative">
              <Mail size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type={isLogin ? 'text' : 'email'}
                name={isLogin ? 'username' : 'email'}
                required
                value={isLogin ? form.username : form.email}
                onChange={handleChange}
                placeholder={isLogin ? 'username or user@domain.com' : 'user@domain.com'}
                className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-10 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500 transition-colors"
              />
            </div>
          </div>

          {!isLogin && (
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1.5">Username</label>
              <div className="relative">
                <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500 text-sm font-semibold">@</span>
                <input
                  type="text"
                  name="username"
                  required
                  value={form.username}
                  onChange={handleChange}
                  placeholder="unique_handle"
                  className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-10 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500 transition-colors"
                />
              </div>
            </div>
          )}

          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1.5">Password</label>
            <div className="relative">
              <Lock size={18} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="password"
                name="password"
                required
                value={form.password}
                onChange={handleChange}
                placeholder="••••••••"
                className="w-full bg-slate-950/80 border border-slate-800 rounded-xl px-10 py-2.5 text-sm text-white focus:outline-none focus:border-violet-500 transition-colors"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full mt-3 py-3 rounded-xl bg-violet-600 hover:bg-violet-700 active:scale-[0.99] transition-all text-white font-semibold text-sm flex items-center justify-center gap-2 shadow-lg shadow-violet-600/30 disabled:opacity-50"
          >
            {loading ? 'Processing...' : isLogin ? 'Sign In' : 'Create Account'}
            {!loading && <ArrowRight size={16} />}
          </button>
        </form>

        <div className="mt-8 pt-6 border-t border-slate-800/80 flex items-center justify-center gap-2 text-xs text-slate-500">
          <ShieldCheck size={14} className="text-violet-400" />
          <span>Encrypted WebRTC Media & Isolated Data</span>
        </div>
      </div>
    </div>
  );
};

