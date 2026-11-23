import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import type { PasswordReset } from '../api/types';
import { errorMessage } from '../i18n/errors';
import { Alert } from './ui/Alert';
import { Button } from './ui/Button';
import { Dialog } from './ui/Dialog';

/**
 * Nadanie nowego hasła startowego (administrator – dowolne konto, dziekanat – student wydziału).
 * Hasło generuje serwer i pokazuje je tylko raz; wszystkie sesje konta zostają zakończone.
 */
export function ResetPasswordDialog({
    endpoint,
    person,
    onClose,
    onDone,
}: {
    /** adres POST, np. /api/admin/users/{id}/reset-password */
    endpoint: string;
    /** kto dostaje nowe hasło: imię i nazwisko oraz e-mail lub numer albumu */
    person: { name: string; detail: string };
    onClose: () => void;
    onDone?: () => void;
}) {
    const [copied, setCopied] = useState(false);
    const reset = useMutation({
        mutationFn: () => api<PasswordReset>(endpoint, { method: 'POST' }),
        onSuccess: () => onDone?.(),
    });

    async function copy(text: string) {
        try {
            await navigator.clipboard.writeText(text);
            setCopied(true);
        } catch {
            setCopied(false);
        }
    }

    return (
        <Dialog open onClose={onClose} title="Nowe hasło startowe">
            <div className="flex flex-col gap-4">
                {reset.data ? (
                    <>
                        <Alert variant="success">
                            Nowe hasło dla <strong>{reset.data.email}</strong>:{' '}
                            <code className="font-mono font-semibold whitespace-nowrap">{reset.data.initialPassword}</code>{' '}
                            <button
                                type="button"
                                onClick={() => copy(reset.data.initialPassword)}
                                className="ml-1 font-semibold underline underline-offset-2"
                            >
                                {copied ? 'skopiowano' : 'kopiuj'}
                            </button>
                        </Alert>
                        <p className="text-sm text-muted">
                            Hasło widać tylko teraz. Przekaż je osobiście lub inną bezpieczną drogą, nigdy w tym samym mailu
                            co login. Przy logowaniu system poprosi o ustawienie własnego hasła.
                        </p>
                        <div className="flex justify-end">
                            <Button type="button" autoFocus onClick={onClose}>
                                Gotowe
                            </Button>
                        </div>
                    </>
                ) : (
                    <>
                        <p>
                            Nadać nowe hasło startowe dla <strong>{person.name}</strong> ({person.detail})?
                        </p>
                        <p className="text-sm text-muted">
                            Dotychczasowe hasło przestanie działać, a wszystkie sesje tego konta zostaną od razu zakończone.
                            Zmiana trafi do dziennika zdarzeń.
                        </p>
                        {reset.isError && <Alert>{errorMessage(reset.error)}</Alert>}
                        <div className="flex justify-end gap-2">
                            <Button type="button" variant="secondary" onClick={onClose}>
                                Anuluj
                            </Button>
                            <Button type="button" autoFocus loading={reset.isPending} onClick={() => reset.mutate()}>
                                Nadaj nowe hasło
                            </Button>
                        </div>
                    </>
                )}
            </div>
        </Dialog>
    );
}
