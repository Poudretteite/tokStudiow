import { Center, Loader } from '@mantine/core';
import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';

/** Wpuszcza tylko zalogowanych; pozostałych odsyła na stronę logowania. */
export function RequireAuth() {
    const { user, loading } = useAuth();
    const location = useLocation();

    if (loading) {
        return (
            <Center h="100vh">
                <Loader />
            </Center>
        );
    }
    if (!user) {
        return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    }
    return <Outlet />;
}