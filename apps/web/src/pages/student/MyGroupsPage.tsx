import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api } from '../../api/client';
import type { ClassGroup } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { COMPONENT_LABELS } from '../../i18n/labels';

export function MyGroupsPage() {
    const query = useQuery({ queryKey: ['me', 'groups'], queryFn: () => api<ClassGroup[]>('/api/me/groups') });

    return (
        <>
            <PageHeader title="Moje zajęcia" crumbs={[{ label: 'Moje zajęcia' }]} />
            <Panel title="Zajęcia, na które jesteś zapisany" flush>
                <QueryState query={query}>
                    {(groups) =>
                        groups.length === 0 ? (
                            <p className="p-4 text-muted">Nie jesteś zapisany do żadnych zajęć. Zapisy prowadzi dziekanat.</p>
                        ) : (
                            <Table minWidth={640}>
                                <thead>
                                <tr>
                                    <Th>Przedmiot</Th>
                                    <Th>Forma zajęć</Th>
                                    <Th>Grupa</Th>
                                    <Th>Prowadzący</Th>
                                    <Th>Semestr</Th>
                                </tr>
                                </thead>
                                <tbody>
                                {groups.map((group) => (
                                    <Tr key={group.id}>
                                        <Td>
                                            <Link
                                                to={`/groups/${group.id}`}
                                                className="font-semibold text-navy underline-offset-2 hover:underline"
                                            >
                                                {group.courseName}
                                            </Link>
                                            <div className="text-xs text-muted">{group.courseCode}</div>
                                        </Td>
                                        <Td>{COMPONENT_LABELS[group.componentType]}</Td>
                                        <Td>{group.groupNumber}</Td>
                                        <Td>
                                            {group.lecturerFirstName} {group.lecturerLastName}
                                        </Td>
                                        <Td>{group.academicTermName}</Td>
                                    </Tr>
                                ))}
                                </tbody>
                            </Table>
                        )
                    }
                </QueryState>
            </Panel>
        </>
    );
}