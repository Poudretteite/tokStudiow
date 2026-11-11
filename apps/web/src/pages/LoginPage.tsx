import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { Seal } from '../components/Seal';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { TextField } from '../components/ui/TextField';

export function LoginPage() {
    const { user, login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    if (user) {
        return <Navigate to="/" replace />;
    }

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            await login(email, password);
            navigate((location.state as { from?: string } | null)?.from ?? '/', { replace: true });
        } catch {
            setError('Nieprawidłowy adres e-mail lub hasło.');
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="flex min-h-screen flex-col items-center justify-center p-4">
            <div className="w-full max-w-[400px] border border-line bg-white">
                <div className="flex items-center gap-3.5 bg-navy px-5 py-4 text-white">
                    <Seal size={52} />
                    <div>
                        <div className="text-2xl leading-tight font-bold">tokStudiow</div>
                        <div className="text-sm opacity-85">System obsługi toku studiów</div>
                    </div>
                </div>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4 p-5">
                    <h1 className="font-semibold">Logowanie</h1>
                    {error && <Alert>{error}</Alert>}
                    <TextField
                        label="Adres e-mail"
                        type="email"
                        autoComplete="username"
                        required
                        value={email}
                        onChange={(e) => setEmail(e.currentTarget.value)}
                    />
                    <TextField
                        label="Hasło"
                        type="password"
                        autoComplete="current-password"
                        required
                        value={password}
                        onChange={(e) => setPassword(e.currentTarget.value)}
                    />
                    <Button type="submit" loading={submitting} className="w-full">
                        Zaloguj się
                    </Button>
                </form>
            </div>
            <p className="mt-4 max-w-[400px] text-center text-sm text-muted">
                Problem z logowaniem? Skontaktuj się z dziekanatem swojego wydziału.
            </p>
        </div>
    );
}