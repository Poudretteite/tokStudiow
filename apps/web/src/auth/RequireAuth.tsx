import { Navigate, Outlet, useLocation } from 'react-router';
import { Spinner } from '../components/ui/Spinner';
import { useAuth } from './AuthContext';

export function RequireAuth() {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) {
        return (
            <div className="flex min-h-screen items-center justify-center text-navy">
                <Spinner />
            </div>
        );
    }
    if (!user) {
        return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    }
    return <Outlet />;
}