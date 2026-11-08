import { useState, type FormEvent } from 'react';
import { Alert, Button, Center, Paper, PasswordInput, Stack, TextInput, Title } from '@mantine/core';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';

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
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Nie udało się zalogować');
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <Center h="100vh" p="md">
            <Paper withBorder shadow="sm" p="xl" radius="md" w="100%" maw={400}>
                <form onSubmit={handleSubmit}>
                    <Stack>
                        <Title order={2}>tokStudiow</Title>
                        {error && <Alert color="red">{error}</Alert>}
                        <TextInput
                            label="E-mail"
                            type="email"
                            autoComplete="username"
                            required
                            value={email}
                            onChange={(e) => setEmail(e.currentTarget.value)}
                        />
                        <PasswordInput
                            label="Hasło"
                            autoComplete="current-password"
                            required
                            value={password}
                            onChange={(e) => setPassword(e.currentTarget.value)}
                        />
                        <Button type="submit" loading={submitting} fullWidth>
                            Zaloguj
                        </Button>
                    </Stack>
                </form>
            </Paper>
        </Center>
    );
}