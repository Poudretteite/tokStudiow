import { Navigate, Route, Routes } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { AppLayout } from './layout/AppLayout';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { GroupDetailsPage } from './pages/student/GroupDetailsPage';
import { MyGroupsPage } from './pages/student/MyGroupsPage';
import { TranscriptPage } from './pages/student/TranscriptPage';
import { LecturerGroupPage } from './pages/lecturer/LecturerGroupPage';
import { LecturerGroupsPage } from './pages/lecturer/LecturerGroupsPage';

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
                    <Route element={<RequireRole roles={['LECTURER']} />}>
                        <Route path="teaching" element={<LecturerGroupsPage />} />
                        <Route path="teaching/:groupId" element={<LecturerGroupPage />} />
                    </Route>
                </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}