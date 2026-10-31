# VIYO API Documentation

**Application:** VIYO  
**Tagline:** See. Talk. Connect.  
**Protocol:** RESTful HTTPS + WebSockets (RFC 6455)  
**Authentication:** HTTP Bearer JSON Web Tokens (JWT) / Firebase ID Token  
**Base URL:** `/api/v1`

---

## 1. Authentication Endpoints (`/api/v1/auth`)

### 1.1 Register New Account
- **Endpoint:** `POST /api/v1/auth/register`
- **Access:** Public
- **Request Body:**
  ```json
  {
    "email": "user@viyo.io",
    "username": "user_handle",
    "password": "SecurePassword123!",
    "display_name": "Full Name"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "access_token": "eyJhbGciOi...",
    "token_type": "bearer",
    "user_id": "90e665fd-0720-410a-b31c-c2bf0b1da567",
    "username": "user_handle"
  }
  ```

### 1.2 Sign In
- **Endpoint:** `POST /api/v1/auth/login`
- **Access:** Public
- **Request Body:**
  ```json
  {
    "username_or_email": "user_handle",
    "password": "SecurePassword123!"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "access_token": "eyJhbGciOi...",
    "token_type": "bearer",
    "user_id": "90e665fd-0720-410a-b31c-c2bf0b1da567",
    "username": "user_handle"
  }
  ```

### 1.3 Firebase Authentication Exchange
- **Endpoint:** `POST /api/v1/auth/firebase-auth`
- **Access:** Public
- **Request Body:**
  ```json
  {
    "id_token": "firebase_jwt_token_string",
    "username": "optional_chosen_handle",
    "display_name": "Optional Name"
  }
  ```

### 1.4 Current User Profile & Settings
- **Endpoint:** `GET /api/v1/auth/me`
- **Headers:** `Authorization: Bearer <token>`
- **Response (200 OK):** User entity with nested profile and privacy settings.

---

## 2. User Directory & Profile Management (`/api/v1/users`)

### 2.1 Search Directory
- **Endpoint:** `GET /api/v1/users/search?q={query}`
- **Headers:** `Authorization: Bearer <token>`
- **Description:** Search users by username or display name. Automatically filters blocked users and enforces privacy settings.

### 2.2 Update Profile
- **Endpoint:** `PUT /api/v1/users/profile`
- **Request Body:**
  ```json
  {
    "display_name": "Jane Cooper",
    "bio": "Bio description",
    "status_message": "Available"
  }
  ```

### 2.3 Upload Avatar
- **Endpoint:** `POST /api/v1/users/avatar`
- **Content-Type:** `multipart/form-data` (file: image file)

### 2.4 Update Settings
- **Endpoint:** `PUT /api/v1/users/settings`
- **Request Body:**
  ```json
  {
    "privacy_last_seen": "contacts_only",
    "privacy_calls": "everyone",
    "allow_incoming_calls": true,
    "camera_enabled_by_default": true,
    "microphone_enabled_by_default": true
  }
  ```

---

## 3. Contacts & Social Graph (`/api/v1/contacts`)

- `GET /api/v1/contacts`: Returns all accepted contacts with presence information.
- `POST /api/v1/contacts/request`: Sends a contact request (`{"receiver_username": "target"}`).
- `GET /api/v1/contacts/requests/pending`: Lists received pending contact requests.
- `POST /api/v1/contacts/requests/{id}/accept`: Accepts a contact request.
- `POST /api/v1/contacts/requests/{id}/reject`: Declines a request.
- `DELETE /api/v1/contacts/{contact_user_id}`: Removes a contact.
- `POST /api/v1/contacts/block`: Blocks a user and removes mutual contacts.
- `POST /api/v1/contacts/unblock/{id}`: Unblocks a user.

---

## 4. One-to-One Calling (`/api/v1/calls`)

### 4.1 Initiate Direct Call
- **Endpoint:** `POST /api/v1/calls/initiate`
- **Request Body:**
  ```json
  {
    "receiver_id": "uuid-recipient",
    "call_type": "video"
  }
  ```
- **Returns:** Call record with signed `livekit_token` and `livekit_url`. Triggers `incoming_call` WebSocket event to recipient.

### 4.2 Call Action
- **Endpoint:** `POST /api/v1/calls/{id}/action`
- **Request Body:** `{"action": "accept" | "reject" | "cancel" | "end"}`

### 4.3 Call History
- **Endpoint:** `GET /api/v1/calls/history`

---

## 5. Group Video Meetings (`/api/v1/meetings`)

### 5.1 Host Meeting
- **Endpoint:** `POST /api/v1/meetings`
- **Request Body:**
  ```json
  {
    "title": "Family Video Catch-up",
    "waiting_room_enabled": false
  }
  ```
- **Returns:** Alphanumeric 9-character `meeting_code` (e.g., `abc-defg-hij`) and host LiveKit token.

### 5.2 Join Meeting
- **Endpoint:** `GET /api/v1/meetings/{meeting_code}`
- **Returns:** Meeting details and attendee LiveKit access token.

### 5.3 Conclude Meeting
- **Endpoint:** `POST /api/v1/meetings/{meeting_code}/end`
- **Description:** Host concludes meeting and terminates SFU room.

---

## 6. Real-Time WebSockets (`/api/v1/ws`)

Connect: `ws://<host>/api/v1/ws?token=<access_token>`

### Events Emitted to Client:
- `incoming_call`: `{ call_id, room_name, call_type, caller: { id, username, display_name, avatar_url } }`
- `call_accepted`: `{ call_id, room_name }`
- `call_rejected`: `{ call_id }`
- `call_cancelled`: `{ call_id }`
- `call_ended`: `{ call_id, duration }`
- `new_message`: `{ message: { id, conversation_id, content, sender_id, attachments } }`
- `contact_request_received`: `{ request_id, sender_username }`
- `user_typing`: `{ conversation_id, user_id, is_typing }`

### Client Uplink Messages:
- `{"action": "typing", "conversation_id": "uuid", "participant_ids": ["uuid"], "is_typing": true}`
- `{"action": "ping"}` -> Server replies `{"event": "pong"}`

