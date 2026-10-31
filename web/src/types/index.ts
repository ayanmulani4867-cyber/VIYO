export interface UserProfile {
  display_name: string;
  avatar_url?: string | null;
  bio?: string | null;
  status_message?: string | null;
  is_online: boolean;
  last_seen?: string | null;
}

export interface UserSettings {
  privacy_last_seen: 'everyone' | 'contacts_only' | 'nobody';
  privacy_calls: 'everyone' | 'contacts_only' | 'nobody';
  privacy_profile_photo: string;
  allow_incoming_calls: boolean;
  camera_enabled_by_default: boolean;
  microphone_enabled_by_default: boolean;
  push_notifications_enabled: boolean;
}

export interface User {
  id: string;
  email: string;
  username: string;
  is_active: boolean;
  is_verified: boolean;
  is_admin: boolean;
  created_at: string;
  profile?: UserProfile;
  settings?: UserSettings;
}

export interface Contact {
  id: string;
  contact_user_id: string;
  username: string;
  display_name: string;
  avatar_url?: string | null;
  is_online: boolean;
  last_seen?: string | null;
  nickname?: string | null;
  created_at: string;
}

export interface ContactRequest {
  id: string;
  sender_id: string;
  receiver_id: string;
  status: string;
  created_at: string;
  sender_username?: string;
  sender_display_name?: string;
  sender_avatar_url?: string | null;
}

export interface Attachment {
  id: string;
  file_url: string;
  file_name: string;
  file_size_bytes: number;
  mime_type: string;
  duration_seconds?: number | null;
  created_at: string;
}

export interface Message {
  id: string;
  conversation_id: string;
  sender_id?: string | null;
  sender_username?: string | null;
  sender_display_name?: string | null;
  content?: string | null;
  message_type: 'text' | 'image' | 'audio' | 'system';
  is_deleted: boolean;
  created_at: string;
  attachments?: Attachment[];
}

export interface ConversationMember {
  user_id: string;
  username: string;
  display_name: string;
  avatar_url?: string | null;
  role: string;
  is_online: boolean;
}

export interface Conversation {
  id: string;
  type: 'direct' | 'group';
  title?: string | null;
  avatar_url?: string | null;
  created_at: string;
  members: ConversationMember[];
  last_message?: Message | null;
  unread_count?: number;
}

export interface CallRecord {
  id: string;
  caller_id?: string | null;
  receiver_id?: string | null;
  caller_username?: string | null;
  caller_display_name?: string | null;
  caller_avatar_url?: string | null;
  receiver_username?: string | null;
  receiver_display_name?: string | null;
  receiver_avatar_url?: string | null;
  room_name: string;
  call_type: 'video' | 'voice' | 'group';
  status: string;
  started_at?: string | null;
  ended_at?: string | null;
  duration_seconds: number;
  created_at: string;
  livekit_token?: string | null;
  livekit_url?: string | null;
}

export interface Meeting {
  id: string;
  meeting_code: string;
  title: string;
  room_name: string;
  host_id: string;
  host_username?: string | null;
  host_display_name?: string | null;
  is_active: boolean;
  waiting_room_enabled: boolean;
  created_at: string;
  livekit_token?: string | null;
  livekit_url?: string | null;
}

