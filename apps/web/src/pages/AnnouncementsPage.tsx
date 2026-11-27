import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { Announcement, AnnouncementTarget } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { AnnouncementList, ContentField } from '../components/AnnouncementList';
import { PageHeader } from '../components/PageHeader';
import { Panel } from '../components/Panel';
import { QueryState } from '../components/QueryState';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Select } from '../components/ui/Select';
import { TextField } from '../components/ui/TextField';
import { errorMessage } from '../i18n/errors';
import { ANNOUNCEMENTS_KEY, sortTargets, targetLabel } from '../lib/announcements';

/** Ogłoszenia: tablica dla każdego, a dla prowadzących, dziekanatu i administratora – także pisanie. */
export function AnnouncementsPage() {
    const { user } = useAuth();
    const feed = useQuery({ queryKey: ANNOUNCEMENTS_KEY, queryFn: () => api<Announcement[]>('/api/announcements') });
    const canWrite = user !== null && user.role !== 'STUDENT';

    return (
        <>
            <PageHeader title="Ogłoszenia" crumbs={[{ label: 'Ogłoszenia' }]} />
            <div className="flex flex-col gap-6">
                {canWrite && <NewAnnouncement />}
                <Panel title="Tablica">
                    <QueryState query={feed}>{(items) => <AnnouncementList items={items} />}</QueryState>
                </Panel>
            </div>
        </>
    );
}

function NewAnnouncement() {
    const queryClient = useQueryClient();
    const targets = useQuery({
        queryKey: ['announcements', 'targets'],
        queryFn: () => api<AnnouncementTarget[]>('/api/announcements/targets'),
    });
    const [targetKey, setTargetKey] = useState('');
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const [notice, setNotice] = useState<string | null>(null);

    const options = sortTargets(targets.data ?? []);
    const keyOf = (t: AnnouncementTarget) => `${t.type}:${t.id ?? ''}`;
    const chosen = options.find((t) => keyOf(t) === targetKey) ?? (options.length === 1 ? options[0] : undefined);

    const publish = useMutation({
        mutationFn: () =>
            api<Announcement>('/api/announcements', {
                method: 'POST',
                body: JSON.stringify({ title, content, targetType: chosen!.type, targetId: chosen!.id }),
            }),
        onSuccess: async (created) => {
            await queryClient.invalidateQueries({ queryKey: ANNOUNCEMENTS_KEY });
            setNotice(`Opublikowano ogłoszenie dla: ${targetLabel(created.target)}.`);
            setTitle('');
            setContent('');
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setNotice(null);
        publish.mutate();
    }

    return (
        <Panel title="Nowe ogłoszenie">
            <QueryState query={targets}>
                {() =>
                    options.length === 0 ? (
                        <p className="text-muted">Nie masz w tym semestrze grup, do których możesz pisać.</p>
                    ) : (
                        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                            <Select
                                label="Do kogo"
                                required
                                value={chosen ? keyOf(chosen) : ''}
                                onChange={(e) => setTargetKey(e.currentTarget.value)}
                            >
                                {options.length > 1 && <option value="">wybierz</option>}
                                {options.map((t) => (
                                    <option key={keyOf(t)} value={keyOf(t)}>
                                        {targetLabel(t)}
                                    </option>
                                ))}
                            </Select>
                            <TextField
                                label="Tytuł"
                                required
                                maxLength={200}
                                value={title}
                                onChange={(e) => setTitle(e.currentTarget.value)}
                            />
                            <ContentField value={content} onChange={setContent} />
                            {notice && <Alert variant="success">{notice}</Alert>}
                            {publish.isError && <Alert>{errorMessage(publish.error)}</Alert>}
                            <div>
                                <Button
                                    type="submit"
                                    loading={publish.isPending}
                                    disabled={!chosen || !title.trim() || !content.trim()}
                                >
                                    Opublikuj
                                </Button>
                            </div>
                        </form>
                    )
                }
            </QueryState>
        </Panel>
    );
}
