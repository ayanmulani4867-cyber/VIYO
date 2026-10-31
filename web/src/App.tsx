import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './store/authContext';
import { CallProvider } from './store/callContext';

import { AppLayout } from './layouts/AppLayout';
import { AuthPage } from './pages/AuthPage';
import { HomePage } from './pages/HomePage';
import { ChatsPage } from './pages/ChatsPage';
import { CallsPage } from './pages/CallsPage';
import { MeetingsPage } from './pages/MeetingsPage';
import { ContactsPage } from './pages/ContactsPage';
import { ProfilePage } from './pages/ProfilePage';
import { SettingsPage } from './pages/SettingsPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="h-screen w-full flex items-center justify-center bg-[#0B0F19]">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-violet-600 to-blue-600 flex items-center justify-center font-bold text-white text-xl animate-pulse">
            V
          </div>
          <p className="text-xs font-semibold text-violet-400 tracking-widest uppercase">Connecting to VIYO...</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/auth" replace />;
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CallProvider>
          <Routes>
            <Route path="/auth" element={<AuthPage />} />

            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <AppLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<HomePage />} />
              <Route path="chats" element={<ChatsPage />} />
              <Route path="calls" element={<CallsPage />} />
              <Route path="meetings" element={<MeetingsPage />} />
              <Route path="contacts" element={<ContactsPage />} />
              <Route path="profile" element={<ProfilePage />} />
              <Route path="settings" element={<SettingsPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </CallProvider>
      </AuthProvider>
    </BrowserRouter>
  );
};

