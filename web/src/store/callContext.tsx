import React, { createContext, useContext, useState, useEffect } from 'react';
import { CallRecord } from '../types';
import { api } from '../services/api';
import { wsClient } from '../services/websocket';
import { useAuth } from './authContext';

interface IncomingCallData {
  call_id: string;
  room_name: string;
  call_type: 'video' | 'voice';
  caller: {
    id: string;
    username: string;
    display_name: string;
    avatar_url?: string | null;
  };
}

interface CallContextType {
  incomingCall: IncomingCallData | null;
  activeCall: CallRecord | null;
  startCall: (receiverId: string, callType?: 'video' | 'voice') => Promise<void>;
  acceptCall: () => Promise<void>;
  rejectCall: () => Promise<void>;
  endCall: () => Promise<void>;
}

const CallContext = createContext<CallContextType | undefined>(undefined);

export const CallProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const [incomingCall, setIncomingCall] = useState<IncomingCallData | null>(null);
  const [activeCall, setActiveCall] = useState<CallRecord | null>(null);

  useEffect(() => {
    if (!user) return;

    // Listen to incoming call
    const unsubIncoming = wsClient.on('incoming_call', (data) => {
      setIncomingCall(data);
    });

    // Listen to call accepted by other participant
    const unsubAccepted = wsClient.on('call_accepted', (data) => {
      setActiveCall((prev) => (prev ? { ...prev, status: 'accepted' } : null));
    });

    // Listen to call rejected by other participant
    const unsubRejected = wsClient.on('call_rejected', () => {
      setActiveCall(null);
      setIncomingCall(null);
      alert('Call was declined');
    });

    // Listen to call cancelled
    const unsubCancelled = wsClient.on('call_cancelled', () => {
      setIncomingCall(null);
    });

    // Listen to call ended
    const unsubEnded = wsClient.on('call_ended', () => {
      setActiveCall(null);
      setIncomingCall(null);
    });

    return () => {
      unsubIncoming();
      unsubAccepted();
      unsubRejected();
      unsubCancelled();
      unsubEnded();
    };
  }, [user]);

  const startCall = async (receiverId: string, callType: 'video' | 'voice' = 'video') => {
    try {
      const call = await api.initiateCall(receiverId, callType);
      setActiveCall(call);
    } catch (err: any) {
      alert(`Could not start call: ${err.message}`);
    }
  };

  const acceptCall = async () => {
    if (!incomingCall) return;
    try {
      const call = await api.callAction(incomingCall.call_id, 'accept');
      setActiveCall(call);
      setIncomingCall(null);
    } catch (err: any) {
      alert(`Failed to accept call: ${err.message}`);
      setIncomingCall(null);
    }
  };

  const rejectCall = async () => {
    if (!incomingCall) return;
    try {
      await api.callAction(incomingCall.call_id, 'reject');
    } catch (e) {
      // Ignored
    } finally {
      setIncomingCall(null);
    }
  };

  const endCall = async () => {
    if (!activeCall) return;
    try {
      await api.callAction(activeCall.id, 'end');
    } catch (e) {
      // Ignored
    } finally {
      setActiveCall(null);
    }
  };

  return (
    <CallContext.Provider
      value={{
        incomingCall,
        activeCall,
        startCall,
        acceptCall,
        rejectCall,
        endCall,
      }}
    >
      {children}
    </CallContext.Provider>
  );
};

export const useCall = () => {
  const context = useContext(CallContext);
  if (!context) {
    throw new Error('useCall must be used within a CallProvider');
  }
  return context;
};

