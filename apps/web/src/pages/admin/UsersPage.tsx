import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api } from '../../api/client';
import type { AdminUser, Faculty, Role } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { ResetPasswordDialog } from '../../components/ResetPasswordDialog';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Select } from '../../components/ui/Select';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { TextField } from '../../components/ui/TextField';
import { errorMessage } from '../../i18n/errors';
import { formatInstant } from '../../i18n/format';
import { ROLE_LABELS } from '../../i18n/labels';
import { studentEmail } from '../../lib/studentImport';

const USERS_KEY = ['admin', 'users'];
const ROLES = Object.keys(ROLE_LABELS) as Role[];

// bez znaków łatwych do pomylenia przy przepisywaniu (0/O, 1/l/I)
const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-_!?';

/** Hasło startowe z kryptograficznego generatora; losowanie z odrzucaniem, żeby nie faworyzować znaków. */
function generatePassword(length = 16): string {
    const limit = 256 - (256 % PASSWORD_ALPHABET.length);
    const result: string[] = [];
    while (result.length < length) {
        for (const byte of crypto.getRandomValues(new Uint8Array(length * 2))) {
            if (byte < limit && result.length < length) {
                result.push(PASSWORD_ALPHABET[byte % PASSWORD_ALPHABET.length]);
            }
        }
    }
    return result.join('');
}

export function UsersPage() {
    const { user: me } = useAuth();
    const queryClient = useQueryClient();
    const users = useQuery({ queryKey: USERS_KEY, queryFn: () => api<AdminUser[]>('/api/admin/users') });
    const faculties = useQuery({ queryKey: ['faculties'], queryFn: () => api<Faculty[]>('/api/faculties') });
    const [search, setSearch] = useState('');
    const [role, setRole] = useState<Role | ''>('');
    const [deactivating, setDeactivating] = useState<AdminUser | null>(null);
    const [resetting, setResetting] = useState<AdminUser | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    const facultyName = (id: string | null) => faculties.data?.find((f) => f.id === id)?.shortName ?? '';

    return (
        <>
            <PageHeader title="Użytkownicy" crumbs={[{ label: 'Użytkownicy' }]} />
            <div className="flex flex-col gap-6">
                <NewUserForm faculties={faculties.data ?? []} />

                <QueryState query={users}>
                    {(list) => {
                        const phrase = search.trim().toLowerCase();
                        const visible = list.filter(
                            (u) =>
                                (!role || u.role === role) &&
                                (!phrase ||
                                    `${u.lastName} ${u.firstName} ${u.email} ${u.albumNumber ?? ''}`
                                        .toLowerCase()
                                        .includes(phrase)),
                        );
                        return (
                            <div className="flex flex-col gap-4">
                                <div className="grid gap-4 sm:grid-cols-[2fr_1fr]">
                                    <TextField
                                        label="Szukaj"
                                        placeholder="nazwisko, imię, e-mail lub numer albumu"
                                        value={search}
                                        onChange={(e) => setSearch(e.currentTarget.value)}
                                    />
                                    <Select label="Rola" value={role} onChange={(e) => setRole(e.currentTarget.value as Role | '')}>
                                        <option value="">wszystkie</option>
                                        {ROLES.map((r) => (
                                            <option key={r} value={r}>
                                                {ROLE_LABELS[r]}
                                            </option>
                                        ))}
                                    </Select>
                                </div>
                                {notice && <Alert variant="success">{notice}</Alert>}
                                <Panel
                                    title="Konta"
                                    flush
                                    aside={<span className="text-sm">{visible.length === list.length ? list.length : `${visible.length} z ${list.length}`}</span>}
                                >
                                    <Table minWidth={820}>
                                        <thead>
                                        <tr>
                                            <Th>Użytkownik</Th>
                                            <Th>Rola</Th>
                                            <Th>Album / wydział</Th>
                                            <Th>Utworzono</Th>
                                            <Th>Status</Th>
                                            <Th />
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {visible.map((u) => (
                                            <Tr key={u.id}>
                                                <Td>
                                                    {u.lastName} {u.firstName}
                                                    <div className="text-xs text-muted">{u.email}</div>
                                                </Td>
                                                <Td>{ROLE_LABELS[u.role]}</Td>
                                                <Td>{u.albumNumber ?? facultyName(u.facultyId)}</Td>
                                                <Td>{formatInstant(u.createdAt)}</Td>
                                                <Td className={u.active ? '' : 'font-semibold text-red-700'}>
                                                    {u.active ? 'aktywne' : 'zablokowane'}
                                                    {u.active && u.mustChangePassword && (
                                                        <div className="text-xs font-normal text-muted">hasło startowe</div>
                                                    )}
                                                </Td>
                                                <Td className="text-right whitespace-nowrap">
                                                    <Link
                                                        to={`/admin/audit?entityType=user&entityId=${u.id}`}
                                                        className="mr-3 text-sm font-semibold text-navy underline-offset-2 hover:underline"
                                                    >
                                                        Historia
                                                    </Link>
                                                    {u.active && u.id !== me?.id && (
                                                        <Button
                                                            type="button"
                                                            variant="secondary"
                                                            className="mr-2 px-3 py-1 text-sm"
                                                            onClick={() => {
                                                                setNotice(null);
                                                                setResetting(u);
                                                            }}
                                                        >
                                                            Nowe hasło
                                                        </Button>
                                                    )}
                                                    {u.active && u.id !== me?.id && (
                                                        <Button
                                                            type="button"
                                                            variant="secondary"
                                                            className="px-3 py-1 text-sm"
                                                            onClick={() => {
                                                                setNotice(null);
                                                                setDeactivating(u);
                                                            }}
                                                        >
                                                            Zablokuj
                                                        </Button>
                                                    )}
                                                </Td>
                                            </Tr>
                                        ))}
                                        </tbody>
                                    </Table>
                                </Panel>
                            </div>
                        );
                    }}
                </QueryState>
            </div>

            {resetting && (
                <ResetPasswordDialog
                    endpoint={`/api/admin/users/${encodeURIComponent(resetting.id)}/reset-password`}
                    person={{ name: `${resetting.firstName} ${resetting.lastName}`, detail: resetting.email }}
                    onClose={() => setResetting(null)}
                    onDone={() => queryClient.invalidateQueries({ queryKey: USERS_KEY })}
                />
            )}
            {deactivating && (
                <DeactivateDialog
                    user={deactivating}
                    onClose={() => setDeactivating(null)}
                    onDone={() => setNotice(`Zablokowano konto: ${deactivating.email}.`)}
                />
            )}
        </>
    );
}

// ---------- nowe konto ----------

function NewUserForm({ faculties }: { faculties: Faculty[] }) {
    const queryClient = useQueryClient();
    const [role, setRole] = useState<Role>('STUDENT');
    const [firstName, setFirstName] = useState('');
    const [lastName, setLastName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState(generatePassword);
    const [albumNumber, setAlbumNumber] = useState('');
    const [facultyId, setFacultyId] = useState('');
    const [academicTitle, setAcademicTitle] = useState('');
    const [created, setCreated] = useState<{ email: string; password: string } | null>(null);
    const [copied, setCopied] = useState(false);

    const needsFaculty = role === 'LECTURER' || role === 'DEAN_OFFICE';
    const isStudent = role === 'STUDENT';
    // student dostaje adres uczelniany automatycznie – podgląd tego, co utworzy serwer
    const generatedEmail =
        isStudent && firstName.trim() && lastName.trim() && albumNumber.trim()
            ? studentEmail(firstName, lastName, albumNumber)
            : null;

    const create = useMutation({
        mutationFn: () =>
            api<AdminUser>('/api/admin/users', {
                method: 'POST',
                body: JSON.stringify({
                    firstName,
                    lastName,
                    email: isStudent ? null : email,
                    role,
                    password,
                    albumNumber: isStudent ? albumNumber : null,
                    facultyId: needsFaculty ? facultyId : null,
                    academicTitle: role === 'LECTURER' && academicTitle ? academicTitle : null,
                }),
            }),
        onSuccess: async (user) => {
            await queryClient.invalidateQueries({ queryKey: USERS_KEY });
            setCreated({ email: user.email, password });
            setCopied(false);
            setFirstName('');
            setLastName('');
            setEmail('');
            setAlbumNumber('');
            setAcademicTitle('');
            setPassword(generatePassword());
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setCreated(null);
        create.mutate();
    }

    async function copy(text: string) {
        try {
            await navigator.clipboard.writeText(text);
            setCopied(true);
        } catch {
            setCopied(false);
        }
    }

    return (
        <Panel title="Nowe konto">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div className="grid gap-4 sm:grid-cols-3">
                    <Select label="Rola" required value={role} onChange={(e) => setRole(e.currentTarget.value as Role)}>
                        {ROLES.map((r) => (
                            <option key={r} value={r}>
                                {ROLE_LABELS[r]}
                            </option>
                        ))}
                    </Select>
                    <TextField label="Imię" required maxLength={100} value={firstName} onChange={(e) => setFirstName(e.currentTarget.value)} />
                    <TextField label="Nazwisko" required maxLength={100} value={lastName} onChange={(e) => setLastName(e.currentTarget.value)} />
                    {isStudent ? (
                        <>
                            <TextField
                                label="Numer albumu"
                                required
                                maxLength={20}
                                pattern="[0-9A-Za-z/\-]{1,20}"
                                value={albumNumber}
                                onChange={(e) => setAlbumNumber(e.currentTarget.value)}
                            />
                            <div>
                                <span className="mb-1 block text-sm font-semibold">E-mail (login)</span>
                                <p className="rounded-sm border border-dashed border-line px-3 py-2 text-[15px]" aria-live="polite">
                                    {generatedEmail ?? (
                                        <span className="text-muted">utworzy się z inicjałów i numeru albumu</span>
                                    )}
                                </p>
                            </div>
                        </>
                    ) : (
                        <TextField
                            label="Adres e-mail"
                            type="email"
                            required
                            maxLength={255}
                            autoComplete="off"
                            value={email}
                            onChange={(e) => setEmail(e.currentTarget.value)}
                        />
                    )}
                    {needsFaculty && (
                        <Select label="Wydział" required value={facultyId} onChange={(e) => setFacultyId(e.currentTarget.value)}>
                            <option value="">wybierz</option>
                            {faculties.map((f) => (
                                <option key={f.id} value={f.id}>
                                    {f.name}
                                </option>
                            ))}
                        </Select>
                    )}
                    {role === 'LECTURER' && (
                        <TextField
                            label="Tytuł naukowy"
                            maxLength={50}
                            placeholder="np. dr inż."
                            value={academicTitle}
                            onChange={(e) => setAcademicTitle(e.currentTarget.value)}
                        />
                    )}
                </div>

                <div className="flex flex-wrap items-end gap-3">
                    <div className="min-w-64 flex-1 sm:max-w-sm">
                        <TextField
                            label="Hasło startowe"
                            required
                            minLength={12}
                            maxLength={72}
                            autoComplete="new-password"
                            spellCheck={false}
                            value={password}
                            onChange={(e) => setPassword(e.currentTarget.value)}
                        />
                    </div>
                    <Button type="button" variant="secondary" onClick={() => setPassword(generatePassword())}>
                        Wygeneruj nowe
                    </Button>
                </div>
                <p className="text-sm text-muted">
                    Hasło jest wygenerowane losowo. Przekaż je użytkownikowi osobiście lub inną bezpieczną drogą, nigdy
                    w tym samym mailu co adres logowania. Przy pierwszym logowaniu użytkownik musi ustawić własne hasło.
                </p>

                {created && (
                    <Alert variant="success">
                        Utworzono konto <strong>{created.email}</strong>. Hasło startowe:{' '}
                        <code className="font-mono font-semibold">{created.password}</code>{' '}
                        <button
                            type="button"
                            onClick={() => copy(created.password)}
                            className="ml-1 font-semibold underline underline-offset-2"
                        >
                            {copied ? 'skopiowano' : 'kopiuj'}
                        </button>
                        <br />
                        Zapisz je teraz – po opuszczeniu strony nie będzie można go ponownie wyświetlić.
                    </Alert>
                )}
                {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
                <div>
                    <Button type="submit" loading={create.isPending}>
                        Utwórz konto
                    </Button>
                </div>
            </form>
        </Panel>
    );
}

// ---------- blokada konta ----------

function DeactivateDialog({ user, onClose, onDone }: { user: AdminUser; onClose: () => void; onDone: () => void }) {
    const queryClient = useQueryClient();
    const deactivate = useMutation({
        mutationFn: () => api(`/api/admin/users/${encodeURIComponent(user.id)}/deactivate`, { method: 'POST' }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: USERS_KEY });
            onDone();
            onClose();
        },
    });

    return (
        <Dialog open onClose={onClose} title="Blokada konta">
            <div className="flex flex-col gap-4">
                <p>
                    Zablokować konto{' '}
                    <strong>
                        {user.firstName} {user.lastName}
                    </strong>{' '}
                    ({user.email})?
                </p>
                <p className="text-sm text-muted">
                    Użytkownik nie zaloguje się ponownie, a jego aktywne sesje zostaną natychmiast zakończone na wszystkich
                    urządzeniach. Dane i oceny pozostają w systemie.
                </p>
                {deactivate.isError && <Alert>{errorMessage(deactivate.error)}</Alert>}
                <div className="flex justify-end gap-2">
                    <Button type="button" variant="secondary" onClick={onClose}>
                        Anuluj
                    </Button>
                    <Button type="button" autoFocus loading={deactivate.isPending} onClick={() => deactivate.mutate()}>
                        Zablokuj konto
                    </Button>
                </div>
            </div>
        </Dialog>
    );
}
