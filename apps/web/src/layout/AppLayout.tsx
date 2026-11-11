import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';
import { Seal } from '../components/Seal';
import { ROLE_LABELS } from '../i18n/labels';

const NAVIGATION: Record<Role, { label: string; to: string }[]> = {
    STUDENT: [
        { label: 'Strona główna', to: '/' },
        { label: 'Moje zajęcia', to: '/groups' },
        { label: 'Indeks', to: '/transcript' },
    ],
    LECTURER: [{ label: 'Strona główna', to: '/' }],
    DEAN_OFFICE: [{ label: 'Strona główna', to: '/' }],
    ADMIN: [{ label: 'Strona główna', to: '/' }],
};

const CONTAINER = 'mx-auto max-w-[1120px] px-4';

export function AppLayout() {
    const { user, logout } = useAuth();
    if (!user) {
        return null;
    }

    return (
        <div className="min-h-screen">
            <header className="bg-navy text-white">
                <div className={`${CONTAINER} flex items-center justify-between gap-4 py-3`}>
                    <Link
                        to="/"
                        className="flex items-center gap-3 rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                    >
                        <Seal />
                        <span>
              <span className="block text-[1.375rem] leading-tight font-bold">tokStudiow</span>
              <span className="hidden text-[13px] opacity-85 sm:block">System obsługi toku studiów</span>
            </span>
                    </Link>

                    <div className="flex items-center gap-4 text-sm">
            <span className="hidden text-right leading-snug sm:block">
              <strong className="block">
                {user.firstName} {user.lastName}
              </strong>
              <span className="opacity-85">{ROLE_LABELS[user.role]}</span>
            </span>
                        <button
                            type="button"
                            onClick={logout}
                            className="rounded-sm border border-white/55 px-3.5 py-1.5 hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
                        >
                            Wyloguj
                        </button>
                    </div>
                </div>
            </header>

            <nav aria-label="Menu główne" className="border-b border-line bg-white">
                <div className={`${CONTAINER} flex gap-1 overflow-x-auto`}>
                    {NAVIGATION[user.role].map((item) => (
                        <NavLink
                            key={item.to}
                            to={item.to}
                            end={item.to === '/'}
                            className={({ isActive }) =>
                                `border-b-[3px] px-3.5 pt-3 pb-2.5 text-[15px] font-semibold whitespace-nowrap focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-navy ${
                                    isActive ? 'border-navy text-navy' : 'border-transparent hover:bg-page hover:text-navy'
                                }`
                            }
                        >
                            {item.label}
                        </NavLink>
                    ))}
                </div>
            </nav>

            <main className={`${CONTAINER} pt-6 pb-12`}>
                <Outlet />
            </main>
        </div>
    );
}