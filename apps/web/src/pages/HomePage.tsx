import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { api } from '../api/client';
import type { Study } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { Panel } from '../components/Panel';
import { QueryState } from '../components/QueryState';
import { Table, Td, Th, Tr } from '../components/ui/Table';
import { ROLE_LABELS, STUDY_STATUS_LABELS } from '../i18n/labels';

export function HomePage() {
    const { user } = useAuth();
    const isStudent = user?.role === 'STUDENT';
    const studies = useQuery({
        queryKey: ['me', 'studies'],
        queryFn: () => api<Study[]>('/api/me/studies'),
        enabled: isStudent,
    });

    if (!user) {
        return null;
    }

    const details: [string, string][] = [
        ['Imię i nazwisko', `${user.firstName} ${user.lastName}`],
        ['Adres e-mail', user.email],
        ['Rola', ROLE_LABELS[user.role]],
    ];
    if (user.albumNumber) {
        details.push(['Numer albumu', user.albumNumber]);
    }

    return (
        <>
            <PageHeader title="Strona główna" />
            <p className="mb-6">Witaj, {user.firstName}.</p>

            <div className="grid gap-6 md:grid-cols-2">
                <div className="flex flex-col gap-6">
                    <Panel title="Twoje dane" flush>
                        <Table>
                            <tbody>
                            {details.map(([label, value]) => (
                                <Tr key={label}>
                                    <Th scope="row" className="w-2/5">
                                        {label}
                                    </Th>
                                    <Td>{value}</Td>
                                </Tr>
                            ))}
                            </tbody>
                        </Table>
                    </Panel>

                    {isStudent && (
                        <Panel title="Studia" flush>
                            <QueryState query={studies}>
                                {(list) =>
                                    list.length === 0 ? (
                                        <p className="p-4 text-muted">Nie masz jeszcze przypisanych studiów.</p>
                                    ) : (
                                        <Table>
                                            <thead>
                                            <tr>
                                                <Th>Kierunek</Th>
                                                <Th>Semestr</Th>
                                                <Th>Status</Th>
                                            </tr>
                                            </thead>
                                            <tbody>
                                            {list.map((study) => (
                                                <Tr key={study.id}>
                                                    <Td>{study.fieldOfStudyName}</Td>
                                                    <Td>{study.currentSemester}</Td>
                                                    <Td>{STUDY_STATUS_LABELS[study.status]}</Td>
                                                </Tr>
                                            ))}
                                            </tbody>
                                        </Table>
                                    )
                                }
                            </QueryState>
                        </Panel>
                    )}
                </div>

                {isStudent && (
                    <Panel title="Na skróty">
                        <div className="flex flex-col gap-4">
                            <Shortcut to="/groups" title="Moje zajęcia">
                                Oceny cząstkowe i obecności w grupach, do których jesteś zapisany.
                            </Shortcut>
                            <Shortcut to="/transcript" title="Indeks">
                                Zatwierdzone oceny końcowe i średnia.
                            </Shortcut>
                        </div>
                    </Panel>
                )}

                {user.role === 'DEAN_OFFICE' && (
                    <Panel title="Na skróty">
                        <div className="flex flex-col gap-4">
                            <Shortcut to="/dean/groups" title="Grupy zajęciowe">
                                Tworzenie grup, zapisy studentów i protokoły ocen końcowych.
                            </Shortcut>
                            <Shortcut to="/dean/students" title="Studenci">
                                Studenci wydziału, semestr i status studiów.
                            </Shortcut>
                        </div>
                    </Panel>
                )}

                {user.role === 'LECTURER' && (
                    <Panel title="Na skróty">
                        <Shortcut to="/teaching" title="Moje grupy">
                            Elementy zaliczenia i oceny cząstkowe w prowadzonych grupach.
                        </Shortcut>
                    </Panel>
                )}
            </div>
        </>
    );
}

function Shortcut({ to, title, children }: { to: string; title: string; children: ReactNode }) {
    return (
        <div>
            <Link to={to} className="font-semibold text-navy underline-offset-2 hover:underline">
                {title}
            </Link>
            <p className="text-sm text-muted">{children}</p>
        </div>
    );
}