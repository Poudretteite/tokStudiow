import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, refreshAccessToken, setAccessToken } from '../api/client';
import type { Me } from '../api/types';

interface AuthState {
    user: Me | null;
    loading: boolean;
    login: (email: string, password: string) => Promise<void>;
    logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<Me | null>(null);
    const [loading, setLoading] = useState(true);

    // Po otwarciu lub odświeżeniu strony: spróbuj odtworzyć sesję z ciasteczka
    useEffect(() => {
        (async () => {
            if (await refreshAccessToken()) {
                try {
                    setUser(await api<Me>('/api/me'));
                } catch {
                    setUser(null);
                }
            }
            setLoading(false);
        })();
    }, []);

    const login = useCallback(async (email: string, password: string) => {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Client': 'web' },
            body: JSON.stringify({ email, password }),
        });
        if (!response.ok) {
            throw new Error('Nieprawidłowy e-mail lub hasło');
        }
        setAccessToken((await response.json()).accessToken);
        setUser(await api<Me>('/api/me'));
    }, []);

    const logout = useCallback(async () => {
        await fetch('/api/auth/logout', { method: 'POST', headers: { 'X-Client': 'web' } });
        setAccessToken(null);
        setUser(null);
    }, []);

    const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout]);
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used inside AuthProvider');
    }
    return context;
}