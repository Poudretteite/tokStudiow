import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router';
import { api } from '../../api/client';
import type { AssessmentItem, ClassGroup, MyAttendance, PartialGrade } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { Tabs } from '../../components/ui/Tabs';
import { formatDate, formatTime } from '../../i18n/format';
import { ATTENDANCE_CLASSES, ATTENDANCE_LABELS, COMPONENT_LABELS } from '../../i18n/labels';

export function GroupDetailsPage() {
    const groupId = encodeURIComponent(useParams().groupId ?? '');

    const groups = useQuery({ queryKey: ['me', 'groups'], queryFn: () => api<ClassGroup[]>('/api/me/groups') });
    const items = useQuery({
        queryKey: ['groups', groupId, 'items'],
        queryFn: () => api<AssessmentItem[]>(`/api/groups/${groupId}/assessment-items`),
    });
    const grades = useQuery({
        queryKey: ['me', 'groups', groupId, 'grades'],
        queryFn: () => api<PartialGrade[]>(`/api/me/groups/${groupId}/partial-grades`),
    });
    const attendance = useQuery({
        queryKey: ['me', 'groups', groupId, 'attendance'],
        queryFn: () => api<MyAttendance[]>(`/api/me/groups/${groupId}/attendance`),
    });

    const group = groups.data?.find((g) => encodeURIComponent(g.id) === groupId);
    const title = group ? group.courseName : 'Zajęcia';

    return (
        <>
            <PageHeader title={title} crumbs={[{ label: 'Moje zajęcia', to: '/groups' }, { label: title }]} />

            {group && (
                <div className="mb-6">
                    <Panel title="Informacje o zajęciach" flush>
                        <Table>
                            <tbody>
                            <InfoRow label="Kod przedmiotu" value={group.courseCode} />
                            <InfoRow
                                label="Forma zajęć"
                                value={`${COMPONENT_LABELS[group.componentType]}, grupa ${group.groupNumber}`}
                            />
                            <InfoRow label="Prowadzący" value={`${group.lecturerFirstName} ${group.lecturerLastName}`} />
                            <InfoRow label="Semestr" value={group.academicTermName} />
                            </tbody>
                        </Table>
                    </Panel>
                </div>
            )}

            <Tabs
                label="Szczegóły zajęć"
                tabs={[
                    {
                        id: 'grades',
                        label: 'Oceny cząstkowe',
                        content: (
                            <QueryState query={items}>
                                {(itemList) => (
                                    <QueryState query={grades}>
                                        {(gradeList) => <GradesTable items={itemList} grades={gradeList} />}
                                    </QueryState>
                                )}
                            </QueryState>
                        ),
                    },
                    {
                        id: 'attendance',
                        label: 'Obecności',
                        content: <QueryState query={attendance}>{(records) => <AttendanceTable records={records} />}</QueryState>,
                    },
                ]}
            />
        </>
    );
}

function InfoRow({ label, value }: { label: string; value: string }) {
    return (
        <Tr>
            <Th scope="row" className="w-[30%]">
                {label}
            </Th>
            <Td>{value}</Td>
        </Tr>
    );
}

function GradesTable({ items, grades }: { items: AssessmentItem[]; grades: PartialGrade[] }) {
    if (items.length === 0) {
        return <p className="text-muted">Prowadzący nie dodał jeszcze elementów zaliczenia.</p>;
    }
    return (
        <Panel title="Elementy zaliczenia" flush>
            <Table>
                <thead>
                <tr>
                    <Th>Element</Th>
                    <Th>Waga</Th>
                    <Th>Ocena</Th>
                </tr>
                </thead>
                <tbody>
                {items.map((item) => {
                    const attempts = grades
                        .filter((g) => g.assessmentItemId === item.id)
                        .sort((a, b) => a.attempt - b.attempt);
                    const current = attempts.at(-1);
                    const earlier = attempts.slice(0, -1);
                    return (
                        <Tr key={item.id}>
                            <Td>{item.name}</Td>
                            <Td>{item.gradingType === 'PASS_FAIL' ? 'zal/nzal' : `${item.weightPercent}%`}</Td>
                            <Td>
                                {current ? (
                                    <>
                      <span className={`font-semibold ${current.passing ? '' : 'text-red-700'}`}>
                        {current.gradeLabel}
                      </span>
                                        {earlier.length > 0 && (
                                            <span className="ml-2 text-sm text-muted">
                          (wcześniej: {earlier.map((g) => g.gradeLabel).join(', ')})
                        </span>
                                        )}
                                    </>
                                ) : (
                                    <span className="text-muted">brak</span>
                                )}
                            </Td>
                        </Tr>
                    );
                })}
                </tbody>
            </Table>
        </Panel>
    );
}

function AttendanceTable({ records }: { records: MyAttendance[] }) {
    if (records.length === 0) {
        return <p className="text-muted">Prowadzący nie wpisał jeszcze obecności.</p>;
    }
    const present = records.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length;
    return (
        <div className="flex flex-col gap-3">
            <p>
                Obecność: {present} z {records.length} zajęć ({Math.round((present / records.length) * 100)}%)
            </p>
            <Panel title="Lista obecności" flush>
                <Table>
                    <thead>
                    <tr>
                        <Th>Data</Th>
                        <Th>Godzina</Th>
                        <Th>Status</Th>
                    </tr>
                    </thead>
                    <tbody>
                    {records.map((r) => (
                        <Tr key={r.sessionId}>
                            <Td>{formatDate(r.date)}</Td>
                            <Td>
                                {formatTime(r.startTime)}–{formatTime(r.endTime)}
                            </Td>
                            <Td className={`font-semibold ${ATTENDANCE_CLASSES[r.status]}`}>{ATTENDANCE_LABELS[r.status]}</Td>
                        </Tr>
                    ))}
                    </tbody>
                </Table>
            </Panel>
        </div>
    );
}
