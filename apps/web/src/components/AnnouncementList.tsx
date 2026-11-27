import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Announcement } from '../api/types';
import { errorMessage } from '../i18n/errors';
import { formatInstant } from '../i18n/format';
import { ANNOUNCEMENTS_KEY, targetLabel, wasEdited } from '../lib/announcements';
import { Alert } from './ui/Alert';
import { Button } from './ui/Button';
import { Dialog } from './ui/Dialog';
import { TextField } from './ui/TextField';

/**
 * Lista ogłoszeń. Treść jest wstawiana jako tekst (React zamienia znaki „<”, „>” itd.), więc
 * kod HTML w ogłoszeniu nigdy się nie wykona. `compact` – skrócona treść na stronie głównej.
 */
export function AnnouncementList({ items, compact = false }: { items: Announcement[]; compact?: boolean }) {
    const [editing, setEditing] = useState<Announcement | null>(null);
    const [deleting, setDeleting] = useState<Announcement | null>(null);

    if (items.length === 0) {
        return <p className="text-muted">Nie ma ogłoszeń.</p>;
    }
    return (
        <>
            <ul className="flex flex-col divide-y divide-line">
                {items.map((a) => (
                    <li key={a.id} className="py-3 first:pt-0 last:pb-0">
                        <article>
                            <h3 className="font-semibold break-words">{a.title}</h3>
                            <p className="text-sm text-muted">
                                {targetLabel(a.target)}, {a.authorName}, {formatInstant(a.createdAt)}
                                {wasEdited(a) && <> (edytowano {formatInstant(a.updatedAt)})</>}
                            </p>
                            <p className={`mt-1 break-words whitespace-pre-line ${compact ? 'line-clamp-3' : ''}`}>
                                {a.content}
                            </p>
                            {!compact && (a.canEdit || a.canDelete) && (
                                <div className="mt-2 flex gap-2">
                                    {a.canEdit && (
                                        <Button
                                            type="button"
                                            variant="secondary"
                                            className="px-3 py-1 text-sm"
                                            onClick={() => setEditing(a)}
                                        >
                                            Popraw
                                        </Button>
                                    )}
                                    {a.canDelete && (
                                        <Button
                                            type="button"
                                            variant="secondary"
                                            className="px-3 py-1 text-sm"
                                            onClick={() => setDeleting(a)}
                                        >
                                            Usuń
                                        </Button>
                                    )}
                                </div>
                            )}
                        </article>
                    </li>
                ))}
            </ul>
            {editing && <EditDialog announcement={editing} onClose={() => setEditing(null)} />}
            {deleting && <DeleteDialog announcement={deleting} onClose={() => setDeleting(null)} />}
        </>
    );
}

function EditDialog({ announcement, onClose }: { announcement: Announcement; onClose: () => void }) {
    const queryClient = useQueryClient();
    const [title, setTitle] = useState(announcement.title);
    const [content, setContent] = useState(announcement.content);
    const save = useMutation({
        mutationFn: () =>
            api(`/api/announcements/${encodeURIComponent(announcement.id)}`, {
                method: 'PUT',
                body: JSON.stringify({ title, content }),
            }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: ANNOUNCEMENTS_KEY });
            onClose();
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        save.mutate();
    }

    return (
        <Dialog open onClose={onClose} title="Popraw ogłoszenie">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <p className="text-sm text-muted">Odbiorcy: {targetLabel(announcement.target)} (nie zmieniają się).</p>
                <TextField label="Tytuł" required maxLength={200} value={title} onChange={(e) => setTitle(e.currentTarget.value)} />
                <ContentField value={content} onChange={setContent} />
                {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
                <div className="flex justify-end gap-2">
                    <Button type="button" variant="secondary" onClick={onClose}>
                        Anuluj
                    </Button>
                    <Button type="submit" loading={save.isPending} disabled={!title.trim() || !content.trim()}>
                        Zapisz poprawkę
                    </Button>
                </div>
            </form>
        </Dialog>
    );
}

function DeleteDialog({ announcement, onClose }: { announcement: Announcement; onClose: () => void }) {
    const queryClient = useQueryClient();
    const remove = useMutation({
        mutationFn: () => api(`/api/announcements/${encodeURIComponent(announcement.id)}`, { method: 'DELETE' }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: ANNOUNCEMENTS_KEY });
            onClose();
        },
    });

    return (
        <Dialog open onClose={onClose} title="Usuń ogłoszenie">
            <div className="flex flex-col gap-4">
                <p>
                    Usunąć ogłoszenie <strong>{announcement.title}</strong>? Odbiorcy przestaną je widzieć, a usunięcie trafi
                    do dziennika zdarzeń.
                </p>
                {remove.isError && <Alert>{errorMessage(remove.error)}</Alert>}
                <div className="flex justify-end gap-2">
                    <Button type="button" variant="secondary" onClick={onClose}>
                        Anuluj
                    </Button>
                    <Button type="button" autoFocus loading={remove.isPending} onClick={() => remove.mutate()}>
                        Usuń ogłoszenie
                    </Button>
                </div>
            </div>
        </Dialog>
    );
}

/** Pole treści z licznikiem znaków (limit jak na serwerze). */
export function ContentField({ value, onChange }: { value: string; onChange: (value: string) => void }) {
    return (
        <div>
            <label htmlFor="announcement-content" className="mb-1 block text-sm font-semibold">
                Treść
                <span aria-hidden="true" className="text-red-700">
                    {' '}*
                </span>
            </label>
            <textarea
                id="announcement-content"
                required
                rows={6}
                maxLength={5000}
                value={value}
                onChange={(e) => onChange(e.currentTarget.value)}
                className="w-full rounded-sm border border-line bg-white px-3 py-2 text-[15px] outline-none focus:border-navy focus:ring-2 focus:ring-navy/20"
            />
            <p className="mt-1 text-right text-xs text-muted">{value.length} / 5000</p>
        </div>
    );
}
