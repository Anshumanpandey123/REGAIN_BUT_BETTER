# ⚡ Supabase & Google Sign-In Integration Status & Guide

Your **Regain Pro** application backend is fully configured on Supabase:

- **Project Reference**: `rvvpugklbrdnfayckhhx`
- **Project URL**: `https://rvvpugklbrdnfayckhhx.supabase.co`
- **Anon Public API Key**:
  `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJ2dnB1Z2tsYnJkbmZheWNraGh4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM4NzA1OTcsImV4cCI6MjEwOTQ0NjU5N30.Nm5oozpjiGiFYbxXHbt9iP7KMLaA9kFt680BGF353qU`
- **Google Provider Status**: **`ENABLED`**
- **Google Client ID**: `107854115743-2p89cjds9e2c8hmt1p0c3vftap9ir11v.apps.googleusercontent.com`
- **Redirect URIs Allowed**: `regainpro://auth-callback`, `http://localhost:3000`, `https://regain.local`
- **Database Tables**:
  - `public.profiles` (User metadata, Google avatar, streak days, total focus minutes)
  - `public.focus_sessions` (Pomodoro, countdown logs, completed subjects)
  - `public.study_rooms` (Multiplayer active focus rooms)

---

## 🔑 Crucial Step in Google Cloud Console

In your [Google Cloud Console](https://console.cloud.google.com/apis/credentials):
1. Select your project and edit OAuth 2.0 Client ID: `107854115743-2p89cjds9e2c8hmt1p0c3vftap9ir11v.apps.googleusercontent.com`
2. Under **Authorized redirect URIs**, make sure this exact URI is present:
   ```
   https://rvvpugklbrdnfayckhhx.supabase.co/auth/v1/callback
   ```
3. Save changes.

---

## 📱 How Google Login Operates in Regain Pro

1. User taps **Sign in with Google** on the Account Modal in Regain Pro.
2. The app asks Supabase for the Google OAuth authorization URL.
3. Because Google blocks OAuth in embedded WebViews, Regain Pro opens the system browser (Chrome) using `AndroidBridge.openExternalBrowser(url)`.
4. User selects their Google account and authorizes.
5. Google returns to Supabase at `https://rvvpugklbrdnfayckhhx.supabase.co/auth/v1/callback`.
6. Supabase redirects to `regainpro://auth-callback#access_token=...&refresh_token=...`.
7. Android's intent-filter activates `MainActivity` (`launchMode="singleTask"`), which extracts the session tokens and updates the UI!
