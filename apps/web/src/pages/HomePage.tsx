import { AppShell, Button, Group, Stack, Text, Title } from '@mantine/core';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';

const ROLE_LABELS: Record<Role, string> = {
    STUDENT: 'Student',
    LECTURER: 'Prowadzący',
    DEAN_OFFICE: 'Dziekanat',
    ADMIN: 'Administrator',
};

export function HomePage() {
    const { user, logout } = useAuth();
    if (!user) {
        return null;
    }

    return (
        <AppShell header={{ height: 60 }} padding="md">
            <AppShell.Header>
                <Group h="100%" px="md" justify="space-between">
                    <Title order={4}>tokStudiow</Title>
                    <Group>
                        <Text size="sm">
                            {user.firstName} {user.lastName}
                        </Text>
                        <Button variant="default" size="xs" onClick={logout}>
                            Wyloguj
                        </Button>
                    </Group>
                </Group>
            </AppShell.Header>
            <AppShell.Main>
                <Stack>
                    <Title order={2}>Witaj, {user.firstName}</Title>
                    <Text c="dimmed">Rola: {ROLE_LABELS[user.role]}</Text>
                    {user.albumNumber && <Text>Numer albumu: {user.albumNumber}</Text>}
                </Stack>
            </AppShell.Main>
        </AppShell>
    );
}