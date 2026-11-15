import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { api } from '../../api/client';
import type { AdminUser, AuditAction, AuditEntry } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { ATTEMPT_LABELS, ATTENDANCE_LABELS, ROLE_LABELS, STUDY_STATUS_LABELS } from '../../i18n/labels';

const PAGE_SIZE = 50;

const ENTITY_LABELS: Record<string, string> = {
    user: 'Konto',
    study: 'Przebieg studiów',
    faculty: 'Wydział',
    field_of_study: 'Kierunek',
    course: 'Przedmiot',
    academic_term: 'Semestr',
    room: 'Sala',
    class_group: 'Grupa zajęciowa',
    group_enrollment: 'Zapis do grupy',
    assessment_item: 'Element zaliczenia',
    partial_grade: 'Ocena cząstkowa',
    final_grade: 'Ocena końcowa',
    class_session: 'Zajęcia',
    attendance: 'Obecność',
};

const ACTION_LABELS: Record<AuditAction, string> = {
    CREATE: 'utworzenie',
    UPDATE: 'zmiana',
    DELETE: 'usunięcie',
};

const FIELD_LABELS: Record<string, string> = {
    firstName: 'imię',
    lastName: 'nazwisko',
    email: 'e-mail',
    role: 'rola',
    active: 'aktywne',
    albumNumber: 'album',
    grade: 'ocena',
    attempt: 'próba',
    status: 'status',
    reason: 'powód',
    name: 'nazwa',
    weightPercent: 'waga',
    currentSemester: 'semestr',
    correctedBy: 'skorygował(a)',
    approvedBy: 'zatwierdził(a)',
    issuedBy: 'wystawił(a)',
    recordedBy: 'wpisał(a)',
};

/** Wartości wyliczeniowe z backendu po polsku – reszta bez zmian. */
const VALUE_LABELS: Record<string, string> = {
    ...ROLE_LABELS,
    ...ATTEMPT_LABELS,
    ...ATTENDANCE_LABELS,
    ...STUDY_STATUS_LABELS,
    DRAFT: 'szkic',
    APPROVED: 'zatwierdzona',
};

const dateTime = new Intl.DateTimeFormat('pl-PL', { dateStyle: 'short', timeStyle: 'medium' });
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function AuditPage() {
    const [params, setParams] = useSearchParams();
    const entityType = params.get('entityType') ?? '';
    const entityId = params.get('entityId');
    const actorId = params.get('actorId') ?? '';

    const users = useQuery({ queryKey: ['admin', 'users'], queryFn: () => api<AdminUser[]>('/api/admin/users') });
    const nameOf = (id: string | null) => {
        if (!id) {
            return 'system';
        }
        const u = users.data?.find((x) => x.id === id);
        return u ? `${u.lastName} ${u.firstName}` : `${id.slice(0, 8)}…`;
    };
    const knownUser = (id: string) => users.data?.some((u) => u.id === id) ?? false;
    const resolve = (id: string) => (knownUser(id) ? nameOf(id) : undefined);

    function setFilter(key: string, value: string) {
        const next = new URLSearchParams(params);
        if (value) {
            next.set(key, value);
        } else {
            next.delete(key);
        }
        next.delete('entityId');
        setParams(next);
    }

    return (
        <>
            <PageHeader title="Dziennik zdarzeń" crumbs={[{ label: 'Dziennik zdarzeń' }]} />
            <p className="-mt-3 mb-6 max-w-3xl text-muted">
                Każda zmiana danych zapisuje się tu automatycznie, razem z autorem i wartościami przed i po. Wpisów nie
                można edytować ani usuwać – blokuje to sama baza danych.
            </p>

            {entityId ? (
                <ObjectHistory entityType={entityType} entityId={entityId} nameOf={nameOf} resolve={resolve} />
            ) : (
                <div className="flex flex-col gap-4">
                    <div className="grid gap-4 sm:grid-cols-2 md:max-w-2xl">
                        <Select label="Rodzaj obiektu" value={entityType} onChange={(e) => setFilter('entityType', e.currentTarget.value)}>
                            <option value="">wszystkie</option>
                            {Object.entries(ENTITY_LABELS).map(([key, label]) => (
                                <option key={key} value={key}>
                                    {label}
                                </option>
                            ))}
                        </Select>
                        <Select label="Kto" value={actorId} onChange={(e) => setFilter('actorId', e.currentTarget.value)}>
                            <option value="">wszyscy</option>
                            {users.data?.map((u) => (
                                <option key={u.id} value={u.id}>
                                    {u.lastName} {u.firstName}
                                </option>
                            ))}
                        </Select>
                    </div>
                    <RecentEntries entityType={entityType} actorId={actorId} nameOf={nameOf} resolve={resolve} />
                </div>
            )}
        </>
    );
}

type Resolve = (id: string) => string | undefined;

function RecentEntries({
    entityType,
    actorId,
    nameOf,
    resolve,
}: {
    entityType: string;
    actorId: string;
    nameOf: (id: string | null) => string;
    resolve: Resolve;
}) {
    const entries = useInfiniteQuery({
        queryKey: ['admin', 'audit', entityType, actorId],
        initialPageParam: null as AuditEntry | null,
        queryFn: ({ pageParam }) => {
            const query = new URLSearchParams({ limit: String(PAGE_SIZE) });
            if (entityType) query.set('entityType', entityType);
            if (actorId) query.set('actorId', actorId);
            if (pageParam) {
                query.set('beforeCreatedAt', pageParam.createdAt);
                query.set('beforeId', pageParam.id);
            }
            return api<AuditEntry[]>(`/api/admin/audit-log/recent?${query}`);
        },
        getNextPageParam: (lastPage) => (lastPage.length === PAGE_SIZE ? lastPage.at(-1) : undefined),
    });
    const list = entries.data?.pages.flat();

    return (
        <QueryState query={{ isPending: entries.isPending, error: entries.error, data: list }}>
            {(rows) => (
                <Panel title="Najnowsze zdarzenia" flush>
                    <EntriesTable rows={rows} nameOf={nameOf} resolve={resolve} showObject />
                    {entries.hasNextPage && (
                        <div className="border-t border-line px-4 py-3">
                            <Button
                                type="button"
                                variant="secondary"
                                loading={entries.isFetchingNextPage}
                                onClick={() => entries.fetchNextPage()}
                            >
                                Wczytaj starsze
                            </Button>
                        </div>
                    )}
                </Panel>
            )}
        </QueryState>
    );
}

function ObjectHistory({
    entityType,
    entityId,
    nameOf,
    resolve,
}: {
    entityType: string;
    entityId: string;
    nameOf: (id: string | null) => string;
    resolve: Resolve;
}) {
    const history = useQuery({
        queryKey: ['admin', 'audit', 'object', entityType, entityId],
        queryFn: () =>
            api<AuditEntry[]>(
                `/api/admin/audit-log?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`,
            ),
    });

    return (
        <div className="flex flex-col gap-4">
            <div>
                <Link to="/admin/audit" className="text-sm font-semibold text-navy underline-offset-2 hover:underline">
                    Pokaż cały dziennik
                </Link>
            </div>
            <QueryState query={history}>
                {(rows) => (
                    <Panel title={`Historia: ${ENTITY_LABELS[entityType] ?? entityType}`} flush>
                        <EntriesTable rows={rows} nameOf={nameOf} resolve={resolve} showObject={false} />
                    </Panel>
                )}
            </QueryState>
        </div>
    );
}

function EntriesTable({
    rows,
    nameOf,
    resolve,
    showObject,
}: {
    rows: AuditEntry[];
    nameOf: (id: string | null) => string;
    resolve: Resolve;
    showObject: boolean;
}) {
    if (rows.length === 0) {
        return <p className="p-4 text-muted">Brak zdarzeń.</p>;
    }
    return (
        <Table minWidth={820}>
            <thead>
            <tr>
                <Th>Data i godzina</Th>
                <Th>Kto</Th>
                <Th>Zdarzenie</Th>
                <Th>Szczegóły</Th>
            </tr>
            </thead>
            <tbody>
            {rows.map((entry) => (
                <Tr key={entry.id}>
                    <Td className="whitespace-nowrap">{dateTime.format(new Date(entry.createdAt))}</Td>
                    <Td>{nameOf(entry.actorId)}</Td>
                    <Td>
                        {showObject ? (
                            <Link
                                to={`/admin/audit?entityType=${entry.entityType}&entityId=${entry.entityId}`}
                                className="font-semibold text-navy underline-offset-2 hover:underline"
                            >
                                {ENTITY_LABELS[entry.entityType] ?? entry.entityType}
                            </Link>
                        ) : null}
                        <div className={showObject ? 'text-xs text-muted' : ''}>{ACTION_LABELS[entry.action]}</div>
                    </Td>
                    <Td className="text-sm">
                        <Changes entry={entry} resolve={resolve} />
                    </Td>
                </Tr>
            ))}
            </tbody>
        </Table>
    );
}

/** Przy zmianie pokazuje tylko pola, które się zmieniły; przy utworzeniu – wszystkie. */
function Changes({ entry, resolve }: { entry: AuditEntry; resolve: Resolve }) {
    const before = entry.oldValue ?? {};
    const after = entry.newValue ?? {};
    const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])].filter(
        (key) => entry.action !== 'UPDATE' || JSON.stringify(before[key]) !== JSON.stringify(after[key]),
    );
    if (keys.length === 0) {
        return <span className="text-muted">bez zmian</span>;
    }
    return (
        <ul className="flex flex-col gap-0.5">
            {keys.map((key) => (
                <li key={key}>
                    <span className="text-muted">{FIELD_LABELS[key] ?? key}:</span>{' '}
                    {entry.action === 'UPDATE' && key in before && (
                        <>
                            <Value value={before[key]} resolve={resolve} /> →{' '}
                        </>
                    )}
                    <Value value={key in after ? after[key] : null} resolve={resolve} />
                </li>
            ))}
        </ul>
    );
}

function Value({ value, resolve }: { value: unknown; resolve: Resolve }) {
    if (value === null || value === undefined) {
        return <span className="text-muted">–</span>;
    }
    if (typeof value === 'boolean') {
        return <>{value ? 'tak' : 'nie'}</>;
    }
    const text = String(value);
    if (UUID_PATTERN.test(text) && resolve(text)) {
        return <span className="font-semibold">{resolve(text)}</span>;
    }
    if (VALUE_LABELS[text]) {
        return <span className="font-semibold">{VALUE_LABELS[text]}</span>;
    }
    if (UUID_PATTERN.test(text)) {
        return (
            <span title={text} className="font-mono text-xs">
                {text.slice(0, 8)}…
            </span>
        );
    }
    return <span className="font-semibold">{text}</span>;
}
