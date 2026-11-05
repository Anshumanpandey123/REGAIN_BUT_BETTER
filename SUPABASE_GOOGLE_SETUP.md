# ⚡ Supabase & Google Sign-In Integration Guide for Regain Pro

This guide walks you through connecting your **Regain Pro** application to **Supabase** for cloud synchronization, multiplayer focus rooms, streak analytics, and **Google Sign-In**.

---

## 1. Setting Up Your Supabase Project

1. Go to [https://supabase.com](https://supabase.com) and create an account or sign in.
2. Click **New Project**.
3. Choose an organization, project name (e.g. `regain-pro-backend`), and a secure database password.
4. Select the region closest to you.
5. Once your project is created, navigate to **Project Settings** -> **API**:
   - Copy **Project URL** (e.g., `https://xyzabcdefg.supabase.co`)
   - Copy **anon / public** API Key

---

## 2. Setting Up Database Tables

Open the **SQL Editor** tab in your Supabase dashboard and run the following script:

```sql
-- 1. Create Profiles Table
CREATE TABLE public.profiles (
    id UUID REFERENCES auth.users ON DELETE CASCADE PRIMARY KEY,
    email TEXT,
    full_name TEXT,
    avatar_url TEXT,
    streak_days INTEGER DEFAULT 0,
    total_focus_minutes INTEGER DEFAULT 0,
    is_pro_unlocked BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Create Focus Sessions Table
CREATE TABLE public.focus_sessions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    subject TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    completed BOOLEAN DEFAULT TRUE,
    mode TEXT DEFAULT 'pomodoro',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Create Multiplayer Focus Rooms Table
CREATE TABLE public.study_rooms (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL,
    topic TEXT NOT NULL,
    active_peers INTEGER DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Seed initial multiplayer focus rooms
INSERT INTO public.study_rooms (name, topic, active_peers) VALUES
('🔥 Deep Work - Code Room', 'LeetCode & Android Dev zero-lag session', 14),
('📚 Physics & Math Marathon', 'Mechanics and Quantum formulas', 8),
('☕ Late Night Study Sprint', 'Silent Pomodoro & Reading', 19);

-- 4. Enable Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.focus_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.study_rooms ENABLE ROW LEVEL SECURITY;

-- Profiles Policies
CREATE POLICY "Public profiles are viewable by everyone" 
ON public.profiles FOR SELECT USING (true);

CREATE POLICY "Users can insert their own profile" 
ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update their own profile" 
ON public.profiles FOR UPDATE USING (auth.uid() = id);

-- Focus Sessions Policies
CREATE POLICY "Users can view their own focus sessions" 
ON public.focus_sessions FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert their own focus sessions" 
ON public.focus_sessions FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Study Rooms Policies
CREATE POLICY "Study rooms viewable by all users" 
ON public.study_rooms FOR SELECT USING (true);
```

---

## 3. Configuring Google Sign-In with Supabase

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Create a project or select an existing project.
3. Configure the **OAuth Consent Screen**:
   - User Type: **External**
   - App Name: `Regain Pro`
   - User support email & Developer contact email: your email.
   - Save and continue through Scopes (`email`, `profile`, `openid`).
4. Go to **Credentials** -> **Create Credentials** -> **OAuth Client ID**:
   - Application Type: **Web application**
   - Name: `Regain Pro Supabase Web Client`
   - Under **Authorized redirect URIs**, add:
     ```
     https://<YOUR-PROJECT-REF>.supabase.co/auth/v1/callback
     ```
     *(Replace `<YOUR-PROJECT-REF>` with your Supabase project ID, found in Project Settings -> General).*
5. Copy your **Client ID** and **Client Secret**.
6. Back in the **Supabase Dashboard**:
   - Go to **Authentication** -> **Providers** -> **Google**.
   - Toggle **Enable Google provider**.
   - Paste the **Client ID** and **Client Secret**.
   - Click **Save**.

---

## 4. Connecting in the App

1. Launch **Regain Pro** on your Android device.
2. Go to the **Groups** tab or tap the **Avatar (🦁)** in the top-left corner.
3. Tap **Configure Supabase Project ⚙**.
4. Enter your **Supabase URL** and **Anon Public Key**.
5. Tap **Save & Connect**.
6. Tap **Sign in with Google** to authenticate and sync all study streaks and data!
