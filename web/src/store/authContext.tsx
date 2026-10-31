import React, { createContext, useContext, useState, useEffect } from 'react';
import { User } from '../types';
import { api } from '../services/api';
import { wsClient } from '../services/websocket';

interface AuthContextType {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (data: any) => Promise<void>;
  register: (data: any) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem('viyo_token'));
  const [loading, setLoading] = useState<boolean>(true);

  const refreshUser = async () => {
    const curToken = localStorage.getItem('viyo_token');
    if (!curToken) {
      setUser(null);
      setLoading(false);
      return;
    }
    try {
      const userData = await api.getMe();
      setUser(userData);
      wsClient.connect(curToken);
    } catch (err) {
      console.warn('Session expired or invalid:', err);
      localStorage.removeItem('viyo_token');
      setToken(null);
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshUser();
  }, []);

  const login = async (credentials: any) => {
    const res = await api.login(credentials);
    localStorage.setItem('viyo_token', res.access_token);
    setToken(res.access_token);
    wsClient.connect(res.access_token);
    const userData = await api.getMe();
    setUser(userData);
  };

  const register = async (data: any) => {
    const res = await api.register(data);
    localStorage.setItem('viyo_token', res.access_token);
    setToken(res.access_token);
    wsClient.connect(res.access_token);
    const userData = await api.getMe();
    setUser(userData);
  };

  const logout = async () => {
    try {
      await api.logout();
    } catch (e) {
      // Ignored
    }
    wsClient.disconnect();
    localStorage.removeItem('viyo_token');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, loading, login, register, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

