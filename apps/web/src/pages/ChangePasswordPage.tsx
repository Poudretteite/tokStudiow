import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { PageHeader } from '../components/PageHeader';
import { Panel } from '../components/Panel';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { TextField } from '../components/ui/TextField';
import { errorMessage } from '../i18n/errors';
import { isPasswordAcceptable, passwordRules } from '../lib/passwordPolicy';

/**
 * Zmiana własnego hasła. Z `forced` – po zalogowaniu hasłem startowym, zanim użytkownik
 * dostanie dostęp do czegokolwiek innego (serwer blokuje resztę API do czasu zmiany).
 */
export function ChangePasswordPage({ forced = false }: { forced?: boolean }) {
    const { user, changePassword } = useAuth();
    const [current, setCurrent] = useState('');
    const [next, setNext] = useState('');
    const [repeat, setRepeat] = useState('');
    const [visible, setVisible] = useState(false);
    const [pending, setPending] = useState(false);
    const [error, setError] = useState<unknown>(null);
    const [done, setDone] = useState(false);

    if (!user) {
        return null;
    }
    const rules = passwordRules(next, user);
    const mismatch = repeat !== '' && repeat !== next;
    const canSubmit = current !== '' && isPasswordAcceptable(next, user) && next === repeat && next !== current;

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setPending(true);
        setError(null);
        try {
            await changePassword(current, next);
            setCurrent('');
            setNext('');
            setRepeat('');
            setDone(true);
        } catch (e) {
            setError(e);
        } finally {
            setPending(false);
        }
    }

    const type = visible ? 'text' : 'password';

    return (
        <>
            <PageHeader title="Zmiana hasła" crumbs={forced ? [] : [{ label: 'Zmiana hasła' }]} />
            <div className="max-w-xl">
                {forced && (
                    <div className="mb-4">
                        <Alert>
                            Logujesz się hasłem startowym, które zna osoba zakładająca konto. Ustaw własne hasło, żeby
                            korzystać z systemu.
                        </Alert>
                    </div>
                )}
                {done && (
                    <div className="mb-4">
                        <Alert variant="success">
                            Hasło zostało zmienione. Na innych urządzeniach trzeba zalogować się ponownie.
                        </Alert>
                    </div>
                )}
                <Panel title="Nowe hasło">
                    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                        {/* ukryte pole z loginem – menedżer haseł wie, do którego konta zapisać nowe hasło */}
                        <input type="email" name="username" autoComplete="username" value={user.email} readOnly hidden />
                        <TextField
                            label={forced ? 'Hasło startowe' : 'Obecne hasło'}
                            type={type}
                            required
                            autoFocus
                            autoComplete="current-password"
                            maxLength={200}
                            value={current}
                            onChange={(e) => setCurrent(e.currentTarget.value)}
                        />
                        <div>
                            <TextField
                                label="Nowe hasło"
                                type={type}
                                required
                                autoComplete="new-password"
                                maxLength={200}
                                spellCheck={false}
                                aria-describedby="password-rules"
                                value={next}
                                onChange={(e) => setNext(e.currentTarget.value)}
                            />
                            <ul id="password-rules" className="mt-2 flex flex-col gap-0.5 text-sm">
                                {rules.map((rule) => (
                                    <li key={rule.id} className={rule.ok ? 'text-green-800' : 'text-muted'}>
                                        <span aria-hidden="true">{rule.ok ? '✓' : '○'}</span> {rule.label}
                                        <span className="sr-only">{rule.ok ? ' – spełnione' : ' – niespełnione'}</span>
                                    </li>
                                ))}
                                {next !== '' && next === current && (
                                    <li className="text-red-700">nowe hasło musi różnić się od obecnego</li>
                                )}
                            </ul>
                        </div>
                        <div>
                            <TextField
                                label="Powtórz nowe hasło"
                                type={type}
                                required
                                autoComplete="new-password"
                                maxLength={200}
                                value={repeat}
                                onChange={(e) => setRepeat(e.currentTarget.value)}
                            />
                            {mismatch && <p className="mt-1 text-sm text-red-700">Hasła się różnią.</p>}
                        </div>
                        <label className="flex items-center gap-2 text-sm">
                            <input type="checkbox" checked={visible} onChange={(e) => setVisible(e.currentTarget.checked)} />
                            Pokaż hasła
                        </label>
                        <p className="text-sm text-muted">
                            Najłatwiej o mocne hasło z kilku słów, np. „zielony-rower-nad-Wisłą”. Nie musi zawierać cyfr
                            ani znaków specjalnych – liczy się długość. Po zmianie inne urządzenia zostaną wylogowane.
                        </p>
                        {error !== null && <Alert>{errorMessage(error)}</Alert>}
                        <div>
                            <Button type="submit" loading={pending} disabled={!canSubmit}>
                                Zmień hasło
                            </Button>
                        </div>
                    </form>
                </Panel>
            </div>
        </>
    );
}
