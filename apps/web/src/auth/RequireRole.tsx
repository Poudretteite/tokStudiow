import { Navigate, Outlet } from 'react-router';
import type { Role } from '../api/types';
import { useAuth } from './AuthContext';

export function RequireRole({ roles }: { roles: Role[] }) {
    const { user } = useAuth();
    if (!user || !roles.includes(user.role)) {
        return <Navigate to="/" replace />;
    }
    return <Outlet />;
}