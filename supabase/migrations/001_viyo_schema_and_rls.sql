-- ====================================================================
-- VIYO — Native Android Production Database Schema & Security Policies
-- Platform: Supabase PostgreSQL (auth.users integration + RLS)
-- ====================================================================

-- 1. Enable UUID Extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. User Profiles Table (Linked to Supabase Auth)
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    avatar_url TEXT,
    bio TEXT,
    status_message TEXT,
    is_online BOOLEAN DEFAULT FALSE,
    last_seen TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for instant username / display_name discovery
CREATE INDEX IF NOT EXISTS idx_user_profiles_username ON public.user_profiles (username);
CREATE INDEX IF NOT EXISTS idx_user_profiles_display_name ON public.user_profiles (display_name);

-- 3. Automatic Profile Creation Trigger on Supabase Auth Signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.user_profiles (id, username, display_name)
    VALUES (
        NEW.id,
        LOWER(COALESCE(NEW.raw_user_meta_data->>'username', SPLIT_PART(NEW.email, '@', 1))),
        COALESCE(NEW.raw_user_meta_data->>'display_name', NEW.raw_user_meta_data->>'username', SPLIT_PART(NEW.email, '@', 1))
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- 4. Contacts Table (Mutual Connections)
CREATE TABLE IF NOT EXISTS public.contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    contact_user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    nickname TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_user_contact UNIQUE (user_id, contact_user_id)
);

CREATE INDEX IF NOT EXISTS idx_contacts_user_id ON public.contacts (user_id);
CREATE INDEX IF NOT EXISTS idx_contacts_contact_user_id ON public.contacts (contact_user_id);

-- 5. Contact Requests Table
CREATE TABLE IF NOT EXISTS public.contact_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    receiver_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    status TEXT DEFAULT 'pending' NOT NULL CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_contact_requests_sender ON public.contact_requests (sender_id);
CREATE INDEX IF NOT EXISTS idx_contact_requests_receiver ON public.contact_requests (receiver_id);

-- 6. Conversations Table
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type TEXT DEFAULT 'direct' NOT NULL CHECK (type IN ('direct', 'group')),
    title TEXT,
    created_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 7. Conversation Members Table
CREATE TABLE IF NOT EXISTS public.conversation_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE NOT NULL,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    role TEXT DEFAULT 'member' NOT NULL CHECK (role IN ('owner', 'admin', 'member')),
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT uq_conv_member UNIQUE (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_conv_members_user ON public.conversation_members (user_id);
CREATE INDEX IF NOT EXISTS idx_conv_members_conv ON public.conversation_members (conversation_id);

-- 8. Messages Table
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID REFERENCES public.conversations(id) ON DELETE CASCADE NOT NULL,
    sender_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    content TEXT,
    message_type TEXT DEFAULT 'text' NOT NULL CHECK (message_type IN ('text', 'image', 'audio', 'system')),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_messages_conv_created ON public.messages (conversation_id, created_at ASC);

-- 9. Calls Table (Signaling & Records)
CREATE TABLE IF NOT EXISTS public.calls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    caller_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    receiver_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    room_name TEXT NOT NULL,
    call_type TEXT DEFAULT 'video' NOT NULL CHECK (call_type IN ('video', 'voice')),
    status TEXT DEFAULT 'ringing' NOT NULL CHECK (status IN ('ringing', 'accepted', 'rejected', 'ended', 'missed')),
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    duration_seconds INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_calls_caller ON public.calls (caller_id);
CREATE INDEX IF NOT EXISTS idx_calls_receiver ON public.calls (receiver_id);

-- 10. Meetings Table
CREATE TABLE IF NOT EXISTS public.meetings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_code TEXT UNIQUE NOT NULL,
    host_id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL,
    title TEXT NOT NULL,
    room_name TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_meetings_code ON public.meetings (meeting_code);

-- ====================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- ====================================================================

ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.contact_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversation_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.meetings ENABLE ROW LEVEL SECURITY;

-- Profiles: Authenticated users can read all profiles (discovery); users can only update their own profile
CREATE POLICY "Public profiles are readable by authenticated users"
    ON public.user_profiles FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Users can update their own profile"
    ON public.user_profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id);

-- Contacts: Users only see and manage their own contact book
CREATE POLICY "Users view their own contacts"
    ON public.contacts FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users insert their own contacts"
    ON public.contacts FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users delete their own contacts"
    ON public.contacts FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- Contact Requests: Participants can see and update requests
CREATE POLICY "Users view requests they sent or received"
    ON public.contact_requests FOR SELECT
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

CREATE POLICY "Users can create contact requests as sender"
    ON public.contact_requests FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = sender_id);

CREATE POLICY "Recipients can update request status"
    ON public.contact_requests FOR UPDATE
    TO authenticated
    USING (auth.uid() = receiver_id OR auth.uid() = sender_id);

-- Conversations & Members: Only conversation members can access conversations
CREATE POLICY "Members view their conversations"
    ON public.conversations FOR SELECT
    TO authenticated
    USING (
        id IN (SELECT conversation_id FROM public.conversation_members WHERE user_id = auth.uid())
    );

CREATE POLICY "Users can create conversations"
    ON public.conversations FOR INSERT
    TO authenticated
    WITH CHECK (true);

CREATE POLICY "Members view member list"
    ON public.conversation_members FOR SELECT
    TO authenticated
    USING (
        conversation_id IN (SELECT conversation_id FROM public.conversation_members WHERE user_id = auth.uid())
    );

CREATE POLICY "Users can join or add members"
    ON public.conversation_members FOR INSERT
    TO authenticated
    WITH CHECK (true);

-- Messages: Only conversation participants can view and send messages
CREATE POLICY "Members can view messages"
    ON public.messages FOR SELECT
    TO authenticated
    USING (
        conversation_id IN (SELECT conversation_id FROM public.conversation_members WHERE user_id = auth.uid())
    );

CREATE POLICY "Members can send messages"
    ON public.messages FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = sender_id AND
        conversation_id IN (SELECT conversation_id FROM public.conversation_members WHERE user_id = auth.uid())
    );

-- Calls: Callers and recipients can view and update call signaling records
CREATE POLICY "Call participants view calls"
    ON public.calls FOR SELECT
    TO authenticated
    USING (auth.uid() = caller_id OR auth.uid() = receiver_id);

CREATE POLICY "Callers can initiate calls"
    ON public.calls FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = caller_id);

CREATE POLICY "Participants can update call status"
    ON public.calls FOR UPDATE
    TO authenticated
    USING (auth.uid() = caller_id OR auth.uid() = receiver_id);

-- Meetings: Authenticated users can view and host meetings
CREATE POLICY "Authenticated users view meetings"
    ON public.meetings FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Hosts can create meetings"
    ON public.meetings FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = host_id);

-- ====================================================================
-- REALTIME SUBSCRIPTIONS
-- ====================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'calls'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.calls;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'contact_requests'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.contact_requests;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables 
        WHERE pubname = 'supabase_realtime' AND tablename = 'user_profiles'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.user_profiles;
    END IF;
END $$;

