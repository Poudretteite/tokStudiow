import { Navigate, Route, Routes } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { AppLayout } from './layout/AppLayout';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { GroupDetailsPage } from './pages/student/GroupDetailsPage';
import { MyGroupsPage } from './pages/student/MyGroupsPage';
import { TranscriptPage } from './pages/student/TranscriptPage';

export default function App() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<RequireAuth />}>
                <Route path="/" element={<AppLayout />}>
                    <Route index element={<HomePage />} />
                    <Route element={<RequireRole roles={['STUDENT']} />}>
                        <Route path="groups" element={<MyGroupsPage />} />
                        <Route path="groups/:groupId" element={<GroupDetailsPage />} />
                        <Route path="transcript" element={<TranscriptPage />} />
                    </Route>
                </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}