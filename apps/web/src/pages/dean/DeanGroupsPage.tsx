import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { api } from '../../api/client';
import type { ClassGroup, Course, FacultyGroup, FieldOfStudy, Lecturer } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { TextField } from '../../components/ui/TextField';
import { errorMessage } from '../../i18n/errors';
import { COMPONENT_LABELS } from '../../i18n/labels';
import { defaultTermId, useAcademicTerms, useFacultyCatalog } from './catalog';

export function DeanGroupsPage() {
    const terms = useAcademicTerms();
    const [chosenTermId, setChosenTermId] = useState<string>();
    const termId = chosenTermId ?? (terms.data ? defaultTermId(terms.data) : undefined);

    return (
        <>
            <PageHeader title="Grupy zajęciowe" crumbs={[{ label: 'Grupy zajęciowe' }]} />
            <QueryState query={terms}>
                {(termList) =>
                    termList.length === 0 || !termId ? (
                        <p className="text-muted">Administrator nie dodał jeszcze semestrów.</p>
                    ) : (
                        <div className="flex flex-col gap-6">
                            <div className="max-w-xs">
                                <Select label="Semestr" value={termId} onChange={(e) => setChosenTermId(e.currentTarget.value)}>
                                    {termList.map((t) => (
                                        <option key={t.id} value={t.id}>
                                            {t.name}
                                            {t.current ? ' (bieżący)' : ''}
                                        </option>
                                    ))}
                                </Select>
                            </div>
                            <TermGroups termId={termId} />
                        </div>
                    )
                }
            </QueryState>
        </>
    );
}

function TermGroups({ termId }: { termId: string }) {
    const groups = useQuery({
        queryKey: ['dean', 'groups', termId],
        queryFn: () => api<FacultyGroup[]>(`/api/dean-office/groups?academicTermId=${encodeURIComponent(termId)}`),
    });

    return (
        <QueryState query={groups}>
            {(list) => (
                <>
                    <Panel title="Grupy w semestrze" flush>
                        {list.length === 0 ? (
                            <p className="p-4 text-muted">W tym semestrze nie ma jeszcze grup. Utwórz pierwszą poniżej.</p>
                        ) : (
                            <Table minWidth={720}>
                                <thead>
                                <tr>
                                    <Th>Przedmiot</Th>
                                    <Th>Forma zajęć</Th>
                                    <Th>Grupa</Th>
                                    <Th>Prowadzący</Th>
                                    <Th className="text-right">Zapisani</Th>
                                </tr>
                                </thead>
                                <tbody>
                                {list.map(({ group, enrolledCount }) => (
                                    <Tr key={group.id}>
                                        <Td>
                                            <Link
                                                to={`/dean/groups/${group.id}`}
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
                                        <Td className="text-right">
                                            {enrolledCount}
                                            {group.capacity !== null && <span className="text-muted"> / {group.capacity}</span>}
                                        </Td>
                                    </Tr>
                                ))}
                                </tbody>
                            </Table>
                        )}
                    </Panel>
                    <NewGroupForm termId={termId} groups={list.map((g) => g.group)} />
                </>
            )}
        </QueryState>
    );
}

interface ComponentOption {
    id: string;
    course: Course;
    label: string;
}

/** Nowa grupa: numer podpowiada się sam (kolejny wolny), po utworzeniu od razu przechodzimy do zapisów. */
function NewGroupForm({ termId, groups }: { termId: string; groups: ClassGroup[] }) {
    const { user } = useAuth();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const catalog = useFacultyCatalog(user?.facultyId ?? null);
    const lecturers = useQuery({
        queryKey: ['dean', 'lecturers'],
        queryFn: () => api<Lecturer[]>('/api/dean-office/lecturers'),
    });

    const [componentId, setComponentId] = useState('');
    const [lecturerId, setLecturerId] = useState('');
    const [groupNumber, setGroupNumber] = useState('');
    const [capacity, setCapacity] = useState('');

    const options = byField(catalog.data?.fields ?? [], catalog.data?.courses ?? []);
    const chosen = options.flatMap((o) => o.options).find((o) => o.id === componentId);
    const usedNumbers = chosen
        ? groups
              .filter((g) => g.courseId === chosen.course.id && g.componentType === componentTypeOf(chosen))
              .map((g) => g.groupNumber)
        : [];
    const nextNumber = String(firstFree(usedNumbers));

    const create = useMutation({
        mutationFn: () =>
            api<ClassGroup>('/api/dean-office/groups', {
                method: 'POST',
                body: JSON.stringify({
                    courseComponentId: componentId,
                    academicTermId: termId,
                    lecturerId,
                    groupNumber: Number(groupNumber || nextNumber),
                    capacity: capacity ? Number(capacity) : null,
                }),
            }),
        onSuccess: async (group) => {
            await queryClient.invalidateQueries({ queryKey: ['dean', 'groups'] });
            navigate(`/dean/groups/${group.id}`);
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        create.mutate();
    }

    return (
        <Panel title="Nowa grupa">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div className="grid gap-4 md:grid-cols-[2fr_1.5fr_0.7fr_0.7fr]">
                    <Select label="Zajęcia" required value={componentId} onChange={(e) => setComponentId(e.currentTarget.value)}>
                        <option value="">wybierz</option>
                        {options.map((field) => (
                            <optgroup key={field.name} label={field.name}>
                                {field.options.map((o) => (
                                    <option key={o.id} value={o.id}>
                                        {o.label}
                                    </option>
                                ))}
                            </optgroup>
                        ))}
                    </Select>
                    <Select label="Prowadzący" required value={lecturerId} onChange={(e) => setLecturerId(e.currentTarget.value)}>
                        <option value="">wybierz</option>
                        {lecturers.data?.map((l) => (
                            <option key={l.id} value={l.id}>
                                {l.lastName} {l.firstName}
                                {l.academicTitle ? `, ${l.academicTitle}` : ''}
                            </option>
                        ))}
                    </Select>
                    <TextField
                        label="Numer grupy"
                        type="number"
                        min={1}
                        max={99}
                        placeholder={nextNumber}
                        value={groupNumber}
                        onChange={(e) => setGroupNumber(e.currentTarget.value)}
                    />
                    <TextField
                        label="Limit miejsc"
                        type="number"
                        min={1}
                        max={500}
                        placeholder="bez limitu"
                        value={capacity}
                        onChange={(e) => setCapacity(e.currentTarget.value)}
                    />
                </div>
                {chosen && (
                    <p className="text-sm text-muted">
                        Numer grupy pozostaw pusty, aby nadać kolejny wolny ({nextNumber}). Po utworzeniu przejdziesz do
                        zapisów studentów.
                    </p>
                )}
                {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
                <div>
                    <Button type="submit" loading={create.isPending}>
                        Utwórz grupę
                    </Button>
                </div>
            </form>
        </Panel>
    );
}

function byField(fields: FieldOfStudy[], courses: Course[]) {
    return fields.map((field) => ({
        name: field.name,
        options: courses
            .filter((c) => c.fieldOfStudyId === field.id)
            .sort((a, b) => a.semesterNumber - b.semesterNumber || a.name.localeCompare(b.name, 'pl'))
            .flatMap((course) =>
                course.components.map(
                    (component): ComponentOption => ({
                        id: component.id,
                        course,
                        label: `${course.name}, ${COMPONENT_LABELS[component.type].toLowerCase()} (${course.code}, sem. ${course.semesterNumber})`,
                    }),
                ),
            ),
    }));
}

function componentTypeOf(option: ComponentOption) {
    return option.course.components.find((c) => c.id === option.id)!.type;
}

function firstFree(used: number[]): number {
    let n = 1;
    while (used.includes(n)) {
        n++;
    }
    return n;
}
