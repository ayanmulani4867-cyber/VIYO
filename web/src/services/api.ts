const API_BASE = import.meta.env.VITE_API_URL || '/api/v1';

class ApiService {
  private getToken(): string | null {
    return localStorage.getItem('viyo_token');
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const token = this.getToken();
    const headers: Record<string, string> = {
      ...(options.headers as Record<string, string>),
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers,
    });

    if (!res.ok) {
      let errorMsg = 'An error occurred';
      try {
        const errorData = await res.json();
        errorMsg = errorData.detail || errorData.message || JSON.stringify(errorData);
      } catch {
        errorMsg = res.statusText || `Request failed with status ${res.status}`;
      }
      throw new Error(errorMsg);
    }

    return res.json();
  }

  // Auth
  async register(data: any) {
    return this.request<{ access_token: string; user_id: string; username: string }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async login(data: any) {
    return this.request<{ access_token: string; user_id: string; username: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async getMe() {
    return this.request<any>('/auth/me');
  }

  async logout() {
    return this.request<any>('/auth/logout', { method: 'POST' });
  }

  // Users & Profiles
  async searchUsers(query: string) {
    return this.request<any[]>(`/users/search?q=${encodeURIComponent(query)}`);
  }

  async updateProfile(data: any) {
    return this.request<any>('/users/profile', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async updateSettings(data: any) {
    return this.request<any>('/users/settings', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async uploadAvatar(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.request<{ avatar_url: string }>('/users/avatar', {
      method: 'POST',
      body: formData,
    });
  }

  // Contacts
  async getContacts() {
    return this.request<any[]>('/contacts');
  }

  async sendContactRequest(receiverUsername: string) {
    return this.request<any>('/contacts/request', {
      method: 'POST',
      body: JSON.stringify({ receiver_username: receiverUsername }),
    });
  }

  async getPendingRequests() {
    return this.request<any[]>('/contacts/requests/pending');
  }

  async acceptContactRequest(requestId: string) {
    return this.request<any>(`/contacts/requests/${requestId}/accept`, {
      method: 'POST',
    });
  }

  async rejectContactRequest(requestId: string) {
    return this.request<any>(`/contacts/requests/${requestId}/reject`, {
      method: 'POST',
    });
  }

  async removeContact(userId: string) {
    return this.request<any>(`/contacts/${userId}`, {
      method: 'DELETE',
    });
  }

  async blockUser(userId: string, reason?: string) {
    return this.request<any>('/contacts/block', {
      method: 'POST',
      body: JSON.stringify({ user_id: userId, reason }),
    });
  }

  // Messaging
  async getConversations() {
    return this.request<any[]>('/messaging/conversations');
  }

  async createConversation(type: 'direct' | 'group', participantUserIds: string[], title?: string) {
    return this.request<any>('/messaging/conversations', {
      method: 'POST',
      body: JSON.stringify({
        type,
        participant_user_ids: participantUserIds,
        title,
      }),
    });
  }

  async getMessages(conversationId: string) {
    return this.request<any[]>(`/messaging/conversations/${conversationId}/messages`);
  }

  async sendMessage(conversationId: string, content?: string, attachmentIds?: string[]) {
    return this.request<any>(`/messaging/conversations/${conversationId}/messages`, {
      method: 'POST',
      body: JSON.stringify({
        content,
        message_type: 'text',
        attachment_ids: attachmentIds,
      }),
    });
  }

  async uploadAttachment(file: File) {
    const formData = new FormData();
    formData.append('file', file);
    return this.request<any>('/messaging/upload', {
      method: 'POST',
      body: formData,
    });
  }

  // Calls
  async initiateCall(receiverId: string, callType: 'video' | 'voice' = 'video') {
    return this.request<any>('/calls/initiate', {
      method: 'POST',
      body: JSON.stringify({ receiver_id: receiverId, call_type: callType }),
    });
  }

  async callAction(callId: string, action: 'accept' | 'reject' | 'cancel' | 'end') {
    return this.request<any>(`/calls/${callId}/action`, {
      method: 'POST',
      body: JSON.stringify({ action }),
    });
  }

  async getCallHistory() {
    return this.request<any[]>('/calls/history');
  }

  // Meetings
  async createMeeting(title: string, waitingRoomEnabled: boolean = false) {
    return this.request<any>('/meetings', {
      method: 'POST',
      body: JSON.stringify({ title, waiting_room_enabled: waitingRoomEnabled }),
    });
  }

  async joinMeeting(meetingCode: string) {
    return this.request<any>(`/meetings/${encodeURIComponent(meetingCode)}`);
  }

  async endMeeting(meetingCode: string) {
    return this.request<any>(`/meetings/${encodeURIComponent(meetingCode)}/end`, {
      method: 'POST',
    });
  }

  // Moderation & Stats
  async getStats() {
    return this.request<any>('/moderation/admin/stats');
  }
}

export const api = new ApiService();

