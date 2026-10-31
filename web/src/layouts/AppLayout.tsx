import React, { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/authContext';
import {
  Home,
  MessageSquare,
  Phone,
  Video,
  Users,
  User,
  Settings,
  LogOut,
  Menu,
  X,
  ShieldAlert,
} from 'lucide-react';
import { IncomingCallDialog } from '../components/IncomingCallDialog';
import { ActiveCallModal } from '../components/ActiveCallModal';

export const AppLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const navItems = [
    { name: 'Home', path: '/', icon: Home },
    { name: 'Chats', path: '/chats', icon: MessageSquare },
    { name: 'Calls', path: '/calls', icon: Phone },
    { name: 'Meetings', path: '/meetings', icon: Video },
    { name: 'Contacts', path: '/contacts', icon: Users },
    { name: 'Profile', path: '/profile', icon: User },
    { name: 'Settings', path: '/settings', icon: Settings },
  ];

  const handleLogout = async () => {
    await logout();
    navigate('/auth');
  };

  return (
    <div className="flex h-screen w-full bg-[#0B0F19] overflow-hidden">
      {/* Global Real-Time Call Components */}
      <IncomingCallDialog />
      <ActiveCallModal />

      {/* Desktop Navigation Sidebar */}
      <aside className="hidden md:flex flex-col w-64 border-r border-slate-800/80 bg-slate-950/60 backdrop-blur-xl p-4 justify-between">
        <div className="flex flex-col gap-6">
          {/* Logo & Branding */}
          <div className="flex items-center gap-3 px-2 pt-2">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-violet-600 to-blue-600 flex items-center justify-center shadow-lg shadow-violet-600/30">
              <span className="font-extrabold text-white text-xl tracking-tight">V</span>
            </div>
            <div>
              <h1 className="font-bold text-lg text-white tracking-wider leading-none">VIYO</h1>
              <p className="text-[10px] text-violet-400 font-medium tracking-widest mt-1 uppercase">See. Talk. Connect.</p>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="flex flex-col gap-1.5 mt-2">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) =>
                    `flex items-center gap-3.5 px-3.5 py-2.5 rounded-xl text-sm font-medium transition-all ${
                      isActive
                        ? 'bg-violet-600 text-white shadow-lg shadow-violet-600/20'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/60'
                    }`
                  }
                >
                  <Icon size={18} />
                  <span>{item.name}</span>
                </NavLink>
              );
            })}
          </nav>
        </div>

        {/* User Card & Logout */}
        <div className="flex flex-col gap-3 pt-4 border-t border-slate-800/80">
          <div className="flex items-center justify-between px-2">
            <div className="flex items-center gap-3 overflow-hidden">
              <div className="w-9 h-9 rounded-full bg-slate-800 border border-violet-500/30 flex items-center justify-center font-bold text-violet-300 overflow-hidden flex-shrink-0">
                {user?.profile?.avatar_url ? (
                  <img src={user.profile.avatar_url} alt="" className="w-full h-full object-cover" />
                ) : (
                  user?.username?.charAt(0).toUpperCase() || 'U'
                )}
              </div>
              <div className="truncate">
                <p className="text-sm font-semibold text-white truncate">
                  {user?.profile?.display_name || user?.username}
                </p>
                <p className="text-xs text-slate-400 truncate">@{user?.username}</p>
              </div>
            </div>

            <button
              onClick={handleLogout}
              className="p-2 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition-colors"
              title="Logout"
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>

      {/* Mobile Top Header */}
      <div className="md:hidden fixed top-0 inset-x-0 z-40 bg-slate-950/90 border-b border-slate-800 px-4 py-3 flex items-center justify-between backdrop-blur-md">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-violet-600 to-blue-600 flex items-center justify-center font-bold text-white text-base">
            V
          </div>
          <span className="font-bold text-white">VIYO</span>
        </div>
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="p-2 text-slate-300 hover:text-white"
        >
          {mobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {/* Mobile Drawer */}
      {mobileMenuOpen && (
        <div className="md:hidden fixed inset-0 z-30 bg-slate-950 pt-16 p-6 flex flex-col justify-between">
          <nav className="flex flex-col gap-2">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.path}
                  to={item.path}
                  onClick={() => setMobileMenuOpen(false)}
                  className={({ isActive }) =>
                    `flex items-center gap-4 px-4 py-3 rounded-xl text-base font-medium ${
                      isActive ? 'bg-violet-600 text-white' : 'text-slate-400'
                    }`
                  }
                >
                  <Icon size={20} />
                  <span>{item.name}</span>
                </NavLink>
              );
            })}
          </nav>
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 px-4 py-3 rounded-xl text-rose-400 bg-rose-500/10 font-medium"
          >
            <LogOut size={20} /> Sign Out
          </button>
        </div>
      )}

      {/* Main Content Area */}
      <main className="flex-1 h-full overflow-y-auto pt-14 md:pt-0">
        <Outlet />
      </main>
    </div>
  );
};

